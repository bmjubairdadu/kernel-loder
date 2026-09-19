# Kernel Loder — Install & ব্যবহার (বাংলা)

## ১. ইনস্টল

1. [Releases](../../releases/latest) থেকে **app-debug.apk** নামাও
2. ফোনে ইনস্টল করো (Unknown sources allow করতে হবে)
3. App খোলো → **Superuser / Su permission** দাও (Magisk / KernelSU popup আসবে)
4. Safety warning পড়ে **I Understand, Continue** চাপো

## ২. Driver load করা (সবচেয়ে সহজ পথ)

1. কিছু pick করা লাগবে না — সরাসরি **⚡ AUTO LOAD (Universal)** চাপো
2. App নিজেই:
   - root check করবে (uid=0)
   - kernel release (`uname -r`), arch (aarch64), SELinux অবস্থা পড়বে
   - bundled driver গুলোর **vermagic** পড়ে তোমার kernel এর সাথে সবচেয়ে ভালো match বেছে নেবে
   - দরকার হলে fix করবে (SELinux, permission, vermagic patch) → `insmod` → আবার চেষ্টা
   - শেষে verify করবে: `lsmod` + `/dev` node + `dmesg`
3. Terminal screen-এ প্রতিটা ধাপ live দেখা যাবে: `CMD` / `OK` / `ERR` / `FIX` / `WARN` / `OUT`

## ৩. নিজের `.ko` load করা

1. **Pick .ko file** চেপে তোমার module বেছে নাও
2. **insmod** = সাধারণ load
3. **insmod -f (Force Load)** = vermagic / CRC / signature check bypass (⚠️ unstable, crash হতে পারে)
4. **rmmod** = unload (module এর আসল নাম নিজেই খুঁজে বের করে)
5. **Verify Module** = লোড হয়েছে কিনা check

## ৪. Terminal-এ নিজের কমান্ড

Terminal screen-এর নিচের input box-এ root shell কমান্ড লিখে পাঠাও:

```bash
uname -r
lsmod | head
cat /proc/misc
ls -l /dev/kloaderctl
cat /dev/kloaderctl                 # -> Kernel Loder 4.9.337 OK
dmesg | grep -i KernelLoder | tail -n 20
```

## ৫. সফল হলে কী দেখবে

```text
ROOT: OK (running as uid=0)
KERNEL: 4.9.337-DaisyForGaming
SOURCE: auto-selected embedded driver: NATIVE 4.9.337
VERIFY: lsmod -> LOADED (kloader_driver)      OK
VERIFY: /dev node -> FOUND (/dev/kloaderctl)  OK
```

## ৬. সমস্যা হলে

| সমস্যা | সমাধান |
|---|---|
| `ROOT: MISSING` | Magisk/KernelSU-তে app-কে Superuser দাও, তারপর app আবার খোলো |
| `Invalid module format` | `.ko` অন্য kernel এর জন্য built। AUTO LOAD চালাও (vermagic auto-patch) বা তোমার kernel এর জন্য rebuild করো |
| `Unknown symbol` | kernel config/CRC mismatch → AUTO LOAD busybox `insmod -f` ব্যবহার করবে |
| Reboot হয়ে গেল | force load-এ ভুল module crash করিয়েছে → exact kernel এর built `.ko` ব্যবহার করো |

## ৭. নিরাপত্তা

- Kernel module load করা **root** কাজ — ভুল module এ ফোন brick হতে পারে
- আগে boot image/récvery backup রাখো, fastboot জানো
- App auto-fix করবে, কিন্তু কোনো guarantee নেই — নিজ দায়িত্বে ব্যবহার করো