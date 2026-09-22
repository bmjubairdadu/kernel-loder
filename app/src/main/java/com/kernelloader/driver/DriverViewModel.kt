package com.kernelloader.driver

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kernelloader.BuildConfig
import com.kernelloader.driver.OtaDriverStore
import com.kernelloader.root.RootChecker
import com.kernelloader.update.AppUpdateChecker
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UNIVERSAL KERNEL LOADER
 * ========================
 * This app loads a kernel module (.ko) on ANY rooted device.
 *
 * How it works:
 * 1. User presses "Load Kernel" button
 * 2. App detects running kernel version (uname -r)
 * 3. Scans assets/drivers/ for a matching .ko file
 * 4. If match found → loads it via insmod through root shell
 * 5. If no match → shows message in console to import .ko
 * 6. Every step is printed in Kernel Loder Console (copyable)
 *
 * Currently bundled: native_4.9.337.ko (universal build)
 * More .ko files can be added to assets/drivers/ - app auto-detects them.
 *
 * Naming convention (prefix is only a hint):
 *   native_<x.y.z>.ko → build for that kernel release (universal)
 */
object EmbeddedDrivers {
    /**
     * Known good kernel releases we bundle builds for. Informational only -
     * the live list is produced by scanning assets/drivers.
     */
    val bundledVersions = listOf(
        "4.9.186", "4.9.186b", "4.9.186c",
        "4.9.307", "4.9.337",
        "4.14.117", "4.14.180", "4.14.186", "4.14.186b", "4.14.186c",
        "4.19.81", "4.19.113", "4.19.113b", "4.19.157", "4.19.157b",
        "4.19.157c", "4.19.191",
        "5.1.1", "5.4", "5.4.61", "5.4.86", "5.4.147", "5.4.210", "5.4c",
        "5.10", "5.10b", "5.10-Pixel-A13",
        "5.15", "5.15b",
        "6.1", "6.6"
    )

    // Legacy aliases (kept so any older call site still compiles).
    val q058Versions: List<String> get() = bundledVersions
    val rtVersions: List<String> get() = bundledVersions

    // Asset path helpers
    fun nativeFilename(kernelRelease: String): String = "drivers/native_${kernelRelease}.ko"
    fun qxFilename(version: String): String = "drivers/qx_${version}.ko"
    fun rtFilename(version: String): String = "drivers/rt_${version}.ko"

