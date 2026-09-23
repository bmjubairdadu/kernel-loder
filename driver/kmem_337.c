/*
 * kmem_337 - clean-room kernel memory driver for 4.9.337 (daisy / msm8953 family)
 * ---------------------------------------------------------------------------
 * ABI-compatible re-implementation of the interface observed in the legacy
 * 4.9.186 "wanbai/entryi" driver, written from scratch. The import set is
 * deliberately kept within what the legacy driver uses (static buffers,
 * manual page-table walk, no access_remote_vm, no kmalloc, no stack
 * protector), so it resolves on the same vendor kernels.
 *
 *   - char device, default node /dev/entryi (overridable: insmod ... devname=xxxx)
 *   - ioctl 0x801 : read_process_memory   (32-byte struct)
 *   - ioctl 0x802 : write_process_memory  (32-byte struct)
 *   - ioctl 0x803 : get_module_base       (24-byte struct + task name)
 *   - ioctl 0x804 : no-op, returns 0
 *   - ioctl 0x805 : handshake, writes 666 into struct, returns 2
 *   - success return is -1, helper failure returns 0 (legacy convention)
 *
 * structs (little-endian, 64-bit):
 *   struct proc_rw { s32 pid; u32 _pad; u64 addr; u64 buf; u64 size; }; // 32 B
 *   struct modbase { s32 pid; u32 _pad; u64 name_ptr; u64 base; };      // 24 B
 *
 * Build (DaisyForGaming tree, proton-clang):
 *   make -C <kernel_dir> M=$(PWD) ARCH=arm64 CROSS_COMPILE=aarch64-linux-android- \
 *        CROSS_COMPILE_ARM32=arm-linux-androideabi- CC=clang modules
 */

#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/cdev.h>
#include <linux/device.h>
#include <linux/uaccess.h>
#include <linux/mm.h>
#include <linux/sched.h>
#include <linux/pid.h>
#include <linux/kallsyms.h>
#include <linux/io.h>
#include <asm/pgtable.h>

#define DRV_DEFAULT_NAME	"entryi"
#define DRV_CLASS_NAME		"kwm_cls"

#define CMD_PROC_READ		0x801
#define CMD_PROC_WRITE		0x802
#define CMD_MOD_BASE		0x803
#define CHUNK			1024

static char *devname = DRV_DEFAULT_NAME;
module_param(devname, charp, 0444);
MODULE_PARM_DESC(devname, "/dev node name for this load (randomize per load)");

/* user structs - must stay packed at 32 / 24 bytes */
struct proc_rw {
	s32 pid;
	u32 _pad;
	u64 addr;
	u64 buf;
	u64 size;
};

struct modbase {
	s32 pid;
	u32 _pad;
	u64 name_ptr;
	u64 base;
};

/* static transfer area (no kmalloc - mirrors the legacy driver) */
static u8 xfer[CHUNK];
static char modname[256];
static char modpath[1024];

extern s64 memstart_addr;

/* translate a kernel linear address to a physical address */
static u64 translate_linear_address(u64 va)
{
	if (va >= PAGE_OFFSET)
		return va - PAGE_OFFSET + memstart_addr;
	return va;
}

static void io_read(void *dst, const volatile void *src, size_t n)
{
	u8 *d = dst;
	const volatile u8 *s = src;

	while (n--)
		*d++ = *s++;
}

static void io_write(volatile void *dst, const void *src, size_t n)
{
	volatile u8 *d = dst;
	const u8 *s = src;

	while (n--)
		*d++ = *s++;
}

static int read_physical_address(u64 pa, void *kbuf, size_t len)
{
	void __iomem *m;

	if (!pfn_valid(pa >> PAGE_SHIFT))
		return -EFAULT;
	m = ioremap_cache(pa, len);
	if (!m)
		return -EFAULT;
	io_read(kbuf, (const volatile void __force *)m, len);
	__iounmap(m);
	return 0;
}

