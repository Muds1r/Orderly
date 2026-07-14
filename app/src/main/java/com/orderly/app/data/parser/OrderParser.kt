package com.orderly.app.data.parser

import com.orderly.app.data.db.OrderEntity
import com.orderly.app.data.db.OrderStatus
import com.orderly.app.data.db.TrackingEventDraft
import com.orderly.app.data.tracking.PakCourier

/**
 * Heuristic parser for shopping-order emails (Amazon, Daraz, Temu, AliExpress, …)
 * plus Pakistani courier updates (PostEx, TCS, Leopards, Pakistan Post).
 */
object OrderParser {

    private val orderNumberRegexes = listOf(
        Regex("""\border\s*(?:number|#|no\.?|id)?\s*[:#]?\s*([A-Z0-9][A-Z0-9\-]{5,})\b""", RegexOption.IGNORE_CASE),
        Regex("""\border\s+([A-Z0-9]{3}-\d{7}-\d{7})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:ORD|ORDER)[-_]?([A-Z0-9]{6,})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b#([A-Z0-9]{8,})\b""")
    )

    private val trackingRegexes = listOf(
        Regex("""\btracking\s*(?:number|#|no\.?|cn|consignment)?\s*[:#]?\s*([A-Z0-9\-]{8,})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:CN|AWB|consignment)\s*(?:number|#|no\.?)?\s*[:#]?\s*([A-Z0-9\-]{8,})\b""", RegexOption.IGNORE_CASE),
        Regex(
            """\b(?:PK-LCS-|PK-POSTEX-|PK-TCS-|PK-DEX-|FDS-)?([A-Z]{2}\d{9,12})\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""\b((?:PE|CX)[-]?[A-Z0-9]{8,})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(77\d{11})\b"""),
        Regex("""\b(?:1Z[A-Z0-9]{16})\b""")
    )

    private val amountRegexes = listOf(
        Regex(
            """(?:total|grand\s*total|order\s*total|amount|paid)\s*:?\s*(?:Rs\.?|PKR\.?|₨|USD|US\$|\$|€|£)?\s*([\d,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""(?:Rs\.?|PKR\.?|₨)\s*:?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:USD|US\$|\$)\s*([\d,]+(?:\.\d{1,2})?)""")
    )

