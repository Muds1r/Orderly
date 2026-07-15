package com.orderly.app.data.tracking

import com.orderly.app.data.db.OrderStatus
import com.orderly.app.data.db.TrackingEventDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

data class LiveTrackingResult(
    val carrier: PakCourier,
    val events: List<TrackingEventDraft>,
    val origin: String? = null,
    val destination: String? = null,
    val currentStatus: OrderStatus? = null,
    val lastLocation: String? = null
)

/**
 * Live package lookups for Pakistani couriers used by Daraz / local sellers.
 * No merchant API keys — uses public consumer tracking pages.
 */
object LiveTrackingClient {

    suspend fun track(trackingNumber: String, hint: PakCourier? = null): LiveTrackingResult? =
        withContext(Dispatchers.IO) {
            val cn = PakCourier.normalizeCn(trackingNumber)
            if (cn.length < 6) return@withContext null
            val courier = hint?.takeIf { it != PakCourier.UNKNOWN }
                ?: PakCourier.detect(cn)

            when (courier) {
                PakCourier.POSTEX -> trackPostEx(cn)
                PakCourier.LEOPARDS -> trackLeopards(cn)
                PakCourier.MNP -> trackMnP(cn)
                PakCourier.TCS -> trackTcsBestEffort(cn)
                PakCourier.PAKISTAN_POST -> null
                else -> {
                    // Ambiguous numeric CNs: try M&P then PostEx then Leopards.
                    trackMnP(cn) ?: trackPostEx(cn) ?: trackLeopards(cn)
                }
            }
        }