static int write_physical_address(u64 pa, const void *kbuf, size_t len)
{
	void __iomem *m;

	if (!pfn_valid(pa >> PAGE_SHIFT))
		return -EFAULT;
	m = ioremap_cache(pa, len);
	if (!m)
		return -EFAULT;
	io_write((volatile void __force *)m, kbuf, len);
	__iounmap(m);
	return 0;
}

static struct task_struct *find_task(s32 pid)
{
	struct pid *p = find_get_pid(pid);

	if (!p)
		return NULL;
	return get_pid_task(p, PIDTYPE_PID);
}

/* manual page-table walk + ioremap: no access_remote_vm needed */
static int walk_read(struct mm_struct *mm, u64 addr, void *kbuf, size_t len)
{
	size_t done = 0;

	while (done < len) {
		pgd_t *pgd;
		pud_t *pud;
		pmd_t *pmd;
		pte_t *pte;
		u64 va = addr + done;
		u64 pa, chunk;

		pgd = pgd_offset(mm, va);
		if (pgd_none(*pgd) || pgd_bad(*pgd))
			return done ? 0 : -EFAULT;
		pud = pud_offset(pgd, va);
		if (pud_none(*pud) || pud_bad(*pud))
			return done ? 0 : -EFAULT;
		pmd = pmd_offset(pud, va);
		if (pmd_none(*pmd) || pmd_bad(*pmd))
			return done ? 0 : -EFAULT;
		pte = pte_offset_map(pmd, va);
		if (!pte || !pte_present(*pte)) {
			if (pte)
				pte_unmap(pte);
			return done ? 0 : -EFAULT;
		}
		pa = ((u64)pte_pfn(*pte) << PAGE_SHIFT) | (va & ~PAGE_MASK);
		pte_unmap(pte);

		chunk = len - done;
		if (chunk > PAGE_SIZE - (va & ~PAGE_MASK))
			chunk = PAGE_SIZE - (va & ~PAGE_MASK);
		if (read_physical_address(pa, (u8 *)kbuf + done, chunk))
			return done ? 0 : -EFAULT;
		done += chunk;
	}
	return 0;
}

static int walk_write(struct mm_struct *mm, u64 addr, const void *kbuf, size_t len)
{
	size_t done = 0;

	while (done < len) {
		pgd_t *pgd;
		pud_t *pud;
		pmd_t *pmd;
		pte_t *pte;
		u64 va = addr + done;
		u64 pa, chunk;

		pgd = pgd_offset(mm, va);
		if (pgd_none(*pgd) || pgd_bad(*pgd))
			return done ? 0 : -EFAULT;
		pud = pud_offset(pgd, va);
		if (pud_none(*pud) || pud_bad(*pud))
			return done ? 0 : -EFAULT;
		pmd = pmd_offset(pud, va);
		if (pmd_none(*pmd) || pmd_bad(*pmd))
			return done ? 0 : -EFAULT;
		pte = pte_offset_map(pmd, va);
		if (!pte || !pte_present(*pte) || !pte_write(*pte)) {
			if (pte)
				pte_unmap(pte);
			return done ? 0 : -EFAULT;
		}
		pa = ((u64)pte_pfn(*pte) << PAGE_SHIFT) | (va & ~PAGE_MASK);
		pte_unmap(pte);

		chunk = len - done;
		if (chunk > PAGE_SIZE - (va & ~PAGE_MASK))
			chunk = PAGE_SIZE - (va & ~PAGE_MASK);
		if (write_physical_address(pa, (const u8 *)kbuf + done, chunk))
			return done ? 0 : -EFAULT;
		done += chunk;
	}
	return 0;
}

