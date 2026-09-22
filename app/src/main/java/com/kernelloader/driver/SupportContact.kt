package com.kernelloader.driver

import android.os.Build
import java.net.URLEncoder

/**
 * Support / custom loader request contact.
 * Jodi kono device er kernel er jonno database e loader na thake
 * ba load fail hoy, user WhatsApp e message korte parbe -
 * amra tar kernel version er jonno custom loader banie dei.
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
            appendLine("❗ Database e ei kernel version er matching loader nai.")
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

    /** Clickable wa.me deep-link with the pre-filled message (+ optional custom text). */
    fun waLink(kernelRelease: String?, custom: String = ""): String =
        "https://wa.me/$WHATSAPP_NUMBER?text=" +
                URLEncoder.encode(messageWithCustom(kernelRelease, custom), "UTF-8")
}
