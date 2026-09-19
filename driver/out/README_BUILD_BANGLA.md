# Kernel Loder - Universal Driver Build Doc (বাংলা)

## ১. driver গুলো কোথায় থাকে

`app/src/main/assets/drivers/` ফোল্ডারে যত `.ko` রাখবে সব অটো scan হবে (prefix শুধু label):

```
native_4.9.337.ko   <- Kernel Loder native build (4.9.337 kernel)
native_4.9.307.ko   <- Kernel Loder native build (4.9.307 kernel)
qx_<version>.ko     <- পুরনো QX build (640x480)
rt_<version>.ko     <- পুরনো RT build (Full HD)
```

App asset-এর file name পড়ে নয় — **binary vermagic পড়ে** আসল kernel release বের করে,
তাই যেকোনো নাম দিলেও সঠিক driver select হয়।

## ২. নতুন universal driver (device name লাগে না)

- Source: `driver/kloader_driver.c` (আগের device-specific driver এর universal version)
- module name: `kloader_driver`, device node: `/dev/kloaderctl`
- dmesg tag: `KernelLoder:`
- author: JUBAIR HOSEN

Built vermagic (এটাই kernel মিলিয়ে load করে):

```
native_4.9.337.ko -> 4.9.337-DaisyForGaming SMP preempt mod_unload modversions aarch64  (9440 bytes)
native_4.9.307.ko -> 4.9.307-DaisyForGaming SMP preempt mod_unload modversions aarch64  (9440 bytes)
```

> vermagic-এর ওই অংশটা kernel tree-র `CONFIG_LOCALVERSION` থেকে আসে, driver source থেকে না।
> অর্থাৎ phone-এর `uname -r` যেমন, build করা `.ko`-এর vermagic তেমনই থাকে।

## ৩. Build কীভাবে (WSL Ubuntu-22.04)

```bash
# একবারেই দুই kernel এর .ko বানায় + app asset-এ কপি করে
wsl -d Ubuntu-22.04 -- bash /mnt/c/.../DaisyDiverLoder/driver/wsl_launch_native.sh
# progress: /tmp/native.log   |  check: driver/wsl_verify.sh
```

Script: `driver/build_native_wsl.sh`
- 337: `/home/jubair/daisy-build/kernel_source` + proton-clang, build dir `/home/jubair/ko-build`
- 307: `/home/jubair/daisy-build/kernel_307` (337 tree copy + `SUBLEVEL=307` + same .config), build dir `/home/jubair/ko-build-307`
- output: `driver/out/native_4.9.337.ko`, `driver/out/native_4.9.307.ko` এবং সরাসরি `app/src/main/assets/drivers/`

## ৪. App-এ load

1. `.ko` pick করা লাগবে না — **⚡ AUTO LOAD (Universal)** চাপলেই exact driver নিজে বেছে নেবে
2. Terminal screen-এ পুরো process live দেখা যাবে: root check → kernel/arch/SELinux → driver select → vermagic check → insmod → fix → verify
3. Root shell-এ manual command ও চালানো যাবে

## ৫. গুরুত্বপূর্ণ

- Kernel mismatch হলে auto-fix ladder: SELinux permissive → chmod/chcon → **vermagic binary patch** → busybox `insmod -f` → sig_enforce off
- `insmod -f` unstable: ভুল kernel-এর built symbol/CRC থাকলে crash/reboot হতে পারে
- সবচেয়ে stable = phone-এর exact kernel release-এর জন্য built `.ko` (এখানে `native_4.9.337.ko`)

---
Kernel Loder v2.1-universal - universal kernel module loader (any device / any model / old & new kernels)