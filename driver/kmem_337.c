/*
 * kmem_337 - clean-room kernel memory driver for 4.9.337 (daisy / msm8953 family)
 * ---------------------------------------------------------------------------
 * ABI-compatible re-implementation of the interface observed in the legacy
 * 4.9.186 "wanbai/entryi" driver, written from scratch:
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
 * Build (example, DaisyForGaming tree):
 *   make -C <kernel_dir> M=$(PWD) ARCH=arm64 CROSS_COMPILE=aarch64-linux-android- modules
 *
 * Loader usage (no binary patching, no reboot):
 *   insmod kmem_337.ko devname=<random>   # then check /dev/<random>
 */

#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/fs.h>
#include <linux/cdev.h>
#include <linux/device.h>
#include <linux/uaccess.h>
#include <linux/slab.h>
#include <linux/mm.h>
#include <linux/sched.h>
#include <linux/pid.h>
#include <linux/pid_namespace.h>
#include <linux/file.h>
#include <linux/path.h>
#include <linux/dcache.h>
#include <linux/random.h>
#include <linux/io.h>
#include <linux/kallsyms.h>
#include <linux/mm_types.h>
#include <asm/pgtable.h>

#define DRV_DEFAULT_NAME	"entryi"
#define DRV_CLASS_NAME		"kwm_cls"

#define CMD_PROC_READ		0x801
#define CMD_PROC_WRITE		0x802
#define CMD_MOD_BASE		0x803
#define CMD_NOOP		0x804
#define CMD_HANDSHAKE		0x805

#define HANDSHAKE_MAGIC		666

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

/* physical offset, resolved at runtime (works even when the symbol
 * layout differs between vendor trees) */
static u64 phys_offset = 0;

static u64 resolve_phys_offset(void)
{
	/* memstart_addr holds the start of linear-mapped RAM on arm64 4.9 */
	ulong addr;

	addr = (ulong)kallsyms_lookup_name("memstart_addr");
	if (addr)
		return *(u64 *)addr;
	/* fallback: assume zero offset (identity-mapped low RAM) */
	return 0;
}

/* translate a kernel linear address to a physical address */
static u64 translate_linear_address(u64 va)
{
	if (va >= PAGE_OFFSET)
		return va - PAGE_OFFSET + phys_offset;
	return va;
}

static int read_physical_address(u64 pa, void *kbuf, size_t len)
{
	void __iomem *m;
	u64 pfn = pa >> PAGE_SHIFT;

	if (!pfn_valid(pfn))
		return -EFAULT;
	m = ioremap_cache(pa, len);
	if (!m)
		return -EFAULT;
	memcpy_fromio(kbuf, m, len);
	iounmap(m);
	return 0;
}

static int write_physical_address(u64 pa, const void *kbuf, size_t len)
{
	void __iomem *m;
	u64 pfn = pa >> PAGE_SHIFT;

	if (!pfn_valid(pfn))
		return -EFAULT;
	m = ioremap_cache(pa, len);
	if (!m)
		return -EFAULT;
	memcpy_toio(m, kbuf, len);
	iounmap(m);
	return 0;
}

static struct task_struct *find_task(s32 pid)
{
	struct pid *p = find_get_pid(pid);

	if (!p)
		return NULL;
	return get_pid_task(p, PIDTYPE_PID);
}

/* manual fallback: walk the target page tables and read via ioremap */
static int proc_read_fallback(struct mm_struct *mm, u64 addr, void *kbuf, size_t len)
{
	size_t done = 0;

	while (done < len) {
		pgd_t *pgd;
		pud_t *pud;
		pmd_t *pmd;
		pte_t *pte;
		u64 va = addr + done;
		u64 pa, chunk;
		u8 *tmp;

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
		pa = (pte_pfn(*pte) << PAGE_SHIFT) | (va & ~PAGE_MASK);
		pte_unmap(pte);

		chunk = min_t(size_t, len - done, PAGE_SIZE - (va & ~PAGE_MASK));
		tmp = kmalloc(chunk, GFP_KERNEL);
		if (!tmp)
			return done ? 0 : -ENOMEM;
		if (read_physical_address(pa, tmp, chunk)) {
			kfree(tmp);
			return done ? 0 : -EFAULT;
		}
		memcpy((u8 *)kbuf + done, tmp, chunk);
		kfree(tmp);
		done += chunk;
	}
	return 0;
}

static int read_process_memory(s32 pid, u64 addr, u64 ubuf, u64 size)
{
	struct task_struct *task;
	struct mm_struct *mm;
	void *kbuf;
	int ret;

	if (!size || size > (64 * 1024 * 1024))
		return -EINVAL;
	if (!access_ok(VERIFY_WRITE, (void __user *)(uintptr_t)ubuf, size))
		return -EFAULT;

	kbuf = kmalloc(size, GFP_KERNEL);
	if (!kbuf)
		return -ENOMEM;

	task = find_task(pid);
	if (!task) {
		kfree(kbuf);
		return -ESRCH;
	}
	mm = get_task_mm(task);
	put_task_struct(task);
	if (!mm) {
		kfree(kbuf);
		return -ESRCH;
	}

	ret = access_remote_vm(mm, addr, kbuf, size, 0);
	if (ret <= 0)
		ret = proc_read_fallback(mm, addr, kbuf, size);
	else
		ret = 0;
	mmput(mm);

	if (!ret && copy_to_user((void __user *)(uintptr_t)ubuf, kbuf, size))
		ret = -EFAULT;
	kfree(kbuf);
	return ret;
}