    /**
     * Returns list of available embedded drivers with their versions.
     * Scans assets/drivers dynamically. Any *.ko dropped into assets/drivers is
     * picked up automatically - prefix (native_ / qx_ / rt_ / anything) is only
     * a label. Files without an underscore are used as-is.
     */
    fun getAvailableDrivers(context: Context): List<DriverInfo> {
        val drivers = mutableListOf<DriverInfo>()
        val names = try {
            context.resources.assets.list("drivers")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        for (name in names) {
            if (!name.endsWith(".ko")) continue
            val base = name.removeSuffix(".ko")
            val idx = base.indexOf('_')
            val type = if (idx == -1) "KERNEL" else base.substring(0, idx).uppercase(Locale.US)
            val version = if (idx == -1) base else base.substring(idx + 1)
            val description = when (type) {
                "UNI" -> "Universal build for kernel $version (any device)"
                "NATIVE", "DAISY" -> "Daisy device build for kernel $version (DaisyForGaming tree)"
                "QX" -> "Legacy QX build for $version"
                "RT" -> "Legacy RT build for $version"
                else -> "Kernel module build for $version"
            }
            drivers.add(DriverInfo(
                type = type,
                version = version,
                filename = "drivers/$name",
                displayName = "$type $version",
                description = description
            ))
        }
        return drivers.sortedWith(compareBy(
            { typeOrder(it.type) },
            { it.version }
        ))
    }

    /**
     * Read the REAL kernel release out of a .ko's vermagic string.
     * Prefix of the asset name is irrelevant - this is what the kernel checks.
     */
    fun readVermagic(context: Context, assetPath: String): String {
        return try {
            val bytes = context.resources.assets.open(assetPath).use { input ->
                val buf = ByteArray(1 shl 20)          // up to 1 MB is enough
                val n = input.read(buf)
                if (n <= 0) ByteArray(0) else buf.copyOf(n)
            }
            val tag = "vermagic=".toByteArray(Charsets.US_ASCII)
            outer@ for (i in 0..(bytes.size - tag.size)) {
                for (j in tag.indices) if (bytes[i + j] != tag[j]) continue@outer
                var end = i + tag.size
                while (end < bytes.size && bytes[end] != 0.toByte()) end++
                return String(bytes, i + tag.size, end - (i + tag.size), Charsets.US_ASCII)
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun typeOrder(t: String): Int = when (t) {
        "UNI", "KERNEL" -> 0
        "NATIVE", "DAISY" -> 1
        "QX" -> 2
        "RT" -> 3
        else -> 4
    }

    private fun assetExists(context: Context, assetPath: String): Boolean {
        return try {
            context.resources.assets.open(assetPath).use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extract embedded .ko from assets to a temp file
     * The embedded .ko files are raw ELF binaries (QX) or base64-encoded (RT)
     * Both are already decoded in the extraction script
     */
    fun extractToTemp(context: Context, assetPath: String, tempName: String): File? {
        return try {
            val tempFile = File(context.cacheDir, tempName)
            context.resources.assets.open(assetPath).use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            tempFile.setExecutable(true)
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class DriverInfo(
    val type: String,
    val version: String,
    val filename: String,
    val displayName: String,
    val description: String
)

enum class DriverType(val displayName: String) {
    NATIVE("Native build"),
    QX("QX (legacy)"),
    RT("RT (legacy)"),
    ANY("Any kernel module")
}

data class DriverLoadResult(
    val success: Boolean,
    val driverName: String,
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
    val tempFile: File?
)


data class LogEntry(
    val timestamp: String,
    val command: String,
    val stdout: List<String>,
    val stderr: List<String>,
    val exitCode: Int
)

data class VerificationResult(
    val lsmod: List<String> = emptyList(),
    val deviceNodeFound: Boolean = false,     // true when ANY /dev node from the module showed up
    val dmesgLogs: List<String> = emptyList(),
    val timestamp: String = "",
    val kernelRelease: String = "",
    val loadedModules: Int = 0
)

data class TerminalLine(
    val time: String,
    val text: String,
    val type: String = "INFO"  // INFO, CMD, OUT, OK, ERR, FIX, WARN
)

class DriverViewModel : ViewModel() {
    var pickedFileUri = mutableStateOf<Uri?>(null)
    var pickedFileName = mutableStateOf<String?>(null)
    val logs = mutableStateListOf<LogEntry>()
    var verificationResult = mutableStateOf<VerificationResult?>(null)

    // ---- Universal Terminal state ----
    val terminalLines = mutableStateListOf<TerminalLine>()
    var isBusy = mutableStateOf(false)
    var busyStep = mutableStateOf("")
    var autoLoadStatus = mutableStateOf("")   // final one-line result
    var autoLoadOk = mutableStateOf<Boolean?>(null)

    // ---- OTA (GitHub driver database) state ----
    var remoteManifest = mutableStateOf<OtaDriverStore.Manifest?>(null)
    var manifestStatus = mutableStateOf("IDLE")   // IDLE / LOADING / OK / EMPTY / OFFLINE

    // ---- In-app auto-update (GitHub Releases database) state ----
    var appUpdate = mutableStateOf<AppUpdateChecker.UpdateInfo?>(null)
    var updateStatus = mutableStateOf("IDLE")     // IDLE / CHECKING / NONE / AVAILABLE / DOWNLOADING / DONE / ERROR
    var updateProgress = mutableStateOf(-1)       // 0..100 while DOWNLOADING, -1 otherwise
    var updateMsg = mutableStateOf("")

    /**
     * Auto-update check: compare our versionCode with the newest GitHub
     * Release. Runs automatically on app open; console-logged like everything
     * else so the user always sees what is happening.
     */
    fun checkForAppUpdate() {
        if (updateStatus.value == "CHECKING" || updateStatus.value == "DOWNLOADING") return
        updateStatus.value = "CHECKING"
        tlog("UPDATE: checking GitHub Releases (current v${BuildConfig.VERSION_CODE} ${BuildConfig.VERSION_NAME})...", "INFO")
        viewModelScope.launch(Dispatchers.IO) {
            val result = AppUpdateChecker.check(BuildConfig.UPDATE_API_URL, BuildConfig.VERSION_CODE)
            withContext(Dispatchers.Main) {
                when (result) {
                    is AppUpdateChecker.UpdateCheck.Available -> {
                        appUpdate.value = result.info
                        updateStatus.value = "AVAILABLE"
                        tlog(
                            "UPDATE: new version found - v${result.info.versionCode} ${result.info.versionName} " +
                                    "(${result.info.apkSize / 1024} KB, ${result.info.publishedAt.take(10)})",
                            "OK"
                        )
                        tlog("UPDATE: tap the update banner to install", "INFO")
                    }
                    AppUpdateChecker.UpdateCheck.UpToDate -> {
                        appUpdate.value = null
                        updateStatus.value = "NONE"
                        tlog("UPDATE: app is up-to-date (v${BuildConfig.VERSION_CODE})", "OK")
                    }
                    AppUpdateChecker.UpdateCheck.Offline -> {
                        if (updateStatus.value != "AVAILABLE") updateStatus.value = "ERROR"
                        tlog("UPDATE: GitHub unreachable (offline?) - skipped", "WARN")
                    }
                }
            }
        }
    }

    /** Download the newer APK from GitHub and open the system installer. */
    fun installAppUpdate(context: Context) {
        val info = appUpdate.value ?: return
        updateStatus.value = "DOWNLOADING"
        updateProgress.value = 0
        viewModelScope.launch(Dispatchers.IO) {
            if (!AppUpdateChecker.canInstall(context)) {
                withContext(Dispatchers.Main) {
                    tlog("UPDATE: \"install unknown apps\" permission needed - opening settings", "WARN")
                    updateStatus.value = "AVAILABLE"
                    updateProgress.value = -1
                    AppUpdateChecker.requestInstallPermission(context)
                }
                return@launch
            }
            val msg = AppUpdateChecker.downloadAndInstall(context, info) { pct ->
                viewModelScope.launch(Dispatchers.Main) { updateProgress.value = pct }
            }
            withContext(Dispatchers.Main) {
                updateMsg.value = msg
                updateStatus.value = if (msg.contains("installer opened")) "DONE" else "ERROR"
                updateProgress.value = -1
                tlog(msg, if (updateStatus.value == "DONE") "OK" else "ERR")
            }
        }
    }

    /**
     * Fetch drivers.json from the GitHub database so the UI can show every
     * kernel version that has a loader available. Runs automatically when the
     * app opens and can be pulled to refresh manually.
     */
    fun refreshManifest() {
        manifestStatus.value = "LOADING"
        viewModelScope.launch(Dispatchers.IO) {
            val m = try { OtaDriverStore.fetchManifest() } catch (e: Exception) { null }
            withContext(Dispatchers.Main) {
                remoteManifest.value = m
                manifestStatus.value = when {
                    m == null -> "OFFLINE"
                    m.drivers.isEmpty() -> "EMPTY"
                    else -> "OK"
                }
            }
        }
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    companion object {
        private const val MAX_TERMINAL_LINES = 1200
    }

    fun tlog(text: String, type: String = "INFO") {
        viewModelScope.launch(Dispatchers.Main) {
            terminalLines.add(TerminalLine(timeFormat.format(Date()), text, type))
            trimTerminal()
        }
    }

    fun tstep(step: String) {
        viewModelScope.launch(Dispatchers.Main) { busyStep.value = step }
    }

    private fun trimTerminal() {
        if (terminalLines.size > MAX_TERMINAL_LINES) {
            repeat(terminalLines.size - MAX_TERMINAL_LINES) { terminalLines.removeAt(0) }
        }
    }

    fun clearTerminal() { terminalLines.clear() }

    fun nowString(): String = dateFormat.format(Date())

    fun setVerification(result: VerificationResult) {
        viewModelScope.launch(Dispatchers.Main) { verificationResult.value = result }
    }

    fun getTerminalText(): String {
        return terminalLines.joinToString("\n") { "[${it.time}] ${it.text}" }
    }

    /** Run a user-typed command in the root shell and stream result into the terminal */
    fun runUserCommand(cmd: String) {
        val c = cmd.trim()
        if (c.isEmpty()) return
        viewModelScope.launch {
            tlog("# $c", "CMD")
            withContext(Dispatchers.IO) {
                try {
                    val res = Shell.cmd(c).exec()
                    withContext(Dispatchers.Main) {
                        res.out.forEach { if (it.isNotBlank()) terminalLines.add(TerminalLine(timeFormat.format(Date()), it, "OUT")) }
                        res.err.forEach { terminalLines.add(TerminalLine(timeFormat.format(Date()), it, "ERR")) }
                        terminalLines.add(TerminalLine(timeFormat.format(Date()), "exit ${res.code}", if (res.isSuccess) "OK" else "ERR"))
                        trimTerminal()
                    }
                    addLog(c, res.out, res.err, res.code)
                } catch (e: Exception) {
                    tlog("ERROR: ${e.message}", "ERR")
                }
            }
        }
    }

    /**
     * OTA auto-load: try to fetch an exact-match .ko from the OTA manifest first.
     * Returns true if a driver was downloaded and loaded.
     */
    private suspend fun runOtaLoad(context: Context): Boolean {
        tlog("OTA: checking manifest...", "INFO")
        val manifest = OtaDriverStore.fetchManifest() ?: run {
            tlog("OTA: manifest unavailable (offline or network error)", "WARN")
            return false
        }
        if (manifest.drivers.isEmpty()) {
            tlog("OTA: manifest has no drivers", "WARN")
            return false
        }
        val kernel = RootChecker.getKernelRelease() ?: return false
        val resolved = OtaDriverStore.resolve(manifest, kernel)
        when (resolved) {
            is OtaDriverStore.ResolveResult.Exact -> {
                tlog("OTA: exact match found for $kernel", "OK")
                return downloadAndLoadOta(context, manifest, resolved.entry)
            }
            is OtaDriverStore.ResolveResult.Near -> {
                val target = resolved.entry.version
                tlog(
                    "OTA: exact match nai; nearest loader = $target (distance ${resolved.distance})",
                    "WARN"
                )
                // ---------- REBOOT GUARD 1: same major.minor holei force-load ----------
                if (!SafetyGuard.canForceLoad(kernel, target)) {
                    SafetyGuard.refusalLines(kernel, target).forEach { tlog(it.first, it.second) }
                    return false
                }
                // ---------- REBOOT GUARD 2: kernel already sick hole kichu load korbo na ----------
                if (SafetyGuard.kernelLooksUnstable()) {
                    SafetyGuard.unstableLines().forEach { tlog(it.first, it.second) }
                    return false
                }
                tlog(
                    "SAFETY: same kernel series (${SafetyGuard.majorMinor(kernel)}) - " +
                            "nearest loader force-load chesta",
                    "FIX"
                )
                return downloadAndLoadOta(context, manifest, resolved.entry, force = true)
            }
            is OtaDriverStore.ResolveResult.None -> {
                tlog("OTA: no driver available for this kernel series", "INFO")
                return false
            }
        }
    }

    private suspend fun downloadAndLoadOta(
        context: Context,
        manifest: OtaDriverStore.Manifest,
        entry: OtaDriverStore.DriverEntry,
        force: Boolean = false
    ): Boolean {
        val tmp = File(context.cacheDir, "ota_${entry.file.substringAfterLast('/')}.ko")
        val downloaded = OtaDriverStore.downloadDriver(
            context = context,
            baseUrl = manifest.baseUrl,
            entry = entry,
            onLog = { msg, kind -> tlog(msg, kind) }
        ) ?: return false
        try {
            if (!UniversalKernelLoader.ensureElf(downloaded)) {
                tlog("OTA: downloaded file is not a valid ELF .ko", "ERR")
                return false
            }

            val kernel = RootChecker.getKernelRelease() ?: return false
            tlog("OTA: loading ${downloaded.name} ($kernel)...", "INFO")

            // ---------- REBOOT GUARD 3: baseline of loaded modules ----------
            val before = SafetyGuard.loadedModuleNames()
            if (before.isNotEmpty()) {
                tlog("SAFETY: baseline modules = ${before.size} (rescue rmmod ready)", "INFO")
            }

            // If the .ko was built for another release, patch vermagic first
            // (mismatched vermagic => kernel rejects, or panic when forced).
            val vermagic = try { UniversalKernelLoader.readVermagic(downloaded) } catch (e: Exception) { null }
            if (vermagic != null &&
                RootChecker.kernelShortVersion(vermagic) != RootChecker.kernelShortVersion(kernel)
            ) {
                tlog("FIX: vermagic \"$vermagic\" != device \"$kernel\" - patching", "FIX")
                if (UniversalKernelLoader.patchVermagic(downloaded, kernel)) {
                    tlog("FIX: vermagic patched OK -> \"$kernel\"", "OK")
                } else {
                    tlog("FIX: vermagic patch failed (string too long) - force-load e chesta korbo", "WARN")
                }
            }

            var res = Shell.cmd("insmod ${downloaded.absolutePath}").exec()
            res.out.forEach { if (it.isNotBlank()) tlog(it, "OUT") }
            res.err.forEach { if (it.isNotBlank()) tlog(it, "ERR") }

            // force-load retry (only in nearest-match mode, guard already approved it)
            if (!res.isSuccess && force) {
                tlog("FIX: insmod -f (force load) - $_forceNote", "FIX")
                res = Shell.cmd("insmod -f ${downloaded.absolutePath}").exec()
                res.out.forEach { if (it.isNotBlank()) tlog(it, "OUT") }
                res.err.forEach { if (it.isNotBlank()) tlog(it, "ERR") }
            }

            if (!res.isSuccess) {
                tlog("OTA: insmod failed (exit ${res.code})", "ERR")
                val errText = (res.out + res.err).joinToString("\n")
                if (errText.contains("Invalid module format", true) ||
                    errText.contains("vermagic", true) ||
                    errText.contains("Exec format error", true)
                ) {
                    tlog("DIAGNOSE: vermagic / module format mismatch - ei loader ei kernel e cholbe na", "WARN")
                    tlog("ACTION: phone restart hoy ni (kichu force kora hoy ni).", "INFO")
                }
                tlog("SUPPORT: ei kernel ($kernel) er jonno custom loader lagbe - nicher WhatsApp button chapun", "FIX")
                return false
            }

            tlog("OTA: insmod OK (exit ${res.code})", "OK")

            // ---------- REBOOT GUARD 4: post-load health check + rescue ----------
            waitForStability()
            if (SafetyGuard.kernelLooksUnstable()) {
                tlog("SAFETY: load er por kernel unstable (panic/oops signature dhorа porlo)", "ERR")
                val newMods = SafetyGuard.newlyLoaded(before)
                if (newMods.isEmpty()) {
                    tlog("SAFETY: notun module name paoa jay nai - /proc/modules check korun", "WARN")
                }
                var rescued = false
                newMods.forEach { mod ->
                    tlog("RESCUE: rmmod $mod (phone restart thekano hocche)", "FIX")
                    if (SafetyGuard.rescueUnload(mod)) {
                        rescued = true
                        tlog("RESCUE: $mod unloaded - kernel stable, phone restart hobe na", "OK")
                    } else {
                        tlog("RESCUE: rmmod $mod fail - module ta load e ache", "WARN")
                    }
                }
                tlog(
                    if (rescued)
                        "RESULT: loader unsafe chilo, tai ber kore dewa hoyeche. Phone restart hoy ni."
                    else
                        "RESULT: loader unsafe - console log ta WhatsApp e pathiye din (restart er age)",
                    "WARN"
                )
                tlog("SUPPORT: nicher WhatsApp button theke custom loader request korun", "FIX")
                withContext(Dispatchers.Main) {
                    autoLoadOk.value = false
                    autoLoadStatus.value = if (rescued)
                        "Unsafe loader removed (no restart)" else "Loader unstable - contact support"
                    tstep("")
                }
                return false
            }

            tlog("SAFETY: kernel stable - phone restart risk nai", "OK")
            withContext(Dispatchers.Main) { verifyModule() }
            return true
        } finally {
            if (downloaded != tmp && downloaded.exists()) downloaded.delete()
        }
    }

    /** Small settle window so dmesg can show a problem before we call it a success. */
    private suspend fun waitForStability() {
        withContext(Dispatchers.IO) { try { Thread.sleep(900) } catch (e: InterruptedException) { } }
    }

    private val _forceNote: String
        get() = "nearest-series loader, restart guard active"

    /**
     * UNIVERSAL AUTO-LOAD:
     * - Works on old & new kernels
     * - Auto-fixes problems (SELinux, permissions, vermagic mismatch, force-load)
     * - Auto-picks best embedded driver if no file is picked
     * - Streams every step into the terminal
     */
    fun autoLoadUniversal(context: Context, preferOta: Boolean = true) {
        if (isBusy.value) return
        isBusy.value = true
        busyStep.value = "Starting..."
        autoLoadOk.value = null
        autoLoadStatus.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (preferOta) {
                    val handled = runOtaLoad(context)
                    if (handled) {
                        isBusy.value = false
                        busyStep.value = ""
                        return@launch
                    }
                    tlog("OTA: no OTA driver; falling back to embedded", "INFO")
                }
                withContext(Dispatchers.Main) {
                    UniversalKernelLoader.autoLoad(context, this@DriverViewModel)
                }
            } catch (e: Exception) {
                tlog("FATAL: ${e.message}", "ERR")
                autoLoadStatus.value = "FATAL: ${e.message}"
                autoLoadOk.value = false
            } finally {
                viewModelScope.launch(Dispatchers.Main) {
                    isBusy.value = false
                    busyStep.value = ""
                }
            }
        }
    }

    private fun addTerminalFor(entry: LogEntry) {
        val type = if (entry.exitCode == 0) "CMD" else "ERR"
        terminalLines.add(TerminalLine(entry.timestamp.substringBefore('.'), "$ ${entry.command}  (exit ${entry.exitCode})", type))
        entry.stderr.forEach { if (it.isNotBlank()) terminalLines.add(TerminalLine(entry.timestamp.substringBefore('.'), it, "ERR")) }
        trimTerminal()
    }

    private fun addLog(command: String, stdout: List<String>, stderr: List<String>, exitCode: Int) {
        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, command, stdout, stderr, exitCode)
        viewModelScope.launch(Dispatchers.Main) {
            logs.add(entry)
            addTerminalFor(entry)
        }
    }

    fun clearLogs() {
        logs.clear()
    }

    fun getLogsText(): String {
        return logs.joinToString("\n\n") { entry ->
            "[${entry.timestamp}] $ ${entry.command}\n" +
                    "Exit Code: ${entry.exitCode}\n" +
                    (if (entry.stdout.isNotEmpty()) "STDOUT:\n${entry.stdout.joinToString("\n")}\n" else "") +
                    (if (entry.stderr.isNotEmpty()) "STDERR:\n${entry.stderr.joinToString("\n")}" else "")
        }.trim()
    }

    fun verifyModule() {
        viewModelScope.launch {
            val kernelRelease = try {
                Shell.cmd("uname -r").exec().out.firstOrNull()?.trim() ?: ""
            } catch (e: Exception) { "" }

            val lsmodRes = Shell.cmd("lsmod").exec()
            // Module names = first column of lsmod (skip header). Any device/model works.
            val moduleNames = lsmodRes.out.drop(1)
                .map { it.trim().split(Regex("\\s+")).firstOrNull() ?: "" }
                .filter { it.isNotBlank() && it != "Module" }

            // The device node name is chosen by the module itself, so first look for
            // /dev entries matching a loaded module name, then fall back to known tags.
            val devList = Shell.cmd("ls /dev 2>/dev/null").exec().out
            val matchedNodes = devList.filter { node ->
                moduleNames.any { m -> node.contains(m, ignoreCase = true) }
            }
            val candidateNodes = (moduleNames + matchedNodes + listOf("kloaderctl", "daisyctl"))
                .filter { it.isNotBlank() }.distinct()
            val devCmd = candidateNodes.joinToString(" ") { "ls /dev/$it 2>/dev/null;" } + " true"
            val devRes = Shell.cmd(devCmd).exec()

            val dmesgRes = Shell.cmd(
                "dmesg | grep -i -E 'kloader|module|vermagic|insmod|misc|driver' | tail -n 8"
            ).exec()

            val timestamp = dateFormat.format(Date())
            verificationResult.value = VerificationResult(
                lsmod = lsmodRes.out,
                deviceNodeFound = devRes.out.any { it.isNotBlank() },
                dmesgLogs = dmesgRes.out,
                timestamp = timestamp,
                kernelRelease = kernelRelease,
                loadedModules = moduleNames.size
            )

            addLog("lsmod", lsmodRes.out, lsmodRes.err, lsmodRes.code)
            addLog("ls /dev/<matching module nodes>", devRes.out, devRes.err, devRes.code)
            addLog("dmesg | grep module", dmesgRes.out, dmesgRes.err, dmesgRes.code)
        }
    }

    fun onFilePicked(context: Context, uri: Uri) {
        pickedFileUri.value = uri
        pickedFileName.value = getFileName(context, uri)
        addLog("File picked: ${pickedFileName.value}", emptyList(), emptyList(), 0)
    }

    fun loadModule(context: Context, forceLoad: Boolean = false) {
        val uri = pickedFileUri.value ?: return
        val fileName = pickedFileName.value ?: "module.ko"
        
        viewModelScope.launch {
            val tempPath = "/data/local/tmp/$fileName"
            
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val cacheFile = File(context.cacheDir, fileName)
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        
                        // 1. Copy to tmp FIRST
                        Shell.cmd(
                            "cp ${cacheFile.absolutePath} $tempPath",
                            "chmod 644 $tempPath",
                        ).exec()

                        // 2. Check kernel version + .ko vermagic BEFORE insmod
                        // (works for ANY kernel release - we compare the real numbers)
                        val checkRes = Shell.cmd(
                            "uname -r",
                            "modinfo $tempPath 2>&1 || strings $tempPath | grep -a -i -m 5 vermagic 2>&1",
                        ).exec()
                        val checkOut = checkRes.out.joinToString("\n")
                        withContext(Dispatchers.Main) {
                            addLog("check kernel + vermagic", checkRes.out, checkRes.err, checkRes.code)
                        }
                        val kernelVer = try {
                            Shell.cmd("uname -r").exec().out.firstOrNull()?.trim() ?: ""
                        } catch (e: Exception) { "" }

                        // Any device / any kernel: compare running release with .ko vermagic
                        val kernelShort = RootChecker.kernelShortVersion(kernelVer)
                        val vermagicShort = RootChecker.kernelShortVersion(checkOut)
                        val isMismatch = kernelShort.isNotEmpty() &&
                                vermagicShort.isNotEmpty() &&
                                kernelShort != vermagicShort

                        // 3. Try normal insmod first
                        var res = Shell.cmd("insmod $tempPath").exec()
                        withContext(Dispatchers.Main) {
                            addLog("insmod $tempPath", res.out, res.err, res.code)
                        }

                        // 3. If failed due to version magic + user allowed force -> try insmod -f
                        val errText = (res.out + res.err).joinToString("\n")
                        val isVersionError = errText.contains("Invalid module format", true) ||
                                errText.contains("vermagic", true) ||
                                errText.contains("version magic", true) ||
                                errText.contains("Exec format error", true)

                        if (!res.isSuccess && isVersionError) {
                            withContext(Dispatchers.Main) {
                                if (isMismatch) {
                                    addLog(
                                        "DIAGNOSE: vermagic mismatch!",
                                        listOf(
                                            "Device kernel: $kernelVer",
                                            ".ko vermagic: $checkOut",
                                            "=> driver built for $vermagicShort, device runs $kernelShort.",
                                            "=> A .ko for another kernel release never loads cleanly.",
                                            "=> Best fix: use AUTO LOAD (auto vermagic patch / native build),",
                                            "   else rebuild this .ko against $kernelShort headers."
                                        ),
                                        emptyList(), -1
                                    )
                                } else {
                                    addLog(
                                        "DIAGNOSE: version error, trying force",
                                        listOf(errText), emptyList(), res.code
                                    )
                                }
                            }
                            if (forceLoad || isMismatch) {
                                // insmod -f = force vermagic check bypass (unstable, but only option without rebuild)
                                val forceRes = Shell.cmd("insmod -f $tempPath").exec()
                                withContext(Dispatchers.Main) {
                                    addLog("insmod -f $tempPath (FORCE)", forceRes.out, forceRes.err, forceRes.code)
                                }
                                res = forceRes
                            }
                        }

                        // 4. Auto-verify after load attempt
                        if (res.isSuccess) {
                            val vRes = Shell.cmd("lsmod | tail -n 10; ls /dev 2>/dev/null; dmesg | tail -n 20").exec()
                            withContext(Dispatchers.Main) {
                                addLog("verify after insmod", vRes.out, vRes.err, vRes.code)
                            }
                            withContext(Dispatchers.Main) { verifyModule() }
                        } else {
                            withContext(Dispatchers.Main) {
                                tlog("LOAD FAILED: ei .ko ei kernel e load hoy nai.", "ERR")
                                tlog(
                                    "SUPPORT: WhatsApp e message korun - apnar kernel er jonno custom loader banie debo: wa.me/${SupportContact.WHATSAPP_NUMBER}",
                                    "FIX"
                                )
                            }
                        }
                        
                        cacheFile.delete()
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            addLog("Error: Could not open input stream", emptyList(), emptyList(), -1)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        addLog("Error: ${e.message}", emptyList(), emptyList(), -1)
                    }
                }
            }
        }
    }

    fun loadModuleForce(context: Context) = loadModule(context, forceLoad = true)

    fun unloadModule() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Find the actual loaded module name from lsmod (module name != file name)
                    val lsmodRes = Shell.cmd("lsmod").exec()
                    val pickedName = pickedFileName.value?.removeSuffix(".ko") ?: ""
                    val entries = lsmodRes.out.drop(1).mapNotNull { line ->
                        line.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() && it != "Module" }
                    }
                    val tokens = nameTokens(pickedFileName.value)
                    val loadedLine = lsmodRes.out.firstOrNull { line ->
                        val modName = line.trim().split(Regex("\\s+")).firstOrNull() ?: ""
                        tokens.any { modName.contains(it, ignoreCase = true) }
                    }
                    val moduleName = loadedLine?.trim()?.split(Regex("\\s+"))?.firstOrNull()
                            ?: pickedName.takeIf { it.isNotEmpty() }
                            ?: entries.lastOrNull()
                    if (moduleName.isNullOrEmpty()) {
                        tlog("UNLOAD: kono module load hoy nai - nothing to unload", "WARN")
                        return@withContext
                    }

                    tlog("UNLOAD: trying rmmod '$moduleName'", "INFO")
                    var res = Shell.cmd("rmmod $moduleName").exec()
                    if (!res.isSuccess) {
                        // busybox fallback (some ROMs ship broken rmmod)
                        val forceRes = Shell.cmd(
                            "BB=\$(command -v busybox); [ -z \"\$BB\" ] && BB=/data/adb/magisk/busybox; \$BB rmmod $moduleName"
                        ).exec()
                        res = forceRes
                    }
                    tlog("UNLOAD: rmmod $moduleName -> exit ${res.code}", if (res.isSuccess) "OK" else "ERR")
                    res.err.forEach { tlog(it, "WARN") }
                    addLog("rmmod $moduleName", res.out, res.err, res.code)
                } catch (e: Exception) {
                    tlog("UNLOAD ERROR: ${e.message}", "ERR")
                }
            }
        }
    }

    /**
     * Non-numeric tokens of a .ko file name - used to locate the module in lsmod,
     * because a module's internal name can differ from the .ko file name.
     */
    private fun nameTokens(fileName: String?): List<String> {
        val base = (fileName ?: "").removeSuffix(".ko")
        val tokens = base.split('_', '-', '.').filter { it.length >= 3 && it.any { c -> !c.isDigit() } }
        return tokens.ifEmpty { listOf("kloader", "driver") }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }
}
