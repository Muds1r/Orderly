package com.orderly.app.data.parser

import com.orderly.app.data.tracking.PakCourier

/**
 * Sender domains for Pakistani courier notification emails.
 * Included in IMAP search so hub scans ("arrived at Lahore") are picked up
 * even when Daraz/Temu don't forward every checkpoint.
 */
object CourierRegistry {

    val domainToCourier: Map<String, PakCourier> = mapOf(
        // PostEx
        "postex.pk" to PakCourier.POSTEX,
        "postex.com" to PakCourier.POSTEX,
        "mail.postex.pk" to PakCourier.POSTEX,

        // TCS
        "tcscourier.com" to PakCourier.TCS,
        "tcs.com.pk" to PakCourier.TCS,
        "mail.tcscourier.com" to PakCourier.TCS,

        // Leopards
        "leopardscourier.com" to PakCourier.LEOPARDS,
        "leopardscod.com" to PakCourier.LEOPARDS,
        "mail.leopardscourier.com" to PakCourier.LEOPARDS,

        // Pakistan Post
        "ep.gov.pk" to PakCourier.PAKISTAN_POST,
        "pakpost.gov.pk" to PakCourier.PAKISTAN_POST,

        // Also common with Daraz sellers
        "trax.pk" to PakCourier.TRAX,
        "mulphilog.com" to PakCourier.MNP
    )

    fun courierFromSender(fromHeader: String): PakCourier? {
        val email = Regex("[\\w.+-]+@([\\w.-]+)").find(fromHeader)?.groupValues?.get(1)?.lowercase()
            ?: return null
        return domainToCourier.entries
            .filter { email == it.key || email.endsWith("." + it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    fun allSearchDomains(): Set<String> = domainToCourier.keys
}
