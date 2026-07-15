package com.orderly.app.data.tracking

/** Reject month names / junk mistaken for city locations in timeline parsing. */
object LocationNames {

    private val months = setOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
        "oct", "nov", "dec"
    )

    private val junk = setOf(
        "status", "reason", "current", "package", "shipment", "order",
        "track", "tracking", "courier", "delivery", "delivered", "transit"
    )

    fun sanitize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim().replace(Regex("""\s+"""), " ")
        if (t.length < 2 || t.length > 60) return null
        val lower = t.lowercase()
        if (lower in months || lower in junk) return null
        if (t.all { it.isDigit() || it == '/' || it == '-' || it == ',' || it.isWhitespace() }) {
            return null
        }
        // "13 Jul 2026" style dates
        if (Regex("""^\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4}""").containsMatchIn(t)) return null
        return t
    }

    /** City allowlist + "to/in/at CITY" patterns; never return a month name. */
    fun fromTrackingText(plain: String): String? {
        val known = Regex(
            """\b(Karachi|Lahore|Islamabad|Rawalpindi|Multan|Faisalabad|Peshawar|Quetta|Hyderabad|Gujrat|Sialkot|Gujranwala|Sukkur|Bahawalpur|Sargodha|Abbottabad|Mardan|Mingora|Sheikhupura|Chicha\s+Watni)\b""",
            RegexOption.IGNORE_CASE
        ).find(plain)?.value
        sanitize(known)?.let { return it }

        // "Dispatched to ISLAMABAD" / "Shipment picked in CHICHA WATNI"
        val patterned = Regex(
            """\b(?:to|in|at|from)\s+([A-Za-z][A-Za-z]+(?:\s+[A-Za-z][A-Za-z]+){0,3})\b""",
            RegexOption.IGNORE_CASE
        ).find(plain)?.groupValues?.get(1)?.trim()
        return sanitize(patterned)?.split(" ")?.joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { c -> c.uppercaseChar() }
        }
    }
}