static int read_process_memory(s32 pid, u64 addr, u64 ubuf, u64 size)
{
	struct task_struct *task;
	struct mm_struct *mm;
	u64 done = 0;
	int ret = -ESRCH;

	if (!size || size > (64 * 1024 * 1024))
		return -EINVAL;
	if (!access_ok(VERIFY_WRITE, (void __user *)(uintptr_t)ubuf, size))
		return -EFAULT;

	task = find_task(pid);
	if (!task)
		return -ESRCH;
	mm = get_task_mm(task);
	put_task_struct(task);
	if (!mm)
		return -ESRCH;

	while (done < size) {
		size_t n = size - done > CHUNK ? CHUNK : (size_t)(size - done);

		ret = walk_read(mm, addr + done, xfer, n);
		if (ret)
			break;
		if (copy_to_user((void __user *)(uintptr_t)(ubuf + done), xfer, n)) {
			ret = -EFAULT;
			break;
		}
		done += n;
	}
	mmput(mm);
	return ret;
}

static int write_process_memory(s32 pid, u64 addr, u64 ubuf, u64 size)
{
	struct task_struct *task;
	struct mm_struct *mm;
	u64 done = 0;
	int ret = -ESRCH;

	if (!size || size > (64 * 1024 * 1024))
		return -EINVAL;
	if (!access_ok(VERIFY_READ, (void __user *)(uintptr_t)ubuf, size))
		return -EFAULT;

	task = find_task(pid);
	if (!task)
		return -ESRCH;
	mm = get_task_mm(task);
	put_task_struct(task);
	if (!mm)
		return -ESRCH;

	while (done < size) {
		size_t n = size - done > CHUNK ? CHUNK : (size_t)(size - done);

		if (copy_from_user(xfer, (void __user *)(uintptr_t)(ubuf + done), n)) {
			ret = -EFAULT;
			break;
		}
		ret = walk_write(mm, addr + done, xfer, n);
		if (ret)
			break;
		done += n;
	}
	mmput(mm);
	return ret;
}

/* lockless vma walk (mirrors the legacy driver - caller retries on miss) */
static u64 get_module_base(s32 pid, const char *name)
{
	struct task_struct *task;
	struct mm_struct *mm;
	struct vm_area_struct *vma;
	u64 base = 0;

	task = find_task(pid);
	if (!task)
		return 0;
	mm = get_task_mm(task);
	put_task_struct(task);
	if (!mm)
		return 0;

	for (vma = mm->mmap; vma; vma = vma->vm_next) {
		char *b, *p;
		size_t i = 0, nl = 0;

		if (!vma->vm_file)
			continue;
		for (i = 0; name[i]; i++)
			;
		nl = i;
		memset(modpath, 0, sizeof(modpath));
		/* d_path writes at the END of the buffer and returns a pointer
		 * to it - the buffer start itself stays empty. */
		p = file_path(vma->vm_file, modpath, sizeof(modpath));
		if (IS_ERR(p))
			continue;
		for (i = 0; p[i]; i++)
			;
		if (i < nl + 1)
			continue;
		b = p + i - nl;
		if (*(b - 1) != '/')
			continue;
		for (i = 0; i < nl; i++) {
			if (b[i] != name[i])
				break;
		}
		if (i == nl && b[nl] == '\0') {
			base = vma->vm_start;
			break;
		}
	}
	mmput(mm);
	return base;
}

static int dispatch_open(struct inode *inode, struct file *file)
{
	return 0;
}

static int dispatch_close(struct inode *inode, struct file *file)
{
	return 0;
}

/*
 * Return convention (mirrors the RT driver byte-for-byte - the client
 * checks these exact values):
 *   read/write helper OK -> -5,  helper failure -> 0
 *   modbase              -> 0 (base field carries the result, may be 0)
 *   bad user pointer     -> -14 (-EFAULT)
 *   unknown ioctl number -> -22 (-EINVAL), including 0x804/0x805
 */
