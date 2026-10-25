package com.txao.sms

/** Messaging platforms a contact can have a username on (the same list Txao Phone offers). Pure data, no Android dependencies. */
enum class ImPlatform(val label: String) {
    WhatsApp("WhatsApp"), Telegram("Telegram"), Signal("Signal"), Messenger("Messenger"),
    Instagram("Instagram"), Discord("Discord"), Snapchat("Snapchat"), X("X"), Skype("Skype"), Other("Other");

    /** Link that opens this person's chat or profile, or null when the handle has no usable link (copy it instead). */
    fun link(handle: String): String? {
        val h = handle.trim()
        if (h.isEmpty()) return null
        val user = h.removePrefix("@")
        val digits = h.filter { it.isDigit() }
        return when (this) {
            WhatsApp -> if (digits.length >= 6) "https://wa.me/$digits" else null
            Telegram -> if (h.startsWith("+") && digits.length >= 6) "tg://resolve?phone=$digits" else "https://t.me/$user"
            Signal -> if (h.startsWith("+") && digits.length >= 6) "https://signal.me/#p/+$digits" else "https://signal.me/#u/$user"
            Messenger -> "https://m.me/$user"
            Instagram -> "https://instagram.com/_u/$user"
            Discord -> if (h.all { it.isDigit() } && h.length >= 15) "https://discord.com/users/$h" else null
            Snapchat -> "https://www.snapchat.com/add/$user"
            X -> "https://x.com/$user"
            Skype -> "skype:$user?chat"
            Other -> null
        }
    }

    companion object {
        fun fromName(name: String?): ImPlatform = entries.firstOrNull { it.label.equals(name?.trim(), ignoreCase = true) } ?: Other
    }
}

/** A username on some platform; [platform] keeps the stored name so unknown platforms still show their label. */
data class ImEntry(val platform: String, val handle: String) {
    val known get() = ImPlatform.fromName(platform)
    val displayPlatform get() = if (known == ImPlatform.Other && platform.isNotBlank()) platform else known.label
}
