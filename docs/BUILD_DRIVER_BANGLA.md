# Kernel Loder — Driver build গাইড (বাংলা)

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

## ২. universal driver source

- Source: `driver/kloader_driver.c`
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
wsl -d Ubuntu-22.04 -- bash /mnt/c/.../KernelLoder/driver/wsl_launch_native.sh
# progress: /tmp/native.log   |  check: driver/wsl_verify.sh
```

Script: `driver/build_native_wsl.sh`
- 337: kernel tree + proton-clang, build dir `~/ko-build`
- 307: 337 tree copy (`SUBLEVEL=307`, একই `.config`), build dir `~/ko-build-307`
- output: `driver/out/native_4.9.337.ko`, `driver/out/native_4.9.307.ko` এবং সরাসরি `app/src/main/assets/drivers/`

নিজে চালাতে চাইলে:

```bash
make -C <kernel_dir> O=out ARCH=arm64 CLANG_TRIPLE=aarch64-linux-gnu- \
     CROSS_COMPILE=aarch64-linux-gnu- CROSS_COMPILE_ARM32=arm-linux-gnueabi- \
     CC=clang M=$PWD modules
```

## ৪. নতুন kernel support যোগ করা

```bash
# শুধু ফাইলটা রাখো - code change লাগবে না
cp my_kernel_5.4.210.ko app/src/main/assets/drivers/native_5.4.210.ko
./gradlew :app:assembleDebug
```

ক্যাটালগ runtime-এ `assets/drivers/` scan করে আর loader প্রতিটা ফাইলের আসল `vermagic`
পড়ে, তাই নতুন driver নিজে থেকেই ধরা পড়ে।

## ৫. গুরুত্বপূর্ণ

- Kernel mismatch হলে auto-fix ladder: SELinux permissive → chmod/chcon → **vermagic binary patch** → busybox `insmod -f` → sig_enforce off
- `insmod -f` unstable: ভুল kernel-এর built symbol/CRC থাকলে crash/reboot হতে পারে
- সবচেয়ে stable = phone-এর exact kernel release-এর জন্য built `.ko`

---
Kernel Loder v2.1-universal — universal kernel module loader (any device / any model / old & new kernels)