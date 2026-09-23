package com.kernelloader.driver

import android.os.Build
import java.net.URLEncoder

/**
 * Support / custom loader request contact.
 * When no loader exists in the database for a device's kernel,
 * or a load fails, the user can message us on WhatsApp -
 * we build a custom loader for their kernel version.
 */
object SupportContact {
    /** App name used inside the message header. */
    const val APP_NAME = "Kernel Loder"

    /** Chat number in international format (no +, no spaces). */
    const val WHATSAPP_NUMBER = "8801785917145"

    /** Pretty display form for the UI. */
    const val WHATSAPP_DISPLAY = "+880 1785-917145"

    /**
     * Pre-filled WhatsApp message: device model + running kernel release,
     * formatted as a stylish bordered card (emoji + box drawing) so the
     * request stands out in the chat.
     */
    fun defaultMessage(kernelRelease: String?): String {
        val model = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim()
        val k = kernelRelease?.takeIf { it.isNotBlank() } ?: "unknown"
        val android = Build.VERSION.RELEASE ?: "?"
        val sdk = Build.VERSION.SDK_INT
        return buildString {
            appendLine("╔══════════════════════════╗")
            appendLine("   ⚡  KERNEL LODER SUPPORT  ⚡")
            appendLine("╚══════════════════════════╝")
            appendLine()
            appendLine("📱 Device  : $model")
            appendLine("🐧 Kernel  : $k")
            appendLine("🤖 Android : $android (API $sdk)")
            appendLine("📦 App     : $APP_NAME")
            appendLine()
            appendLine("❗ No matching loader for this kernel version in the database.")
            appendLine("🛠️ Please build a custom loader (.ko) for this kernel.")
            appendLine()
            append("➖➖➖➖➖➖➖➖➖➖➖➖")
        }
    }

    /**
     * Same message, but with the user's own extra text appended - so he can
     * describe his problem (bootloop, which app he needs, etc.) in his own words.
     */
    fun messageWithCustom(kernelRelease: String?, custom: String): String {
        val base = defaultMessage(kernelRelease)
        val extra = custom.trim()
        return if (extra.isEmpty()) base
        else base + "\n\n📝 My note:\n" + extra + "\n\n➖➖➖➖➖➖➖➖➖➖➖➖"
    }

    /**
     * Full failure report: device + kernel + app + result + last log lines,
     * capped so the wa.me / issue URL stays openable.
     */
    fun failureReport(
        kernelRelease: String?,
        appVersion: String,
        result: String,
        logText: String,
        maxLogChars: Int = 2200
    ): String {
        val model = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim()
        val k = kernelRelease?.takeIf { it.isNotBlank() } ?: "unknown"
        val android = Build.VERSION.RELEASE ?: "?"
        val tail = logText.lines()
            .filter { it.isNotBlank() }
            .takeLast(30)
            .joinToString("\n")
            .takeLast(maxLogChars)
        return buildString {
            appendLine("╔══════════════════════════╗")
            appendLine("   ⚡ KERNEL LODER REPORT ⚡")
            appendLine("╚══════════════════════════╝")
            appendLine()
            appendLine("📱 Device  : $model")
            appendLine("🐧 Kernel  : $k")
            appendLine("🤖 Android : $android (API ${Build.VERSION.SDK_INT})")
            appendLine("📦 App     : $APP_NAME $appVersion")
            appendLine("📊 Result  : ${result.take(120)}")
            appendLine()
            appendLine("📝 LOG:")
            append(tail.ifBlank { "(empty)" })
        }
    }

    /** Clickable wa.me deep-link with the FULL report (no custom textbox needed). */
    fun waLinkFull(kernelRelease: String?, appVersion: String, result: String, logText: String): String =
        "https://wa.me/$WHATSAPP_NUMBER?text=" +
                URLEncoder.encode(failureReport(kernelRelease, appVersion, result, logText), "UTF-8")

    /**
     * Pre-filled GitHub issue URL: one tap opens the issue composer with the
     * full report, user hits Submit (their account) - no token inside the app.
     * PC side (driver/failure_watch.sh) triages these [AUTO-REPORT] issues.
     */
    fun issueUrl(kernelRelease: String?, appVersion: String, result: String, logText: String): String {
        val short = kernelRelease?.let {
            Regex("""(\d+)\.(\d+)\.(\d+)""").find(it)?.value
        } ?: "unknown-kernel"
        val title = "[AUTO-REPORT] load failed on $short"
        val body = "Auto failure report from $APP_NAME $appVersion.\n\n```\n" +
                failureReport(kernelRelease, appVersion, result, logText) + "\n```"
        return "https://github.com/bmjubairdadu/kernel-loder/issues/new?title=" +
                URLEncoder.encode(title, "UTF-8") + "&body=" +
                URLEncoder.encode(body, "UTF-8")
    }
}
