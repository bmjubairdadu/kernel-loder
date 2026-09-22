package com.kernelloader.driver

import com.topjohnwu.superuser.Shell

/**
 * REBOOT GUARD
 * ============
 * Bhul / mismatch kora kernel module force-load korle kernel panic hoy -
 * phone ta nijei restart hoye jay. Ei guard shei risk ta age theke detect kore:
 *
 *  1. FORCE-LOAD shudhu tokhon, jokhon loader er major.minor device kernel er
 *     sathe mile (5.4.147 -> 5.4.210 OK; 5.10 -> 5.4 NEVER - panic).
 *  2. dmesg e age thekei oops/panic/BUG thakle force-load bondho.
 *  3. Load er por panic signature hole module ta sathe sathe rmmod (rescue),
 *     jate reboot na hoy.
 *  4. Protita decision console e message akare show hoy.
 */
object SafetyGuard {

    /** Crash / instability signatures we never want to force-load on top of. */
    private val PANIC_MARKERS = listOf(
        "kernel panic", "panic occurred", "oops:", "bug: ", "unable to handle",
        "call trace", "softlockup", "hardware watchdog", "internal error: "
    )

    /** "5.4.210" -> "5.4" (major.minor, which is what matters for safety). */
    fun majorMinor(version: String): String {
        val p = OtaDriverStore.verParts(version) ?: return ""
        return "${p.first}.${p.second}"
    }

    /**
     * Is it safe to FORCE-load a module built for [target] on a device running
     * [device]? Only the same major.minor passes - anything else panics on the
     * majority of MediaTek / Qualcomm kernels.
     */
    fun canForceLoad(device: String, target: String): Boolean {
        val d = majorMinor(device)
        return d.isNotEmpty() && d == majorMinor(target)
    }

    /** True when dmesg already shows crash / instability signatures. */
    fun kernelLooksUnstable(): Boolean {
        val text = try {
            Shell.cmd("dmesg 2>/dev/null | tail -n 150").exec().out.joinToString("\n")
        } catch (e: Exception) {
            return false
        }
        val low = text.lowercase()
        return PANIC_MARKERS.any { low.contains(it) }
    }

    /** Names currently present in /proc/modules (baseline before a load). */
    fun loadedModuleNames(): Set<String> = try {
        Shell.cmd("cat /proc/modules 2>/dev/null").exec().out
            .mapNotNull { line ->
                line.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }
            }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }

    /** Modules that appeared since [before] (i.e. the one we just loaded). */
    fun newlyLoaded(before: Set<String>): Set<String> =
        loadedModuleNames().filter { it.isNotBlank() && it !in before }.toSet()

    /**
     * RESCUE: unload a module that loaded but behaves badly, so the kernel does
     * not panic (=> phone does not reboot). Returns true when it was removed.
     */
    fun rescueUnload(moduleName: String): Boolean {
        if (moduleName.isBlank()) return false
        return try {
            Shell.cmd("rmmod -f $moduleName 2>/dev/null || rmmod $moduleName 2>/dev/null")
                .exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /** Console lines shown when a force-load is refused for safety reasons. */
    fun refusalLines(device: String, target: String): List<Pair<String, String>> = listOf(
        "SAFETY: force-load REFUSED - phone restart risk" to "ERR",
        "SAFETY: device kernel $device vs nearest loader $target" to "WARN",
        "SAFETY: major.minor mile na => kernel panic hoy, tai load korchi na" to "WARN",
        "SAFETY: WhatsApp button theke custom loader request korun (kernel $device)" to "INFO"
    )

    /** Console lines shown when the guard stops a load because the kernel is sick. */
    fun unstableLines(): List<Pair<String, String>> = listOf(
        "SAFETY: kernel already unstable (dmesg e oops/panic/call trace ache)" to "ERR",
        "SAFETY: force-load bondho - ei obosthay load korle phone restart hobe" to "ERR",
        "SAFETY: phone ta ekbar normal reboot korun, tarpor abar chesta korun" to "WARN"
    )
}