    private val productRegexes = listOf(
        Regex("""(?:item|product|bought|ordered)\s*:?\s*(.{5,80}?)(?=\s*\r?\n|\s{2,}|$)""", RegexOption.IGNORE_CASE),
        Regex(""""([^"]{5,80})"""")
    )

    private val shipFromRegexes = listOf(
        Regex(
            """(?:shipped\s+from|shipping\s+from|dispatched\s+from|origin|from\s+warehouse)\s*:?\s*([A-Za-z][A-Za-z0-9 ,\-]{2,60})""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?:warehouse|fulfillment\s+center)\s*:?\s*([A-Za-z][A-Za-z0-9 ,\-]{2,40})""",
            RegexOption.IGNORE_CASE
        )
    )

    private val locationEventRegexes = listOf(
        Regex(
            """(?:arrived\s+at|reached|at\s+facility|scanned\s+at|departed\s+from|left)\s+([A-Za-z][A-Za-z0-9 ,\-]{2,50})""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?:out\s+for\s+delivery(?:\s+in|\s+to)?|delivering\s+to)\s+([A-Za-z][A-Za-z0-9 ,\-]{2,40})""",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            """(?:currently\s+in|package\s+is\s+in)\s+([A-Za-z][A-Za-z0-9 ,\-]{2,40})""",
            RegexOption.IGNORE_CASE
        )
    )

    data class ParseResult(
        val order: OrderEntity,
        val messageId: String,
        val events: List<TrackingEventDraft> = emptyList(),
        /** True when this mail is a courier update matched by tracking # only. */
        val trackingOnly: Boolean = false
    )

    fun parse(
        messageId: String,
        fromHeader: String,
        subject: String,
        body: String,
        timestamp: Long
    ): ParseResult? {
        val store = StoreRegistry.storeFromSender(fromHeader)
        val courierFromDomain = CourierRegistry.courierFromSender(fromHeader)
        val text = (subject + "\n" + body).replace('\u00a0', ' ')
        val lower = text.lowercase()

        val status = detectStatus(subject, lower)
        val orderNumber = extractOrderNumber(text, subject)
        val trackingRaw = extractTracking(text, orderNumber)
        val tracking = trackingRaw?.let { PakCourier.normalizeCn(it) }

        // Courier-only mail (PostEx / TCS / Leopards) without a store match.
        if (store == null) {
            if (courierFromDomain == null && tracking == null) return null
            if (tracking == null && status == OrderStatus.UNKNOWN) return null
            return parseCourierUpdate(
                messageId = messageId,
                subject = subject,
                text = text,
                lower = lower,
                timestamp = timestamp,
                tracking = tracking,
                status = status,
                courierHint = courierFromDomain
            )
        }

        if (orderNumber == null && status == OrderStatus.UNKNOWN && !looksLikeOrderMail(lower)) {
            return null
        }

        val amount = extractAmount(text)
        val product = extractProduct(text, subject)
        val currency = detectCurrency(text)
        val shipFrom = extractShipFrom(text)
        val courier = PakCourier.detect(tracking, text)
        val carrierName = when {
            courier != PakCourier.UNKNOWN -> courier.displayName
            else -> null
        }
        val paymentStatus = when {
            status == OrderStatus.AWAITING_PAYMENT -> "Awaiting payment"
            status == OrderStatus.PAID || status == OrderStatus.PROCESSING ||
                status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED -> "Paid"
            else -> null
        }

        val id = if (!orderNumber.isNullOrBlank()) {
            "${store.lowercase()}|$orderNumber"
        } else {
            messageId
        }

        val locationEvents = extractLocationEvents(text, timestamp, messageId, status)
        val statusEvent = if (status != OrderStatus.UNKNOWN) {
            listOf(
                TrackingEventDraft(
                    occurredAt = timestamp,
                    status = status,
                    location = locationEvents.firstOrNull()?.location ?: shipFrom,
                    description = subject.ifBlank { status.name },
                    source = "email",
                    fingerprint = "email|$messageId|status",
                    messageId = messageId
                )
            )
        } else {
            emptyList()
        }

        val events = (statusEvent + locationEvents)
            .distinctBy { it.fingerprint }

        val lastLocation = events.mapNotNull { it.location }.firstOrNull()

        val order = OrderEntity(
            id = id,
            store = store,
            orderNumber = orderNumber,
            productSummary = product,
            trackingNumber = tracking,
            carrier = carrierName,
            shipFrom = shipFrom,
            lastLocation = lastLocation,
            orderDate = timestamp,
            amount = amount,
            currency = currency,
            paymentStatus = paymentStatus,
            status = status,
            estimatedDelivery = null,
            subject = subject,
            lastMessageId = messageId,
            updatedAt = System.currentTimeMillis()
        )
        return ParseResult(order, messageId, events)
    }

    private fun parseCourierUpdate(
        messageId: String,
        subject: String,
        text: String,
        lower: String,
        timestamp: Long,
        tracking: String?,
        status: OrderStatus,
        courierHint: PakCourier?
    ): ParseResult? {
        if (tracking.isNullOrBlank()) return null
        val courier = courierHint?.takeIf { it != PakCourier.UNKNOWN }
            ?: PakCourier.detect(tracking, text)
        val shipFrom = extractShipFrom(text)
        val locationEvents = extractLocationEvents(text, timestamp, messageId, status)
        val events = buildList {
            add(
                TrackingEventDraft(
                    occurredAt = timestamp,
                    status = status.takeIf { it != OrderStatus.UNKNOWN } ?: OrderStatus.IN_TRANSIT,
                    location = locationEvents.firstOrNull()?.location,
                    description = subject.ifBlank { "${courier.displayName} update" },
                    source = "email",
                    fingerprint = "email|$messageId|courier",
                    messageId = messageId
                )
            )
            addAll(locationEvents)
        }.distinctBy { it.fingerprint }

        // Placeholder order — SyncEngine merges onto existing order by tracking #.
        val order = OrderEntity(
            id = "tracking|$tracking",
            store = courier.displayName,
            orderNumber = null,
            productSummary = null,
            trackingNumber = tracking,
            carrier = courier.displayName,
            shipFrom = shipFrom,
            lastLocation = events.mapNotNull { it.location }.firstOrNull(),
            orderDate = timestamp,
            amount = null,
            currency = null,
            paymentStatus = null,
            status = status.takeIf { it != OrderStatus.UNKNOWN } ?: OrderStatus.IN_TRANSIT,
            estimatedDelivery = null,
            subject = subject,
            lastMessageId = messageId,
            updatedAt = System.currentTimeMillis()
        )
        return ParseResult(order, messageId, events, trackingOnly = true)
    }

    private fun looksLikeOrderMail(lower: String): Boolean {
        val signals = listOf(
            "order", "shipped", "shipping", "delivered", "tracking",
            "package", "purchase", "invoice", "out for delivery", "dispatch",
            "consignment", "cn number", "postex", "leopards", "tcs"
        )
        return signals.any { it in lower }
    }

    fun detectStatus(subject: String, lowerBody: String): OrderStatus {
        val s = (subject + " " + lowerBody).lowercase()
        return when {
            listOf("cancelled", "canceled", "order cancel").any { it in s } -> OrderStatus.CANCELLED
            listOf("returned", "return received", "refund").any { it in s } &&
                "return" in s -> OrderStatus.RETURNED
            listOf("out for delivery", "arriving today").any { it in s } -> OrderStatus.OUT_FOR_DELIVERY
            listOf("delayed", "exception", "delivery attempt").any { it in s } -> OrderStatus.DELAYED
            listOf("delivered", "was delivered", "has been delivered", "delivered successfully")
                .any { it in s } -> OrderStatus.DELIVERED
            listOf(
                "in transit", "on the way", "on its way", "arrived at", "reached",
                "departed", "scanned at"
            ).any { it in s } -> OrderStatus.IN_TRANSIT
            listOf("shipped", "dispatched", "has shipped", "on the way to you", "picked up")
                .any { it in s } -> OrderStatus.SHIPPED
            listOf("awaiting payment", "payment pending", "complete your payment").any { it in s } ->
                OrderStatus.AWAITING_PAYMENT
            listOf("payment received", "payment confirmed", "we've received your payment").any { it in s } ->
                OrderStatus.PAID
            listOf(
                "order confirmed", "order confirmation", "thanks for your order",
                "thank you for your order", "order placed", "we're preparing"
            ).any { it in s } -> OrderStatus.PROCESSING
            else -> OrderStatus.UNKNOWN
        }
    }

    private fun extractOrderNumber(text: String, subject: String): String? {
        for (regex in orderNumberRegexes) {
            regex.find(subject)?.groupValues?.get(1)?.let { return cleanToken(it) }
        }
        for (regex in orderNumberRegexes) {
            regex.find(text)?.groupValues?.get(1)?.let { return cleanToken(it) }
        }
        return null
    }

    private fun extractTracking(text: String, orderNumber: String?): String? {
        for (regex in trackingRegexes) {
            regex.find(text)?.groupValues?.get(1)?.let { candidate ->
                val cleaned = PakCourier.normalizeCn(cleanToken(candidate))
                if (cleaned != orderNumber && cleaned.length >= 8) return cleaned
            }
        }
        return null
    }

    private fun extractShipFrom(text: String): String? {
        for (regex in shipFromRegexes) {
            val candidate = regex.find(text)?.groupValues?.get(1)?.trim()?.take(60)
            if (!candidate.isNullOrBlank() && !candidate.contains('@')) {
                return candidate.replace(Regex("""\s+"""), " ")
            }
        }
        return null
    }

    private fun extractLocationEvents(
        text: String,
        timestamp: Long,
        messageId: String,
        status: OrderStatus
    ): List<TrackingEventDraft> {
        val events = mutableListOf<TrackingEventDraft>()
        for (regex in locationEventRegexes) {
            regex.findAll(text).forEach { match ->
                val location = match.groupValues[1].trim()
                    .replace(Regex("""[.,;:]+$"""), "")
                    .take(60)
                if (location.length < 3) return@forEach
                val desc = match.value.trim().replace(Regex("""\s+"""), " ").take(160)
                events += TrackingEventDraft(
                    occurredAt = timestamp,
                    status = status.takeIf { it != OrderStatus.UNKNOWN } ?: OrderStatus.IN_TRANSIT,
                    location = location,
                    description = desc,
                    source = "email",
                    fingerprint = "email|$messageId|loc|${location.lowercase()}",
                    messageId = messageId
                )
            }
        }
        return events.distinctBy { it.location?.lowercase() }
    }

    private fun extractAmount(text: String): Double? {
        for (regex in amountRegexes) {
            val match = regex.find(text) ?: continue
            val value = match.groupValues[1].replace(",", "").toDoubleOrNull()
            if (value != null && value > 0) return value
        }
        return null
    }

    private fun extractProduct(text: String, subject: String): String? {
        for (regex in productRegexes) {
            val candidate = regex.find(text)?.groupValues?.get(1)?.trim()
            if (candidate != null && candidate.length in 5..80 && !candidate.contains('@')) {
                return candidate.replace(Regex("""\s+"""), " ")
            }
        }
        val cleaned = subject
            .replace(Regex("""^(Your|New|Update:)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.takeIf { it.length in 5..80 }
    }

    private fun detectCurrency(text: String): String? = when {
        Regex("""\b(?:Rs\.?|PKR|₨)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "PKR"
        Regex("""\b(?:USD|US\$)\b""").containsMatchIn(text) ||
            Regex("""\$\s*[\d,]""").containsMatchIn(text) -> "USD"
        Regex("""€\s*[\d,]""").containsMatchIn(text) -> "EUR"
        Regex("""£\s*[\d,]""").containsMatchIn(text) -> "GBP"
        else -> null
    }

    private fun cleanToken(value: String): String =
        value.trim().trimEnd('.', ',', ';')
}
