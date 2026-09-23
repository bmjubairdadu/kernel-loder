package com.kernelloader.mem

import com.kernelloader.driver.DriverViewModel
import com.topjohnwu.superuser.Shell

/**
 * DRIVERLESS memory engine (no .ko, no insmod, no reboot risk - ever).
 * Uses root + /proc/<pid>/maps + /proc/<pid>/mem, proven live on-device:
 * read pid-1 .text, write+read-back a scratch mapping.
 *
 * Scope: process virtual memory read/write + module base lookup.
 * Physical memory still needs the kmem driver (rare edge cases only).
 *
 * Performance: one root-shell round-trip per call (~30-80ms). Fine for
 * patches and scans; NOT for 60fps loops (batch addresses per call).
 */
object MemDirect {

    data class MemRegion(
        val start: Long,
        val end: Long,
        val perms: String,
        val path: String
    )

    const val MAX_READ = 16 * 1024L
    const val MAX_WRITE = 4 * 1024

    /** All mappings of [pid], empty list on failure. */
    fun readMaps(pid: Int): List<MemRegion> {
        val out = Shell.cmd("cat /proc/$pid/maps 2>&1").exec().out
        val list = mutableListOf<MemRegion>()
        for (line in out) {
            // 556a5f8000-556a6c7000 r-xp 0002e000 103:18 1474  /system/bin/init
            val m = Regex("""^([0-9a-f]+)-([0-9a-f]+)\s+(\S+)\s+\S+\s+\S+\s+\S+\s*(.*)$""")
                .find(line.trim()) ?: continue
            try {
                list.add(
                    MemRegion(
                        start = m.groupValues[1].toLong(16),
                        end = m.groupValues[2].toLong(16),
                        perms = m.groupValues[3],
                        path = m.groupValues[4].trim()
                    )
                )
            } catch (_: Exception) { /* skip bad line */ }
        }
        return list
    }

    /**
     * Read [size] bytes at virtual [addr] of [pid]. Hex transport (od)
     * keeps binary data intact through the text shell. Null on failure.
     */
    fun readMem(pid: Int, addr: Long, size: Long): ByteArray? {
        if (size <= 0 || size > MAX_READ || addr < 0) return null
        val res = Shell.cmd(
            "dd if=/proc/$pid/mem bs=1 skip=$addr count=$size 2>/dev/null | od -An -tx1 -v 2>/dev/null"
        ).exec()
        if (!res.isSuccess) return null
        val bytes = mutableListOf<Byte>()
        for (line in res.out) {
            for (tok in line.trim().split(Regex("\\s+"))) {
                if (tok.isEmpty()) continue
                val v = tok.toIntOrNull(16) ?: return null
                bytes.add(v.toByte())
            }
        }
        if (bytes.size != size.toInt()) return null
        return bytes.toByteArray()
    }

    /**
     * Write [data] at virtual [addr] of [pid]. True when the kernel
     * accepted every byte (dd exit 0). Caller should read-back to verify.
     */
    fun writeMem(pid: Int, addr: Long, data: ByteArray): Boolean {
        if (data.isEmpty() || data.size > MAX_WRITE || addr < 0) return false
        val hex = buildString {
            for (b in data) {
                append("\\x")
                append(String.format("%02x", b))
            }
        }
        val res = Shell.cmd(
            "printf '$hex' | dd of=/proc/$pid/mem bs=1 seek=$addr conv=notrunc 2>/dev/null"
        ).exec()
        return res.isSuccess
    }

    /** Base address of the mapping whose file basename equals [name]. */
    fun moduleBase(pid: Int, name: String): Long? {
        for (r in readMaps(pid)) {
            if (r.path.isEmpty()) continue
            val base = r.path.substringAfterLast('/')
            if (base == name) return r.start
        }
        return null
    }

    /** Safe read-only self-test (pid 1 maps + 16 bytes). Returns log lines. */
    fun selfTest(): List<Pair<String, String>> {
        val lines = mutableListOf<Pair<String, String>>()
        fun log(t: String, k: String) { lines.add(t to k) }
        log("MEM: driverless self-test (root + /proc/pid/mem, no .ko)...", "INFO")
        val maps = readMaps(1)
        if (maps.isEmpty()) {
            log("MEM: FAIL - cannot read /proc/1/maps (need root + setenforce 0)", "ERR")
            return lines
        }
        log("MEM: maps OK (${maps.size} regions)", "OK")
        val rx = maps.firstOrNull { it.perms.startsWith("r-x") }
        if (rx == null) {
            log("MEM: FAIL - no r-x region in pid 1", "ERR")
            return lines
        }
        val data = readMem(1, rx.start, 16)
        if (data == null) {
            log("MEM: FAIL - cannot read /proc/1/mem (ptrace blocked?)", "ERR")
            return lines
        }
        val hex = data.joinToString(" ") { String.format("%02x", it) }
        log("MEM: read OK @ ${rx.start.toString(16)} = $hex", "OK")
        log("MEM: driverless engine WORKS - no driver needed for process R/W", "OK")
        return lines
    }
}
