package com.orderly.app.data.parser

import com.orderly.app.data.db.OrderEntity
import com.orderly.app.data.db.OrderStatus
import com.orderly.app.data.db.TrackingEventDraft
import com.orderly.app.data.tracking.LocationNames
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

    private val productRegexes = listOf(
        // Shopify: "Intense Repair Serum - (5 IN 1) × 2"
        Regex(
            """(?m)^([A-Za-z0-9][^\n]{3,90}?)\s*[×xX]\s*\d+\b"""
        ),
        // Explicit labels with colon (not "Items in this shipment")
        Regex(
            """(?:^|\n)\s*(?:item|product|bought|ordered)\s*:\s*(.{3,80}?)(?=\s*\r?\n|\s{2,}|$)""",
            RegexOption.IGNORE_CASE
        )
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
        // Dispatch/marketing mail with only a phone number as "tracking" — skip.
        if (orderNumber == null && tracking != null && isPhoneLike(tracking)) return null

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

        // Stable ids so confirmation + ship + dispatch emails merge into one row.
        val id = when {
            !orderNumber.isNullOrBlank() -> "${store.lowercase()}|$orderNumber"
            !tracking.isNullOrBlank() -> "${store.lowercase()}|cn|$tracking"
            else -> messageId
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
            // Food / QSR / ride-hailing (not parcel tracking)
            "foodpanda", "pandamart", "careem", "bykea", "uber.com",
            "blinkco", "blinkco.io", "savourfoods", "cheetay", "talabat",
            "mcdonalds", "kfc.", "pizza hut", "domino",
            // Marketing
            "newsletter@", "marketing@", "promo@", "offers@", "deals@"
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
                .any { it in sub } && !isFutureDeliveryWording(sub) -> return OrderStatus.DELIVERED
            listOf("on the way", "on its way", "in transit", "shipment from").any { it in sub } ->
                return OrderStatus.IN_TRANSIT
            listOf("has shipped", "order shipped", "dispatched").any { it in sub } ->
                return OrderStatus.SHIPPED
            listOf("order confirmed", "thanks for your order", "order placed", "confirmed")
                .any { it in sub } -> return OrderStatus.PROCESSING
        }

        return when {
            listOf("cancelled", "canceled", "order cancel").any { it in s } -> OrderStatus.CANCELLED
            listOf("returned", "return received").any { it in s } && "refund" !in sub ->
                OrderStatus.RETURNED
            listOf("out for delivery", "arriving today").any { it in s } -> OrderStatus.OUT_FOR_DELIVERY
            listOf("delayed", "delivery attempt failed").any { it in s } -> OrderStatus.DELAYED
            // Past-tense delivery only — never "will be delivered to you soon"
            isConfirmedDelivered(s) -> OrderStatus.DELIVERED
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
                "thank you for your order", "thank you for your purchase", "order placed",
                "we're preparing", "now in process"
            ).any { it in s } -> OrderStatus.PROCESSING
            else -> OrderStatus.UNKNOWN
        }
    }

    private fun isFutureDeliveryWording(text: String): Boolean =
        Regex(
            """\b(will be|to be|soon be|expected to be)\s+delivered\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text) ||
            Regex("""\bdelivered to you soon\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /** True only for confirmed past delivery — not marketing / ETA wording. */
    private fun isConfirmedDelivered(text: String): Boolean {
        if (isFutureDeliveryWording(text)) return false
        return listOf(
            "was delivered",
            "has been delivered",
            "delivered successfully",
            "package delivered",
            "order delivered",
            "your package was delivered",
            "successfully delivered"
        ).any { it in text }
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
                // Skip phone / WhatsApp numbers mistaken for courier CNs
                if (isPhoneLike(cleaned)) return@forEach
                return cleaned
            }
        }
        return null
    }

    /** Pakistani mobiles, landlines, and WhatsApp hotlines (e.g. 02137130284). */
    fun isPhoneLike(value: String): Boolean {
        val n = value.filter { it.isDigit() }
        return n.matches(Regex("""03\d{9}""")) ||
            n.matches(Regex("""92\d{10}""")) ||
            n.matches(Regex("""0\d{9,11}""")) // 021… landlines / hotlines
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
                val location = LocationNames.sanitize(
                    match.groupValues[1].trim().replace(Regex("""[.,;:]+$"""), "").take(60)
                ) ?: return@forEach
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
        // Shopify often puts the label on one line and "Rs. 2,699" on the next.
        val money = """(?:Rs\.?|PKR\.?|₨|USD|US\$|\$|€|£)?\s*([\d,]+(?:\.\d{1,2})?)"""
        val gap = """\s*:?\s*(?:\r?\n\s*)*"""

        // 1) Order total (not "Total paid today" / not "You saved")
        val totalPatterns = listOf(
            Regex("""(?:grand\s*total|order\s*total|total\s*amount)$gap$money""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\btotal\b(?!\s*paid)$gap$money""", setOf(RegexOption.IGNORE_CASE))
        )
        for (regex in totalPatterns) {
            regex.findAll(text).forEach { match ->
                val amountStart = match.groups[1]?.range?.first ?: return@forEach
                if (isNonPurchaseAmountContext(text, amountStart)) return@forEach
                val value = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@forEach
                if (value > 0) return value
            }
        }

        // 2) Subtotal (still better than unit promo price)
        Regex("""\bsubtotal\b$gap$money""", setOf(RegexOption.IGNORE_CASE)).findAll(text).forEach { match ->
            val amountStart = match.groups[1]?.range?.first ?: return@forEach
            if (isNonPurchaseAmountContext(text, amountStart)) return@forEach
            val value = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@forEach
            if (value > 0) return value
        }

        // 3) Shopify "Buy 1 - 1799" only when this mail has no total (ship follow-up)
        Regex(
            """Buy\s*1\s*[-–:]\s*(?:Rs\.?|PKR\.?|₨)?\s*([\d,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.let { return it }

        // 4) Generic currency — skip discounts / thresholds; do not prefer strike-through list prices
        val candidates = mutableListOf<Double>()
        listOf(
            Regex("""(?:Rs\.?|PKR\.?|₨)\s*:?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:USD|US\$|\$)\s*([\d,]+(?:\.\d{1,2})?)""")
        ).forEach { regex ->
            regex.findAll(text).forEach { match ->
                val amountStart = match.groups[1]?.range?.first ?: match.range.first
                if (isNonPurchaseAmountContext(text, amountStart)) return@forEach
                val value = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@forEach
                if (value > 0) candidates += value
            }
        }
        return candidates.maxOrNull()
    }

    /**
     * Skip discount / promo / threshold / COD-zero figures so we store the real order total.
     */
    private fun isNonPurchaseAmountContext(text: String, amountStart: Int): Boolean {
        val beforeStart = (amountStart - 56).coerceAtLeast(0)
        val before = text.substring(beforeStart, amountStart)
        val beforeLower = before.lowercase()

        // Keep labeled order totals / subtotals (even if "above Rs…" appears earlier)
        if (Regex(
                """(?:grand\s*total|order\s*total|total\s*amount|\bsubtotal\b|\btotal\b(?!\s*paid))\s*:?\s*(?:\r?\n\s*)*(?:rs\.?|pkr\.?|₨|\$|€|£)?\s*$""",
                setOf(RegexOption.IGNORE_CASE)
            ).containsMatchIn(before)
        ) {
            // Still reject "total paid today" / savings lines
            if ("paid" in beforeLower.takeLast(24) && "today" in beforeLower.takeLast(24)) return true
            if ("saved" in beforeLower.takeLast(24) || "you saved" in beforeLower) return true
            return false
        }

        // Keep Shopify unit price line when used as fallback
        if (Regex(
                """buy\s*1\s*[-–:]\s*(?:rs\.?|pkr\.?|₨)?\s*$""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(before)
        ) {
            return false
        }

        // Negative / parenthetical discount: (-Rs. 899) or -Rs.899
        if (Regex("""\(\s*-\s*(?:rs\.?|pkr\.?|₨|\$)?\s*$""", RegexOption.IGNORE_CASE)
                .containsMatchIn(before)
        ) {
            return true
        }
        if (Regex("""[-−–]\s*(?:rs\.?|pkr\.?|₨|\$)\s*$""", RegexOption.IGNORE_CASE)
                .containsMatchIn(before)
        ) {
            return true
        }

        val skipSignals = listOf(
            "% off", " percent off", "discount", "save ", "saved", "saving", "coupon", "promo",
            "buy 2", "buy 3", "buy 4", "above", "over rs", "over pkr",
            "free shipping", "minimum", "starting at", "as low as", "was rs",
            "compare at", "strike", "paid today", "total paid"
        )
        return skipSignals.any { it in beforeLower }
    }

    private fun extractProduct(text: String, subject: String): String? {
        for (regex in productRegexes) {
            regex.findAll(text).forEach { match ->
                val candidate = match.groupValues[1].trim().replace(Regex("""\s+"""), " ")
                if (isPlausibleProduct(candidate)) return candidate.take(80)
            }
        }
        // Gmail shopping card style first line before qty
        Regex(
            """(?i)(?:Items in this shipment|Items)\s*\n+([A-Za-z0-9][^\n]{3,80})"""
        ).find(text)?.groupValues?.get(1)?.trim()?.let {
            val cleaned = it.replace(Regex("""\s+"""), " ")
            if (isPlausibleProduct(cleaned)) return cleaned.take(80)
        }
        val cleaned = subject
            .replace(
                Regex(
                    """^(Your|New|Update:|A shipment from|Order\s+#?\d+\s*)\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(Regex("""\bis on the way\b.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.takeIf { isPlausibleProduct(it) }?.take(80)
    }

    private fun isPlausibleProduct(value: String): Boolean {
        val s = value.trim()
        if (s.length !in 3..80 || '@' in s) return false
        val lower = s.lowercase()
        if ("in this shipment" in lower) return false
        if (lower.startsWith("s in this")) return false
        if (lower in listOf("shipment", "items", "order", "product", "item")) return false
        if (lower.startsWith("order #") && s.length < 20) return false
        return true
    }

    fun isJunkProductSummary(value: String): Boolean = !isPlausibleProduct(value)

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