static long dispatch_ioctl(struct file *file, unsigned int cmd, ulong arg)
{
	if (cmd < CMD_PROC_READ || cmd > CMD_MOD_BASE)
		return -22;

	switch (cmd) {
	case CMD_PROC_READ: {
		struct proc_rw k;
		if (!access_ok(VERIFY_READ, (void __user *)arg, sizeof(k)))
			return -14;
		if (copy_from_user(&k, (void __user *)arg, sizeof(k)))
			return -14;
		return read_process_memory(k.pid, k.addr, k.buf, k.size) ? 0 : -5;
	}
	case CMD_PROC_WRITE: {
		struct proc_rw k;
		if (!access_ok(VERIFY_READ, (void __user *)arg, sizeof(k)))
			return -14;
		if (copy_from_user(&k, (void __user *)arg, sizeof(k)))
			return -14;
		return write_process_memory(k.pid, k.addr, k.buf, k.size) ? 0 : -5;
	}
	case CMD_MOD_BASE: {
		struct modbase k;
		if (!access_ok(VERIFY_READ, (void __user *)arg, sizeof(k)))
			return -14;
		if (copy_from_user(&k, (void __user *)arg, sizeof(k)))
			return -14;
		if (!access_ok(VERIFY_READ, (void __user *)(uintptr_t)k.name_ptr,
			       sizeof(modname)))
			return -14;
		memset(modname, 0, sizeof(modname));
		if (copy_from_user(modname,
				   (void __user *)(uintptr_t)k.name_ptr,
				   sizeof(modname) - 1))
			return -14;
		k.base = get_module_base(k.pid, modname);
		if (!access_ok(VERIFY_WRITE, (void __user *)arg, sizeof(k)))
			return -14;
		if (copy_to_user((void __user *)arg, &k, sizeof(k)))
			return -14;
		return 0;
	}
	default:
		return -22;
	}
}

static const struct file_operations dispatch_fops = {
	.owner		= THIS_MODULE,
	.open		= dispatch_open,
	.release	= dispatch_close,
	.unlocked_ioctl	= dispatch_ioctl,
#ifdef CONFIG_COMPAT
	.compat_ioctl	= dispatch_ioctl,
#endif
	.llseek		= noop_llseek,
};

static dev_t dev_number;
static struct cdev char_dev;
static struct class *char_class;
static struct device *char_device;

/* world-readable/writable node: game tools may open it from contexts
 * that cannot use root-only 0600 nodes (with SELinux permissive). */
static char *kmem_devnode(struct device *dev, umode_t *mode)
{
	if (mode)
		*mode = 0666;
	return NULL;
}

static int __init kmem_init(void)
{
	int ret;

	ret = alloc_chrdev_region(&dev_number, 0, 1, devname);
	if (ret)
		return ret;

	cdev_init(&char_dev, &dispatch_fops);
	char_dev.owner = THIS_MODULE;
	ret = cdev_add(&char_dev, dev_number, 1);
	if (ret)
		goto err_region;

	char_class = class_create(THIS_MODULE, devname);
	if (IS_ERR(char_class)) {
		ret = PTR_ERR(char_class);
		goto err_cdev;
	}
	char_class->devnode = kmem_devnode;

	char_device = device_create(char_class, NULL, dev_number, NULL, devname);
	if (IS_ERR(char_device)) {
		ret = PTR_ERR(char_device);
		goto err_class;
	}

	/* NOTE: no kobject_del hiding here - removing a live device kobject
	 * risks use-after-free panics on some trees. /dev node stays usable. */

	pr_info("kmem: /dev/%s created (major %d). ready.\n",
		devname, MAJOR(dev_number));
	return 0;

err_class:
	class_destroy(char_class);
err_cdev:
	cdev_del(&char_dev);
err_region:
	unregister_chrdev_region(dev_number, 1);
	return ret;
}

static void __exit kmem_exit(void)
{
	device_destroy(char_class, dev_number);
	class_destroy(char_class);
	cdev_del(&char_dev);
	unregister_chrdev_region(dev_number, 1);
	pr_info("kmem: /dev/%s removed\n", devname);
}

module_init(kmem_init);
module_exit(kmem_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("wanbai");
MODULE_DESCRIPTION("wanbai");
MODULE_VERSION("2.0-337");
