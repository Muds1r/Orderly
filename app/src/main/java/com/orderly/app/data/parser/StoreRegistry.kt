package com.orderly.app.data.parser

/**
 * Optional known-domain map (faster / cleaner names for big stores).
 *
 * Unknown shops still work: IMAP also searches by order/shipping keywords, and
 * the store name is taken from the From: display name
 * (e.g. "Saeed Ghani <…@saeedghani1888.com>" → "Saeed Ghani").
 */
object StoreRegistry {

    val domainToStore: Map<String, String> = mapOf(
        // Amazon
        "amazon.com" to "Amazon",
        "amazon.co.uk" to "Amazon",
        "amazon.de" to "Amazon",
        "amazon.in" to "Amazon",
        "amazon.ae" to "Amazon",
        "amazon.sg" to "Amazon",
        "amazon.ca" to "Amazon",

        // AliExpress / Alibaba
        "aliexpress.com" to "AliExpress",
        "alibaba.com" to "AliExpress",

        // Temu
        "temu.com" to "Temu",
        "temuemail.com" to "Temu",

        // Daraz (Pakistan / South Asia)
        "daraz.pk" to "Daraz",
        "daraz.com" to "Daraz",
        "daraz.com.pk" to "Daraz",
        "daraz.lk" to "Daraz",
        "daraz.com.bd" to "Daraz",

        // eBay
        "ebay.com" to "eBay",
        "ebay.co.uk" to "eBay",

        // Walmart / Target / common US
        "walmart.com" to "Walmart",
        "target.com" to "Target",

        // Shein / other marketplaces
        "shein.com" to "SHEIN",
        "sheinmail.com" to "SHEIN",

        // Etsy
        "etsy.com" to "Etsy",

        // Shopify platform + email relay (name from From: display)
        "shopify.com" to "Shopify",
        "shopifyemail.com" to "Shopify",

        // Common Pakistan brands (optional shortcuts)
        "saeedghani1888.com" to "Saeed Ghani",
        "saeedghani.com" to "Saeed Ghani",
        "alerts.saeedghani.com" to "Saeed Ghani",
        "divora.com" to "Divora",
        "j.pk" to "J."
    )

    private val shopifyRelaySuffixes = listOf(
        "shopifyemail.com",
        "shopify.com"
    )

    fun isKnownDomain(fromHeader: String): Boolean {
        val email = senderEmail(fromHeader) ?: return false
        return domainToStore.keys.any { email == it || email.endsWith(".$it") }
    }

    /**
     * Prefer mapped domain name; otherwise use the From: display name so any
     * XYZ shop works without being added to [domainToStore].
     */
    fun resolveStoreName(fromHeader: String): String? {
        storeFromSender(fromHeader)?.let { return it }
        return displayName(fromHeader)?.takeIf { it.length >= 2 }
    }

    /** Resolve a store name from a known domain (null if unknown). */
    fun storeFromSender(fromHeader: String): String? {
        val email = senderEmail(fromHeader) ?: return null

        val mapped = domainToStore.entries
            .filter { email == it.key || email.endsWith("." + it.key) }
            .maxByOrNull { it.key.length }
            ?.value

        val isShopifyRelay = shopifyRelaySuffixes.any { email == it || email.endsWith(".$it") }
        if (isShopifyRelay || mapped == "Shopify") {
            displayName(fromHeader)?.let { name ->
                if (name.isNotBlank() &&
                    !name.equals("Shopify", ignoreCase = true) &&
                    !name.equals("store", ignoreCase = true)
                ) {
                    return name
                }
            }
            return mapped ?: "Shopify"
        }

        return mapped
    }

    fun displayName(fromHeader: String): String? {
        val beforeAngle = fromHeader.substringBefore("<").trim()
            .trim('"', '\'')
            .trim()
        if (beforeAngle.isBlank() || "@" in beforeAngle) return null
        // Drop trailing "via Shopify" style noise
        return beforeAngle
            .replace(Regex("""\s+via\s+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .take(40)
            .takeIf { it.length >= 2 }
    }

    private fun senderEmail(fromHeader: String): String? =
        Regex("[\\w.+-]+@([\\w.-]+)").find(fromHeader)?.groupValues?.get(1)?.lowercase()

    /** Gmail search fragment for known domains (optional boost). */
    fun senderQuery(): String =
        domainToStore.keys.joinToString(" OR ", prefix = "from:(", postfix = ")") { it }
}
