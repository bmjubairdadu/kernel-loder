# Kernel Loder — FAQ (বাংলা)

**Q: এটা কি শুধু Daisy / Mi A2 Lite এর জন্য?**
না। App কোনো brand/model/codename check করে না — শুধু kernel release আর arch দেখে।
যেকোনো arm64 Android ফোনে চলবে (Android 9+)।

**Q: আমার kernel এর জন্য driver নেই, তবুও load হবে?**
AUTO LOAD তোমার kernel এর সাথে সবচেয়ে close bundled driver বেছে নেবে, তারপর দরকার
হলে vermagic binary-patch করবে বা busybox `insmod -f` দিয়ে force করবে।
তবে force load unstable — crash/reboot হতে পারে।

**Q: সবচেয়ে safe কোনটা?**
তোমার exact `uname -r` এর জন্য built `.ko` (এখানে `native_4.9.337.ko`) — তখন কোনো
patch/force লাগে না, clean load হয়।

**Q: নতুন kernel support কীভাবে যোগ করবো?**
`app/src/main/assets/drivers/` এ `.ko` ফাইলটা রেখে দাও (যেকোনো নাম) — ক্যাটালগ নিজে
scan করে নেবে। Driver setanta `docs/BUILD_DRIVER_BANGLA.md`।

**Q: module load হয়েছে কিনা কীভাবে বুঝবো?**
Terminal-এ দেখো, অথবা নিজে চালাও:

```bash
lsmod | grep -i kloader
ls -l /dev/kloaderctl
cat /dev/kloaderctl          # -> Kernel Loder 4.9.337 OK
dmesg | grep -i KernelLoder | tail
```

**Q: `rmmod` fail করছে?**
App module এর আসল নাম lsmod থেকে বের করে নেয় (file name এর সাথে নাও মিলতে পারে)।
তবু fail হলে busybox fallback চালায়। Manual: `rmmod kloader_driver`।

**Q: App root ছাড়া কিছু করবে?**
না — module load/unload এর জন্য root বাধ্যতামূলক। root না থাকলে `ROOT: MISSING` দেখাবে।

**Q: APK কেন `app-debug.apk` নামে?**
এটা debug-signed build (self-signed), root app হিসেবে এটাই কাজ করে। Release signing
যোগ করতে চাইলে `keystore.properties` দিয়ে `assembleRelease` ব্যবহার করো।

**Q: ভুল module load করে ফোন brick হলে কী করবো?**
Module RAM-এ load হয়, reboot করলেই চলে যায় (permanent flash হয় না)। তবু panic হলে
fastboot/recovery দিয়ে boot image flash করে recover করো — তাই আগে backup রাখো।

**Q: Terminal-এ manual কমান্ড দিলে কিছু ভাঙবে?**
যা লিখবে তাই root shell-এ চলে — দায়িত্ব তোমার। `rm -rf /` টাইপ কমান্ড কখনো দিও না।

**Q: এটা কি অ্যাপের অটো-fix kernel ভাঙতে পারে?**
fix গুলো runtime-only (SELinux mode, file permission, RAM-এ load হওয়া module) — flash কিছু
badলায় না, reboot এ সব আগের অবস্থায় ফিরে যায়।