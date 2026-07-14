package com.orderly.app.data.tracking

/**
 * Pakistani couriers used by Daraz, Temu sellers, and local shops.
 * Detection prefers tracking-number shape, then email keywords.
 */
enum class PakCourier(val displayName: String) {
    POSTEX("PostEx"),
    TCS("TCS"),
    LEOPARDS("Leopards"),
    PAKISTAN_POST("Pakistan Post"),
    TRAX("Trax"),
    MNP("M&P"),
    UNKNOWN("Unknown");

    fun trackUrl(trackingNumber: String): String? {
        val cn = normalizeCn(trackingNumber)
        return when (this) {
            POSTEX -> "https://postex.pk/tracking?cn=${cn.encode()}"
            TCS -> "https://www.tcscourier.com/"
            LEOPARDS -> "https://pk.leopardscourier.com/tracking"
            PAKISTAN_POST -> "https://ep.gov.pk/track.asp"
            TRAX -> "https://trax.pk/"
            MNP -> "https://mulphilog.com/"
            UNKNOWN -> null
        }
    }

    companion object {
        /** Strip Daraz / marketplace prefixes so courier sites accept the CN. */
        fun normalizeCn(raw: String): String {
            var s = raw.trim().uppercase().replace(" ", "")
            listOf(
                "PK-LCS-", "PK-LCS", "PK-POSTEX-", "PK-POSTEX",
                "PK-TCS-", "PK-TCS", "PK-DEX-", "PK-DEX",
                "FDS-", "DEX-"
            ).forEach { prefix ->
                if (s.startsWith(prefix)) s = s.removePrefix(prefix)
            }
            // PK-LCSGT… style without second dash
            if (s.startsWith("PKLCS")) s = s.removePrefix("PKLCS")
            return s.trim('-')
        }

        fun detect(trackingNumber: String?, emailText: String? = null): PakCourier {
            val cn = trackingNumber?.let { normalizeCn(it) }.orEmpty()
            val text = (emailText.orEmpty() + " " + (trackingNumber.orEmpty())).lowercase()

            // Explicit name in email wins when clear.
            when {
                listOf("postex", "post-ex", "post ex").any { it in text } &&
                    !listOf("leopards", "tcs courier").any { it in text } -> return POSTEX
                listOf("leopards", "leopard courier", "lcs courier", "pk-lcs").any { it in text } ->
                    return LEOPARDS
                listOf("tcs courier", "tcscourier", " shipped via tcs", "courier: tcs").any { it in text } ||
                    Regex("""\btcs\b""").containsMatchIn(text) && "postex" !in text -> return TCS
                listOf("pakistan post", "pakpost", "ep.gov.pk").any { it in text } ->
                    return PAKISTAN_POST
                listOf("trax courier", "trax.pk").any { it in text } -> return TRAX
                listOf("m&p", "mulphilog", "mnp courier").any { it in text } -> return MNP
            }

            if (cn.isBlank()) return UNKNOWN

            return when {
                // PostEx: PE… or CX-…
                cn.startsWith("PE") || cn.startsWith("CX-") || cn.startsWith("CX") && cn.length in 10..20 ->
                    POSTEX
                // TCS: 13 digits often starting with 777 / 77
                cn.matches(Regex("""77\d{11}""")) || cn.matches(Regex("""\d{11,13}""")) && cn.startsWith("77") ->
                    TCS
                // Leopards: 2 letters + 10 digits (GT7514208669)
                cn.matches(Regex("""[A-Z]{2}\d{9,12}""")) -> LEOPARDS
                cn.startsWith("TRX") -> TRAX
                else -> UNKNOWN
            }
        }

        private fun String.encode(): String =
            java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
    }
}