static int write_process_memory(s32 pid, u64 addr, u64 ubuf, u64 size)
{
	struct task_struct *task;
	struct mm_struct *mm;
	void *kbuf;
	int ret;

	if (!size || size > (64 * 1024 * 1024))
		return -EINVAL;
	if (!access_ok(VERIFY_READ, (void __user *)(uintptr_t)ubuf, size))
		return -EFAULT;

	kbuf = kmalloc(size, GFP_KERNEL);
	if (!kbuf)
		return -ENOMEM;
	if (copy_from_user(kbuf, (void __user *)(uintptr_t)ubuf, size)) {
		kfree(kbuf);
		return -EFAULT;
	}

	task = find_task(pid);
	if (!task) {
		kfree(kbuf);
		return -ESRCH;
	}
	mm = get_task_mm(task);
	put_task_struct(task);
	if (!mm) {
		kfree(kbuf);
		return -ESRCH;
	}

	ret = access_remote_vm(mm, addr, kbuf, size, 1);
	mmput(mm);
	kfree(kbuf);
	return ret <= 0 ? -EFAULT : 0;
}

static u64 get_module_base(s32 pid, const char *name)
{
	struct task_struct *task;
	struct mm_struct *mm;
	struct vm_area_struct *vma;
	u64 base = 0;
	char *kbuf, *base_name;

	kbuf = (char *)__get_free_page(GFP_KERNEL);
	if (!kbuf)
		return 0;

	task = find_task(pid);
	if (!task)
		goto out_page;
	mm = get_task_mm(task);
	put_task_struct(task);
	if (!mm)
		goto out_page;

	down_read(&mm->mmap_sem);
	for (vma = mm->mmap; vma; vma = vma->vm_next) {
		if (!vma->vm_file)
			continue;
		memset(kbuf, 0, PAGE_SIZE);
		if (IS_ERR(file_path(vma->vm_file, kbuf, PAGE_SIZE)))
			continue;
		base_name = strrchr(kbuf, '/');
		base_name = base_name ? base_name + 1 : kbuf;
		if (!strcmp(base_name, name)) {
			base = vma->vm_start;
			break;
		}
	}
	up_read(&mm->mmap_sem);
	mmput(mm);
out_page:
	free_page((ulong)kbuf);
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
 * Legacy return convention (kept for userspace compatibility):
 *   success -> -1,  helper failure -> 0,  handshake -> 2
 */
static long dispatch_ioctl(struct file *file, unsigned int cmd, ulong arg)
{
	if (cmd < CMD_PROC_READ || cmd > CMD_HANDSHAKE)
		return -1;

	switch (cmd) {
	case CMD_PROC_READ: {
		struct proc_rw k;
		if (!access_ok(VERIFY_READ, (void __user *)arg, sizeof(k)))
			goto efault;
		if (copy_from_user(&k, (void __user *)arg, sizeof(k)))
			goto efault;
		return read_process_memory(k.pid, k.addr, k.buf, k.size) ? 0 : -1;
	}
	case CMD_PROC_WRITE: {
		struct proc_rw k;
		if (!access_ok(VERIFY_READ, (void __user *)arg, sizeof(k)))
			goto efault;
		if (copy_from_user(&k, (void __user *)arg, sizeof(k)))
			goto efault;
		return write_process_memory(k.pid, k.addr, k.buf, k.size) ? 0 : -1;
	}
	case CMD_MOD_BASE: {
		struct modbase k;
		char name[256];
		if (!access_ok(VERIFY_READ, (void __user *)arg, sizeof(k)))
			goto efault_m;
		if (copy_from_user(&k, (void __user *)arg, sizeof(k)))
			goto efault_m;
		if (!access_ok(VERIFY_READ, (void __user *)(uintptr_t)k.name_ptr,
			       sizeof(name)))
			goto efault_m;
		memset(name, 0, sizeof(name));
		if (copy_from_user(name, (void __user *)(uintptr_t)k.name_ptr,
				   sizeof(name) - 1))
			goto efault_m;
		k.base = get_module_base(k.pid, name);
		if (!access_ok(VERIFY_WRITE, (void __user *)arg, sizeof(k)))
			return -1;
		if (copy_to_user((void __user *)arg, &k, sizeof(k)))
			return -1;
		return -1;
	}
	case CMD_NOOP:
		return 0;
	case CMD_HANDSHAKE: {
		u8 k[32];
		u32 magic = HANDSHAKE_MAGIC;
		if (!access_ok(VERIFY_READ, (void __user *)arg, sizeof(k)))
			goto efault;
		if (copy_from_user(k, (void __user *)arg, sizeof(k)))
			goto efault;
		memcpy(k, &magic, sizeof(magic));
		if (!access_ok(VERIFY_WRITE, (void __user *)arg, sizeof(k)))
			goto efault;
		if (copy_to_user((void __user *)arg, k, sizeof(k)))
			goto efault;
		return 2;
	}
	}
	return -1;

efault:
	/* legacy behavior: clear caller stack area, report -1 */
	return -1;
efault_m:
	return -1;
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

static int __init kmem_init(void)
{
	int ret;

	phys_offset = resolve_phys_offset();

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

	char_device = device_create(char_class, NULL, dev_number, NULL, devname);
	if (IS_ERR(char_device)) {
		ret = PTR_ERR(char_device);
		goto err_class;
	}

	/* hide from sysfs listing (legacy behavior), /dev node stays usable */
	kobject_del(&char_device->kobj);

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