    private fun trackPostEx(cn: String): LiveTrackingResult? {
        val conn = (URL("https://postex.pk/api/tracking-order").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Orderly/0.2")
        }
        return try {
            OutputStreamWriter(conn.outputStream).use { it.write("""{"trackingNumber":"$cn"}""") }
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            parsePostEx(body, cn)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parsePostEx(body: String, cn: String): LiveTrackingResult? {
        val root = JSONObject(body)
        val code = root.optString("statusCode")
        if (!code.startsWith("20")) return null
        val dist = root.optJSONObject("dist") ?: return null
        val history = dist.optJSONArray("transactionStatusHistory") ?: return null
        val events = mutableListOf<TrackingEventDraft>()
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val message = item.optString("transactionStatusMessage")
            if (message.isBlank()) continue
            val msgCode = item.optString("transactionStatusMessageCode")
            val whenMs = parseFlexibleDate(item.optString("modifiedDatetime"))
                ?: System.currentTimeMillis()
            val status = mapPostExStatus(msgCode, message)
            val location = item.optString("cityName").ifBlank { null }
                ?: item.optString("location").ifBlank { null }
            events += TrackingEventDraft(
                occurredAt = whenMs,
                status = status,
                location = location,
                description = message,
                source = "live",
                fingerprint = "postex|$cn|$msgCode|$message".hashFingerprint()
            )
        }
        if (events.isEmpty()) return null
        val latest = events.maxByOrNull { it.occurredAt }
        return LiveTrackingResult(
            carrier = PakCourier.POSTEX,
            events = events.sortedByDescending { it.occurredAt },
            origin = LocationNames.sanitize(
                dist.optString("merchantAddress1").ifBlank { null }?.take(80)
            ),
            destination = null,
            currentStatus = latest?.status,
            lastLocation = LocationNames.sanitize(latest?.location)
        )
    }

    private fun mapPostExStatus(code: String, message: String): OrderStatus {
        val m = message.lowercase()
        return when {
            code in listOf("0033", "0037") || "delivered" in m -> OrderStatus.DELIVERED
            code == "0004" || "out for delivery" in m -> OrderStatus.OUT_FOR_DELIVERY
            code in listOf("0005", "0031") || "in transit" in m || "picked" in m -> OrderStatus.IN_TRANSIT
            "cancel" in m || "return" in m -> OrderStatus.RETURNED
            else -> OrderStatus.SHIPPED
        }
    }

    private fun trackLeopards(cn: String): LiveTrackingResult? {
        val cookieManager = mutableListOf<HttpCookie>()
        // Seed session + lookup
        val seed = httpGet(
            "https://pk.leopardscourier.com/shipment_tracking-new?cn_number=${cn.encode()}",
            cookieManager,
            acceptJson = true
        ) ?: return null
        if (!seed.contains("\"success\"", ignoreCase = true)) return null

        val html = httpGet(
            "https://pk.leopardscourier.com/shipment_tracking_view?cn_number=${cn.encode()}",
            cookieManager,
            acceptJson = false
        ) ?: return null

        return parseLeopardsHtml(html, cn)
    }

    private fun parseLeopardsHtml(html: String, cn: String): LiveTrackingResult? {
        val origin = Regex(
            """Origin\s*:.*?</td>\s*<td[^>]*>\s*(?:<[^>]+>)*([^<]+)<""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && it != "-" }

        val destination = Regex(
            """Destination\s*:.*?</td>\s*<td[^>]*>.*?<span[^>]*>\s*([^<]+)\s*</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.get(1)?.trim()
            ?: Regex(
                """Destination\s*:.*?</td>\s*<td[^>]*>\s*([^<]+)<""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)?.groupValues?.get(1)?.trim()

        val events = mutableListOf<TrackingEventDraft>()
        val itemPattern = Pattern.compile(
            """<div class="tracking-item">(.*?)</div>\s*</div>""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
        val matcher = itemPattern.matcher(html)
        while (matcher.find()) {
            val block = matcher.group(1) ?: continue
            val plain = block.replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (plain.isBlank() || plain.contains("not available", ignoreCase = true)) continue
            val location = LocationNames.fromTrackingText(plain)
            val status = statusFromText(plain)
            val whenMs = parseFlexibleDate(plain) ?: 0L
            val desc = cleanLiveDescription(plain, location)
            // Prefer real checkpoint time; never stamp all events with "now" (that breaks sort).
            val occurredAt = when {
                whenMs > 0L -> whenMs
                else -> 0L
            }
            if (occurredAt == 0L && desc.isBlank()) continue
            events += TrackingEventDraft(
                occurredAt = occurredAt,
                status = status,
                location = location,
                description = desc.ifBlank { plain.take(120) },
                source = "live",
                fingerprint = "lcs|$cn|${status.name}|${location.orEmpty()}|${desc.take(80).ifBlank { plain.take(80) }}".hashFingerprint()
            )
        }

        // If some dates failed to parse, order by scraped sequence then known times.
        // Unparsed (0) go before parsed of unknown… better: assign relative times from HTML order.
        val withTimes = assignMissingTimesPreservingOrder(events)

        // Fallback: current status line
        if (withTimes.isEmpty()) {
            val current = Regex(
                """Current Status/Reason\s*:?\s*</[^>]+>\s*<[^>]+>\s*([^<]+)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)?.groupValues?.get(1)?.trim()
                ?: Regex("""Arrived|Delivered|In Transit|Out for Delivery""", RegexOption.IGNORE_CASE)
                    .find(html)?.value
            if (!current.isNullOrBlank()) {
                return LiveTrackingResult(
                    carrier = PakCourier.LEOPARDS,
                    events = listOf(
                        TrackingEventDraft(
                            occurredAt = System.currentTimeMillis(),
                            status = statusFromText(current),
                            location = LocationNames.sanitize(destination) ?: LocationNames.sanitize(origin),
                            description = cleanLiveDescription(current, LocationNames.sanitize(destination) ?: LocationNames.sanitize(origin)),
                            source = "live",
                            fingerprint = "lcs|$cn|current|$current".hashFingerprint()
                        )
                    ),
                    origin = LocationNames.sanitize(origin),
                    destination = LocationNames.sanitize(destination),
                    currentStatus = statusFromText(current),
                    lastLocation = LocationNames.sanitize(destination) ?: LocationNames.sanitize(origin)
                )
            }
        }

        if (withTimes.isEmpty() && origin == null && destination == null) return null
        val latest = withTimes.maxByOrNull { it.occurredAt }
        return LiveTrackingResult(
            carrier = PakCourier.LEOPARDS,
            events = withTimes.sortedBy { it.occurredAt },
            origin = LocationNames.sanitize(origin),
            destination = LocationNames.sanitize(destination),
            currentStatus = latest?.status,
            lastLocation = latest?.location
                ?: LocationNames.sanitize(destination)
                ?: LocationNames.sanitize(origin)
        )
    }

    /**
     * Public consumer page: https://www.mulphilog.com/tracking/{cn}
     * Timeline steps: date, status (Booked / In Transit / …), location, message.
     */
    private fun trackMnP(cn: String): LiveTrackingResult? {
        val html = httpGet(
            "https://www.mulphilog.com/tracking/$cn",
            mutableListOf(),
            acceptJson = false
        ) ?: return null
        if ("No tracking record found" in html) return null

        val statuses = Regex(
            """class="order-track-text-stat status"[^>]*>\s*([^<]+)""",
            RegexOption.IGNORE_CASE
        ).findAll(html).map { it.groupValues[1].trim() }.toList()
        val locations = Regex(
            """class="order-track-text-stat location"[^>]*>\s*([^<]+)""",
            RegexOption.IGNORE_CASE
        ).findAll(html).map { it.groupValues[1].trim() }.toList()
        val messages = Regex(
            """class="order-track-text-stat status-message"[^>]*>\s*([^<]+)""",
            RegexOption.IGNORE_CASE
        ).findAll(html).map { it.groupValues[1].trim() }.toList()
        val dates = Regex(
            """class="order-track-text-sub[^"]*"[^>]*>\s*(.*?)\s*</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).map {
            it.groupValues[1].replace(Regex("<[^>]+>"), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }.toList()

        if (statuses.isEmpty()) return null

        val events = mutableListOf<TrackingEventDraft>()
        for (i in statuses.indices) {
            val statusLabel = statuses[i]
            val location = LocationNames.sanitize(locations.getOrNull(i)?.ifBlank { null })
            val message = messages.getOrNull(i)?.ifBlank { null }
            val whenMs = parseFlexibleDate(dates.getOrNull(i)) ?: 0L
            val mapped = statusFromText(statusLabel + " " + (message ?: ""))
            val description = buildString {
                append(statusLabel)
                if (!message.isNullOrBlank() && !message.equals(statusLabel, ignoreCase = true)) {
                    append(" — ")
                    append(message.take(120))
                }
            }
            events += TrackingEventDraft(
                occurredAt = if (whenMs > 0) whenMs else System.currentTimeMillis(),
                status = mapped,
                location = location,
                description = description,
                source = "live",
                fingerprint = "mnp|$cn|$statusLabel|${location.orEmpty()}|${description.take(80)}".hashFingerprint()
            )
        }

        val latest = events.maxByOrNull { it.occurredAt }
        return LiveTrackingResult(
            carrier = PakCourier.MNP,
            events = events.sortedByDescending { it.occurredAt },
            origin = LocationNames.sanitize(locations.lastOrNull()),
            destination = null,
            currentStatus = latest?.status,
            lastLocation = latest?.location
        )
    }

    /**
     * TCS consumer site no longer exposes a stable public JSON endpoint without auth.
     */
    private fun trackTcsBestEffort(cn: String): LiveTrackingResult? {
        return try {
            val url =
                "https://ociconnect.tcscourier.com/tracking/api/Tracking/GetDynamicTrackDetail"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(conn.outputStream).use {
                it.write("""{"consignee":["$cn"]}""")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            parseTcsJson(body, cn)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTcsJson(body: String, cn: String): LiveTrackingResult? {
        val root = JSONObject(body)
        val checkpoints = root.optJSONArray("checkpoints")
        val events = mutableListOf<TrackingEventDraft>()

        val infoArr = root.optJSONArray("info")
        if (infoArr != null) {
            for (i in 0 until infoArr.length()) {
                val item = infoArr.optJSONObject(i) ?: continue
                val status = item.optString("status")
                if (status.isBlank()) continue
                val station = LocationNames.sanitize(item.optString("station").ifBlank { null })
                val whenMs = parseFlexibleDate(item.optString("datetime"))
                    ?: System.currentTimeMillis()
                events += TrackingEventDraft(
                    occurredAt = whenMs,
                    status = statusFromText(status),
                    location = station,
                    description = if (station != null) "$status — $station" else status,
                    source = "live",
                    fingerprint = "tcs|$cn|$status|${station.orEmpty()}".hashFingerprint()
                )
            }
        }

        if (checkpoints != null) {
            for (i in 0 until checkpoints.length()) {
                val item = checkpoints.optJSONObject(i) ?: continue
                val status = item.optString("status")
                if (status.isBlank()) continue
                val whenMs = parseFlexibleDate(item.optString("datetime"))
                    ?: System.currentTimeMillis()
                val loc = LocationNames.sanitize(item.optString("recievedby").ifBlank { null })
                events += TrackingEventDraft(
                    occurredAt = whenMs,
                    status = statusFromText(status),
                    location = loc,
                    description = status,
                    source = "live",
                    fingerprint = "tcs|$cn|cp|$status".hashFingerprint()
                )
            }
        }

        if (events.isEmpty()) return null
        val shipment = root.optJSONArray("shipmentinfo")?.optJSONObject(0)
        return LiveTrackingResult(
            carrier = PakCourier.TCS,
            events = events.sortedByDescending { it.occurredAt },
            origin = LocationNames.sanitize(shipment?.optString("origin")?.ifBlank { null }),
            destination = LocationNames.sanitize(shipment?.optString("destination")?.ifBlank { null }),
            currentStatus = events.maxByOrNull { it.occurredAt }?.status,
            lastLocation = events.maxByOrNull { it.occurredAt }?.location
        )
    }

    private fun statusFromText(text: String): OrderStatus {
        val s = text.lowercase()
        // Future / ETA wording must not count as delivered.
        if (Regex("""\b(will be|to be|soon be)\s+delivered\b""").containsMatchIn(s) ||
            "delivered to you soon" in s
        ) {
            return when {
                "out for delivery" in s -> OrderStatus.OUT_FOR_DELIVERY
                "transit" in s || "on the way" in s || "picked" in s -> OrderStatus.IN_TRANSIT
                "dispatched" in s || "shipped" in s -> OrderStatus.SHIPPED
                "booked" in s || "placed" in s -> OrderStatus.PROCESSING
                else -> OrderStatus.IN_TRANSIT
            }
        }
        return when {
            "delivered" in s || "received by consignee" in s || "received by customer" in s ->
                OrderStatus.DELIVERED
            "out for delivery" in s || "out-for-delivery" in s -> OrderStatus.OUT_FOR_DELIVERY
            "delay" in s || "attempt" in s || "exception" in s || "hold" in s -> OrderStatus.DELAYED
            "return" in s || "cancel" in s -> OrderStatus.RETURNED
            "booked" in s || "placed" in s || "prepared for shipment" in s -> OrderStatus.PROCESSING
            "transit" in s || "arrived" in s || "departed" in s || "facility" in s ||
                "on the way" in s -> OrderStatus.IN_TRANSIT
            "shipped" in s || "dispatched" in s || "picked" in s -> OrderStatus.SHIPPED
            else -> OrderStatus.IN_TRANSIT
        }
    }

    private fun httpGet(
        url: String,
        cookies: MutableList<HttpCookie>,
        acceptJson: Boolean
    ): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 Orderly/0.2")
            setRequestProperty("Accept", if (acceptJson) "application/json" else "text/html")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            if (cookies.isNotEmpty()) {
                setRequestProperty(
                    "Cookie",
                    cookies.joinToString("; ") { "${it.name}=${it.value}" }
                )
            }
        }
        return try {
            conn.headerFields["Set-Cookie"]?.forEach { raw ->
                HttpCookie.parse(raw).forEach { cookies.add(it) }
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parseFlexibleDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
        cleaned.toLongOrNull()?.let { n ->
            return if (n < 10_000_000_000L) n * 1000 else n
        }

        // Leopards embeds: "14 July, 2026 (20:32) Dispatched to ISLAMABAD"
        val embedded = Regex(
            """(\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\.?,?\s*\d{4}\s*\(?\s*\d{1,2}:\d{2}\s*(?:am|pm|a\.m\.|p\.m\.)?\s*\)?)""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)?.groupValues?.get(1)

        val candidates = buildList {
            embedded?.let { add(normalizeDateSnippet(it)) }
            add(normalizeDateSnippet(cleaned))
            // Also try first ~40 chars when the string is a long status line
            if (cleaned.length > 40) add(normalizeDateSnippet(cleaned.take(40)))
        }.distinct()

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "EEEE MMM dd, yyyy HH:mm",
            "EEE MMM dd, yyyy HH:mm",
            "MMM dd, yyyy HH:mm",
            "dd-MM-yyyy HH:mm",
            "dd/MM/yyyy HH:mm",
            "d MMMM yyyy HH:mm",
            "dd MMMM yyyy HH:mm",
            "d MMMM, yyyy HH:mm",
            "dd MMMM, yyyy HH:mm",
            "d MMM yyyy HH:mm",
            "dd MMM yyyy HH:mm",
            "d MMM yyyy, h:mm a",
            "d MMM yyyy h:mm a",
            "dd MMM yyyy h:mm a",
            "dd MMM yyyy, h:mm a",
            "d MMM yyyy, HH:mm",
            "d MMMM yyyy, HH:mm",
            "d MMM yyyy HH:mm:ss",
            "d MMMM yyyy h:mm a"
        )
        for (candidate in candidates) {
            for (p in patterns) {
                try {
                    val fmt = SimpleDateFormat(p, Locale.US).apply {
                        isLenient = true
                        timeZone = TimeZone.getDefault()
                    }
                    fmt.parse(candidate)?.time?.let { return it }
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    /** "14 July, 2026 (20:32)" → "14 July 2026 20:32" */
    private fun normalizeDateSnippet(raw: String): String =
        raw.trim()
            .replace(",", " ")
            .replace("(", " ")
            .replace(")", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Drop embedded date from courier status lines; prefer short status · city. */
    private fun cleanLiveDescription(plain: String, location: String? = null): String {
        val withoutDate = plain.replace(
            Regex(
                """\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\.?,?\s*\d{4}\s*\(?\s*\d{1,2}:\d{2}\s*(?:am|pm)?\s*\)?\s*""",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).replace(Regex("""\s+"""), " ").trim()

        val status = statusFromText(withoutDate)
        val short = when (status) {
            OrderStatus.DELIVERED -> "Delivered"
            OrderStatus.OUT_FOR_DELIVERY -> "Out for delivery"
            OrderStatus.IN_TRANSIT -> when {
                "picked" in withoutDate.lowercase() -> "Picked up"
                "dispatched" in withoutDate.lowercase() -> "Dispatched"
                "arrived" in withoutDate.lowercase() -> "Arrived"
                else -> "In transit"
            }
            OrderStatus.SHIPPED -> when {
                "picked" in withoutDate.lowercase() -> "Picked up"
                "dispatched" in withoutDate.lowercase() -> "Dispatched"
                else -> "Shipped"
            }
            OrderStatus.DELAYED -> "Delayed"
            OrderStatus.RETURNED -> "Returned"
            OrderStatus.PROCESSING -> "Booked"
            else -> withoutDate.take(80).ifBlank { "Update" }
        }
        val city = location?.takeIf { it.isNotBlank() }
            ?: LocationNames.fromTrackingText(withoutDate)
        return if (city != null && !short.contains(city, ignoreCase = true)) {
            "$short · $city"
        } else {
            short
        }
    }

    /**
     * HTML scrape order is usually newest-first. Fill missing dates so ASC sorting
     * still keeps relative order among siblings when only some timestamps parse.
     */
    private fun assignMissingTimesPreservingOrder(
        events: List<TrackingEventDraft>
    ): List<TrackingEventDraft> {
        if (events.isEmpty()) return events
        // Scrape order is typically newest → oldest for Leopards cards.
        val newestFirst = events
        val known = newestFirst.mapIndexedNotNull { i, e ->
            if (e.occurredAt > 0L) i to e.occurredAt else null
        }
        if (known.isEmpty()) {
            // No dates: space them 1 minute apart ending at now (oldest last in list = earlier)
            val now = System.currentTimeMillis()
            return newestFirst.mapIndexed { i, e ->
                e.copy(occurredAt = now - i * 60_000L)
            }
        }
        return newestFirst.mapIndexed { i, e ->
            if (e.occurredAt > 0L) e
            else {
                // Interpolate between neighbors or offset from nearest known
                val before = known.lastOrNull { it.first < i }?.second
                val after = known.firstOrNull { it.first > i }?.second
                val guessed = when {
                    before != null && after != null -> (before + after) / 2
                    before != null -> before - 60_000L
                    after != null -> after + 60_000L
                    else -> System.currentTimeMillis()
                }
                e.copy(occurredAt = guessed)
            }
        }
    }

    private fun String.encode(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.hashFingerprint(): String =
        Integer.toHexString(hashCode())

    /** Exposed for unit tests. */
    fun parseDateForTest(raw: String): Long? = parseFlexibleDate(raw)
}
