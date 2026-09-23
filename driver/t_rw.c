/* t_rw - freestanding ioctl test for kmem_337 (aarch64, -nostdlib -static)
 * usage: t_rw /dev/<node>   ; exit code = number of failed checks (0 = all pass)
 * checks: 0x805 handshake, 0x804 noop, 0x803 modbase, 0x801 self-read, 0x802 self-write
 */
typedef unsigned long u64;
typedef long s64;
typedef unsigned int u32;
typedef int s32;
typedef unsigned char u8;

#define AT_FDCWD (-100)
#define O_RDWR 2
#define SYS_openat 56
#define SYS_ioctl 29
#define SYS_getpid 172
#define SYS_write 64
#define SYS_exit_group 94

static long sc6(long n, long a, long b, long c, long d, long e, long f)
{
	register long x8 __asm__("x8") = n;
	register long x0 __asm__("x0") = a;
	register long x1 __asm__("x1") = b;
	register long x2 __asm__("x2") = c;
	register long x3 __asm__("x3") = d;
	register long x4 __asm__("x4") = e;
	register long x5 __asm__("x5") = f;
	__asm__ volatile ("svc #0"
		: "+r" (x0)
		: "r" (x8), "r" (x1), "r" (x2), "r" (x3), "r" (x4), "r" (x5)
		: "memory");
	return x0;
}

static long sc1(long n, long a) { return sc6(n, a, 0, 0, 0, 0, 0); }
static long sc3(long n, long a, long b, long c) { return sc6(n, a, b, c, 0, 0, 0); }

static void putstr(const char *s)
{
	u64 n = 0;
	while (s[n])
		n++;
	sc3(SYS_write, 1, (long)s, n);
}

static void puthex(u64 v)
{
	char b[20];
	int i;
	b[0] = '0'; b[1] = 'x';
	for (i = 0; i < 16; i++) {
		int nyb = (v >> (60 - i * 4)) & 0xf;
		b[2 + i] = nyb < 10 ? '0' + nyb : 'a' + nyb - 10;
	}
	b[18] = '\n';
	sc3(SYS_write, 1, (long)b, 19);
}

struct proc_rw { s32 pid; u32 pad; u64 addr; u64 buf; u64 size; };
struct modbase { s32 pid; u32 pad; u64 name_ptr; u64 base; };

static volatile u32 marker = 0x12345678;
static u8 tmpbuf[32];
static char namebuf[4096];

void _start(void);

__attribute__((naked)) void _start(void)
{
	__asm__ volatile (
		"mov x0, sp\n"
		"ldr x0, [x0]\n"	/* argc */
		"mov x1, sp\n"
		"add x1, x1, #8\n"	/* argv */
		"bl tmain\n"
		"mov x8, #94\n"		/* exit_group(fails) */
		"svc #0\n"
		"1: b 1b\n"
	);
}

long tmain(long argc, char **argv)
{
	char *dev;
	long fd, pid, r;
	int fails = 0;
	struct proc_rw pr;
	struct modbase mb;

	if (argc < 2 || !argv[1]) {
		putstr("usage: t_rw /dev/<node>\n");
		return 99;
	}
	dev = argv[1];

	fd = sc6(SYS_openat, AT_FDCWD, (long)dev, O_RDWR, 0, 0, 0);
	if (fd < 0) {
		putstr("FAIL open\n");
		sc1(SYS_exit_group, 98);
	}
	putstr("open ok\n");
	pid = sc1(SYS_getpid, 0);

	/* 0x805 handshake */
	{
		int i;
		for (i = 0; i < 32; i++)
			tmpbuf[i] = 0;
		r = sc3(SYS_ioctl, fd, 0x805, (long)tmpbuf);
		if (r == 2 && *(u32 *)tmpbuf == 666)
			putstr("PASS handshake\n");
		else {
			putstr("FAIL handshake ret=");
			puthex((u64)r);
			fails++;
		}
	}

	/* 0x804 noop */
	r = sc3(SYS_ioctl, fd, 0x804, 0);
	if (r == 0)
		putstr("PASS noop\n");
	else {
		putstr("FAIL noop\n");
		fails++;
	}

	/* 0x803 modbase of self (name placed mid-buffer so the 256B
	 * access_ok range can never straddle a page edge) */
	namebuf[2048] = 't'; namebuf[2049] = '_';
	namebuf[2050] = 'r'; namebuf[2051] = 'w'; namebuf[2052] = 0;
	mb.pid = (s32)pid;
	mb.pad = 0;
	mb.name_ptr = (u64)&namebuf[2048];
	mb.base = 0;
	r = sc3(SYS_ioctl, fd, 0x803, (long)&mb);
	if (r == (long)-1 && mb.base != 0) {
		putstr("PASS modbase base=");
		puthex(mb.base);
	} else {
		putstr("FAIL modbase\n");
		fails++;
	}

	/* 0x801 read own marker */
	pr.pid = (s32)pid;
	pr.pad = 0;
	pr.addr = (u64)&marker;
	pr.buf = (u64)tmpbuf;
	pr.size = 4;
	tmpbuf[0] = tmpbuf[1] = tmpbuf[2] = tmpbuf[3] = 0;
	r = sc3(SYS_ioctl, fd, 0x801, (long)&pr);
	if (r == (long)-1 && *(u32 *)tmpbuf == 0x12345678)
		putstr("PASS read\n");
	else {
		putstr("FAIL read got=");
		puthex(*(u32 *)tmpbuf);
		fails++;
	}

	/* 0x802 write own marker */
	*(u32 *)tmpbuf = 0xAABBCCDD;
	pr.buf = (u64)tmpbuf;
	r = sc3(SYS_ioctl, fd, 0x802, (long)&pr);
	if (r == (long)-1 && marker == 0xAABBCCDD)
		putstr("PASS write\n");
	else {
		putstr("FAIL write marker=");
		puthex(marker);
		fails++;
	}

	if (fails == 0)
		putstr("ALL PASS\n");
	return fails;
}
