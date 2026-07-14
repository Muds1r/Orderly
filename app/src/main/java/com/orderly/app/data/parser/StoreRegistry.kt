package com.orderly.app.data.parser

/**
 * Maps sender email domains to store display names. The Gmail / IMAP search is
 * built from this list, so adding a domain automatically includes it in sync.
 *
 * Matching is by domain suffix, so "shipment-tracking@amazon.com" matches
 * "amazon.com". If a store's emails aren't picked up, open one in Gmail, check
 * the sender domain, and add it here.
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

        // Shopify-style / generic (add specific stores as needed)
        "shopify.com" to "Shopify",

        // Local / regional placeholders users can extend
        "j.pk" to "J.",
        "divora.com" to "Divora"
    )

    /** Resolve a store name from the From: header. */
    fun storeFromSender(fromHeader: String): String? {
        val email = Regex("[\\w.+-]+@([\\w.-]+)").find(fromHeader)?.groupValues?.get(1)?.lowercase()
            ?: return null
        return domainToStore.entries
            .filter { email == it.key || email.endsWith("." + it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /** Gmail search query fragment matching all known store domains. */
    fun senderQuery(): String =
        domainToStore.keys.joinToString(" OR ", prefix = "from:(", postfix = ")") { it }
}
