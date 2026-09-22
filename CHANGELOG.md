# Changelog

All notable changes to **Kernel Loder** are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
semantic-ish version tags (`<major>.<minor>-<flavour>`).

---

## [2.4-universal] — 2026-09-22 (versionCode 9)

### Fixed
* Splash logo now fixed at 192dp (was drawn at 512px intrinsic size).
* Fixed branded dark theme everywhere (was following wallpaper/system theme).
* Console readability: fixed dark terminal card, brighter log colors, 12sp font.
* Database connection: real error shown in console (DNS/timeout/TLS/HTTP),
  jsDelivr CDN mirror added as fallback, refresh flow improved.

### Changed
* All UI and console messages in English (Banglish removed).

---

## [2.3-universal] — 2026-09-22 (versionCode 8)

### Added
* **OTA kernel loader** (`OtaDriverStore`): manifest `drivers.json` fetched from the
  `drivers` branch at launch; matching loader downloaded, `insmod`-loaded and verified.
* **SafetyGuard**: refuses wrong major/minor loads, blocks loading after kernel
  panic/oops in dmesg, `rmmod` rescue on failure.
* **WhatsApp support** (+8801785917145) with kernel version prefilled + custom text.
* **In-app auto-update** (`AppUpdateChecker`) from GitHub Releases `latest` API,
  FileProvider APK install flow.
* `scripts/publish_apk.ps1` — one-command release APK upload.

### Changed
* **Zero bundled `.ko`**: `app/src/main/assets/drivers/` ships empty; release APK ~2.5 MB.
* Supported list newest-first with `buildDate`, `LATEST` / `THIS DEVICE` badges.
* R8 shrink + obfuscation, release signed with debug keystore for in-place updates.

---

## [2.2-universal] — 2026-09-21 (versionCode 7)

OTA groundwork release (superseded by 2.3).

---

## [2.1-universal] — 2026-09-19

### Added
* **Universal kernel module driver** — new portable source `driver/kloader_driver.c`
  (module `kloader_driver`, misc device `/dev/kloaderctl`, dmesg tag `KernelLoder:`),
  replacing the device-specific driver source.
* **Native builds for two kernel releases**, produced with proton-clang from real kernel trees:
  * `native_4.9.337.ko` — vermagic `4.9.337-DaisyForGaming SMP preempt mod_unload modversions aarch64`
  * `native_4.9.307.ko` — vermagic `4.9.307-DaisyForGaming SMP preempt mod_unload modversions aarch64`
* One-command WSL build pipeline: `driver/build_native_wsl.sh` (+ `wsl_launch_native.sh`, `wsl_verify.sh`)
  which builds both releases and copies them into `driver/out/` **and** `app/src/main/assets/drivers/`.
* `docs/` — Bangla install / build / FAQ guides.

### Changed
* **Project renamed to “Kernel Loder”** (`settings.gradle.kts` rootProject name, app label,
  credits screen) — the app no longer carries a device-specific identity.
* **Device-agnostic everything:** the app never checks brand/model/codename, only the kernel release.
* Driver selection now reads the **real kernel release from each `.ko` binary (vermagic)**
  (`EmbeddedDrivers.readVermagic`) and scores vermagic-exact matches highest (1200).
* Verification is dynamic: `lsmod` module name → matching `/dev` node, with `kloaderctl` / legacy
  `daisyctl` fallbacks (`VerificationResult.deviceNodeFound`, dump-styled key `daisyctlExists`).
* `rmmod` resolves the real module name from tokens of the picked file name (`nameTokens()`).
* Status line is universal: `UNIVERSAL: <kernel> - exact driver available / no exact driver…`
  (`RootChecker.getCompatibilityMessage`, new `RootChecker.hasExactDriver()`).
* Internal temp paths renamed: `/data/local/tmp/kloader_auto.ko`, cache `kloader_auto.ko`.
* Repo hygiene: strict `.gitignore`, `.gitattributes` (LF for shell/scripts, binary for `.ko`/`.apk`),
  Gradle `versionCode 6`, `versionName 2.1-universal`.

### Removed
* `driver/daisy_driver.c` and the `daisy_*.ko` assets (superseded by `native_*.ko`).
* Device-specific checks (`is337Kernel()`, `is307Kernel()`, `is186Kernel()`, `is337` UI flag).

---

## [2.0-universal] — 2026-09-19

### Added
* **⚡ AUTO LOAD (Universal)** engine (`UniversalKernelLoader`) with an auto-fix ladder:
  SELinux → permissive, chmod/chcon, **binary vermagic patching**, busybox `insmod -f`,
  `sig_enforce` off — each applied after diagnosing the real error, then retried.
* **Live terminal screen** (`TerminalScreen`) with colour-coded `CMD / OK / ERR / FIX / WARN / OUT`
  lines, busy-progress step display, manual root-shell command box, Copy / Clear / quick actions.
* Dynamic embedded-driver catalog that **scans `assets/drivers/`** at runtime (40 modules bundled).
* Auto-verification after loading: `lsmod`, device node existence, `dmesg` tail.

### Fixed
* Duplicate `LogEntry` / `VerificationResult` declarations (Kotlin redeclaration error).
* Missing strings and unterminated `<resources>` block in `strings.xml`.
* `rmmod` failing when the module name differed from the file name; busybox fallback added.

---

## [1.1-337fix] — 2026-09-19

### Added
* First 4.9.337-specific release: `4.9.337` `.ko` built against the device kernel tree,
  `insmod -f` force load, kernel-vs-vermagic diagnostics, `Verify Module` action.

---

## [1.0] — 2026-09-18

### Added
* Initial Android app: root check, kernel version display, `.ko` picker, `insmod` / `rmmod`,
  embedded QX / RT driver catalog, diagnostic log console.