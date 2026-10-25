package com.txao.phone

/** Classifies what was typed on the dial pad: a normal number, or a special code the dialer must handle itself. */
sealed interface DialCode {
    data object None : DialCode
    /** `*#06#`: show device identifiers. */
    data object Imei : DialCode
    /** `*#*#1234#*#*`: hidden code sent to apps that registered for it. */
    data class Secret(val code: String) : DialCode
    /** `*123#`, `#31#`, `**21*+43…#`: network (USSD / MMI) request. */
    data class Ussd(val code: String) : DialCode

    companion object {
        private val secret = Regex("""^\*#\*#(\d+)#\*#\*$""")
        private val ussd = Regex("""^[*#][0-9*#+]*#$""")

        fun parse(input: String): DialCode {
            val s = input.trim()
            if (s == "*#06#") return Imei
            secret.find(s)?.let { return Secret(it.groupValues[1]) }
            // A plain number never starts with * or # and ends with #; a lone "#" or "*" key press is not a code.
            if (s.length >= 3 && ussd.matches(s)) return Ussd(s)
            return None
        }
    }
}
