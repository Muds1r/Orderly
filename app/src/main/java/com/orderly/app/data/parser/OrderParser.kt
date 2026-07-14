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
        Regex("""\border\s*(?:number|#|no\.?|id)?\s*[:#]?\s*([A-Z0-9][A-Z0-9\-]{3,})\b""", RegexOption.IGNORE_CASE),
        Regex("""\border\s+#?\s*(\d{4,})\b""", RegexOption.IGNORE_CASE),
        Regex("""\border\s+([A-Z0-9]{3}-\d{7}-\d{7})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:ORD|ORDER)[-_]?([A-Z0-9]{4,})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b#(\d{4,})\b""")
    )

    private val trackingRegexes = listOf(
        // "M&P tracking number: …" / "Leopards Courier tracking number: …"
        Regex(
            """(?:M\s*&\s*P|Leopards(?:\s+Courier)?|PostEx|TCS|Trax|Mulphilog)\s+tracking\s*(?:number|#|no\.?)?\s*[:#]?\s*([A-Z0-9\-]{8,})\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""\btracking\s*(?:number|#|no\.?|cn|consignment)?\s*[:#]?\s*([A-Z0-9\-]{8,})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:CN|AWB|consignment)\s*(?:number|#|no\.?)?\s*[:#]?\s*([A-Z0-9\-]{8,})\b""", RegexOption.IGNORE_CASE),
        Regex(
            """\b(?:PK-LCS-|PK-POSTEX-|PK-TCS-|PK-DEX-|FDS-)?([A-Z]{2}\d{9,12})\b""",
            RegexOption.IGNORE_CASE
        ),
        Regex("""\b((?:PE|CX)[-]?[A-Z0-9]{8,})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(77\d{11})\b"""),
        // M&P / generic long numeric CN (10–20 digits), used after labeled forms fail
        Regex("""\b(\d{10,20})\b"""),
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
        val knownStore = StoreRegistry.storeFromSender(fromHeader)
        val store = StoreRegistry.resolveStoreName(fromHeader)
        val courierFromDomain = CourierRegistry.courierFromSender(fromHeader)
        val text = (subject + "\n" + body).replace('\u00a0', ' ')
        val lower = text.lowercase()
        val subjectLower = subject.lowercase()

        // Drop foodpanda / promo / voucher mail early.
        if (isPromoOrUnrelated(fromHeader, subjectLower, lower)) return null

        val status = detectStatus(subject, lower)
        val orderNumber = extractOrderNumber(text, subject)
        val trackingRaw = extractTracking(text, orderNumber)
        val tracking = trackingRaw?.let { PakCourier.normalizeCn(it) }

        // Pure courier notification with no shop name — attach by tracking # later.
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

        // Real orders need an order # and/or a tracking #.
        // Promo blasts that only say "order food" / "% off" never qualify.
        val looksLike = looksLikeOrderMail(lower)
        val hasIdentity = orderNumber != null || tracking != null
        if (!hasIdentity) return null
        if (!looksLike && status == OrderStatus.UNKNOWN) return null
        // Unknown shops: require shipping/order language, not just a random #.
        if (knownStore == null && !looksLikeStrict(lower, subjectLower)) return null

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

    /** Foodpanda deals, vouchers, % off — never treat as purchase archive rows. */
    fun isPromoOrUnrelated(fromHeader: String, subjectLower: String, bodyLower: String): Boolean {
        val from = fromHeader.lowercase()
        val blockedSenders = listOf(
            "foodpanda", "pandamart", "careem", "bykea", "uber.com",
            "mcdonalds", "kfc.", "pizza hut", "domino", "newsletter@",
            "marketing@", "promo@", "offers@", "deals@"
        )
        if (blockedSenders.any { it in from }) return true

        val promoSubject = listOf(
            "% off", "%off", "off on", "discount", "voucher", "coupon",
            "flash sale", "limited offer", "free delivery on", "order now and",
            "offering", "promo code", "use code", "deal of", "cashback offer"
        )
        if (promoSubject.any { it in subjectLower }) return true

        // Subject is clearly marketing with no order identity language
        val orderishSubject = listOf(
            "order #", "order number", "your order", "has shipped", "on the way",
            "tracking", "out for delivery", "delivered", "dispatched", "shipment"
        )
        if (promoSubject.any { it in bodyLower.take(400) } &&
            orderishSubject.none { it in subjectLower } &&
            "tracking number" !in bodyLower
        ) {
            return true
        }
        return false
    }

    private fun looksLikeStrict(lower: String, subjectLower: String): Boolean {
        val strict = listOf(
            "tracking number", "order number", "order #", "your order",
            "has shipped", "on the way", "out for delivery", "consignment",
            "m&p tracking", "leopards courier", "postex", "shipped"
        )
        return strict.any { it in lower || it in subjectLower }
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
        val sub = subject.lowercase()
        val s = (subject + " " + lowerBody).lowercase()

        // Subject wins for clear shipping lifecycle (avoids body noise like "will be delivered").
        when {
            listOf("cancelled", "canceled").any { it in sub } -> return OrderStatus.CANCELLED
            listOf("out for delivery", "arriving today").any { it in sub } ->
                return OrderStatus.OUT_FOR_DELIVERY
            listOf("was delivered", "has been delivered", "delivered successfully", "package delivered")
                .any { it in sub } -> return OrderStatus.DELIVERED
            listOf("on the way", "on its way", "in transit", "shipment from").any { it in sub } ->
                return OrderStatus.IN_TRANSIT
            listOf("has shipped", "order shipped", "dispatched").any { it in sub } ->
                return OrderStatus.SHIPPED
            listOf("order confirmed", "thanks for your order", "order placed").any { it in sub } ->
                return OrderStatus.PROCESSING
        }

        return when {
            listOf("cancelled", "canceled", "order cancel").any { it in s } -> OrderStatus.CANCELLED
            listOf("returned", "return received").any { it in s } && "refund" !in sub ->
                OrderStatus.RETURNED
            listOf("out for delivery", "arriving today").any { it in s } -> OrderStatus.OUT_FOR_DELIVERY
            listOf("delayed", "delivery attempt failed").any { it in s } -> OrderStatus.DELAYED
            // Only past-tense / confirmed delivery — never "will be delivered soon"
            listOf(
                "was delivered", "has been delivered", "delivered successfully",
                "package delivered", "order delivered", "your package was delivered",
                "delivered to you", "successfully delivered"
            ).any { it in s } &&
                !Regex("""\b(will be|to be|soon be)\s+delivered\b""").containsMatchIn(s) ->
                OrderStatus.DELIVERED
            listOf(
                "in transit", "on the way", "on its way", "arrived at", "reached",
                "departed", "scanned at", "picked up by the shipper"
            ).any { it in s } -> OrderStatus.IN_TRANSIT
            listOf("shipped", "dispatched", "has shipped", "picked up").any { it in s } ->
                OrderStatus.SHIPPED
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
            regex.findAll(text).forEach { match ->
                val cleaned = PakCourier.normalizeCn(cleanToken(match.groupValues[1]))
                if (cleaned.equals(orderNumber, ignoreCase = true)) return@forEach
                if (cleaned.length < 8) return@forEach
                // Skip Pakistani mobile numbers mistaken for CNs
                if (cleaned.matches(Regex("""03\d{9}"""))) return@forEach
                if (cleaned.matches(Regex("""92\d{10}"""))) return@forEach
                return cleaned
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
