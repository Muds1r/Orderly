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
                PakCourier.TCS -> trackTcsBestEffort(cn)
                PakCourier.PAKISTAN_POST -> null // form + captcha; email timeline only
                else -> {
                    // Try PostEx then Leopards when shape is ambiguous.
                    trackPostEx(cn) ?: trackLeopards(cn)
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
                fingerprint = "postex|$cn|$msgCode|$message|$whenMs".hashFingerprint()
            )
        }
        if (events.isEmpty()) return null
        val latest = events.maxByOrNull { it.occurredAt }
        return LiveTrackingResult(
            carrier = PakCourier.POSTEX,
            events = events.sortedByDescending { it.occurredAt },
            origin = dist.optString("merchantAddress1").ifBlank { null }?.take(80),
            destination = null,
            currentStatus = latest?.status,
            lastLocation = latest?.location
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
            val location = Regex(
                """\b(Karachi|Lahore|Islamabad|Rawalpindi|Multan|Faisalabad|Peshawar|Quetta|Hyderabad|Gujrat|Sialkot|Gujranwala|[A-Z][A-Za-z]+(?:\s+[A-Z][A-Za-z]+)?)\b"""
            ).find(plain)?.value
            val status = statusFromText(plain)
            val whenMs = parseFlexibleDate(plain) ?: System.currentTimeMillis()
            events += TrackingEventDraft(
                occurredAt = whenMs,
                status = status,
                location = location,
                description = plain.take(180),
                source = "live",
                fingerprint = "lcs|$cn|${plain.take(80)}|$whenMs".hashFingerprint()
            )
        }

        // Fallback: current status line
        if (events.isEmpty()) {
            val current = Regex(
                """Current Status/Reason\s*:?\s*</[^>]+>\s*<[^>]+>\s*([^<]+)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)?.groupValues?.get(1)?.trim()
                ?: Regex("""Arrived|Delivered|In Transit|Out for Delivery""", RegexOption.IGNORE_CASE)
                    .find(html)?.value
            if (!current.isNullOrBlank()) {
                events += TrackingEventDraft(
                    occurredAt = System.currentTimeMillis(),
                    status = statusFromText(current),
                    location = destination ?: origin,
                    description = current,
                    source = "live",
                    fingerprint = "lcs|$cn|current|$current".hashFingerprint()
                )
            }
        }

        if (events.isEmpty() && origin == null && destination == null) return null
        val latest = events.maxByOrNull { it.occurredAt }
        return LiveTrackingResult(
            carrier = PakCourier.LEOPARDS,
            events = events.sortedByDescending { it.occurredAt },
            origin = origin,
            destination = destination,
            currentStatus = latest?.status,
            lastLocation = latest?.location ?: destination
        )
    }

    /**
     * TCS consumer site no longer exposes a stable public JSON endpoint without auth.
     * We still return a stub so the UI can open the carrier site; email sync fills events.
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
                val station = item.optString("station").ifBlank { null }
                val whenMs = parseFlexibleDate(item.optString("datetime"))
                    ?: System.currentTimeMillis()
                events += TrackingEventDraft(
                    occurredAt = whenMs,
                    status = statusFromText(status),
                    location = station,
                    description = if (station != null) "$status — $station" else status,
                    source = "live",
                    fingerprint = "tcs|$cn|$status|$station|$whenMs".hashFingerprint()
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
                val loc = item.optString("recievedby").ifBlank { null }
                events += TrackingEventDraft(
                    occurredAt = whenMs,
                    status = statusFromText(status),
                    location = loc,
                    description = status,
                    source = "live",
                    fingerprint = "tcs|$cn|cp|$status|$whenMs".hashFingerprint()
                )
            }
        }

        if (events.isEmpty()) return null
        val shipment = root.optJSONArray("shipmentinfo")?.optJSONObject(0)
        return LiveTrackingResult(
            carrier = PakCourier.TCS,
            events = events.sortedByDescending { it.occurredAt },
            origin = shipment?.optString("origin")?.ifBlank { null },
            destination = shipment?.optString("destination")?.ifBlank { null },
            currentStatus = events.maxByOrNull { it.occurredAt }?.status,
            lastLocation = events.maxByOrNull { it.occurredAt }?.location
        )
    }

    private fun statusFromText(text: String): OrderStatus {
        val s = text.lowercase()
        return when {
            "delivered" in s -> OrderStatus.DELIVERED
            "out for delivery" in s || "out-for-delivery" in s -> OrderStatus.OUT_FOR_DELIVERY
            "delay" in s || "attempt" in s || "exception" in s -> OrderStatus.DELAYED
            "return" in s || "cancel" in s -> OrderStatus.RETURNED
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
        // epoch millis / seconds
        cleaned.toLongOrNull()?.let { n ->
            return if (n < 10_000_000_000L) n * 1000 else n
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "EEEE MMM dd, yyyy HH:mm",
            "EEE MMM dd, yyyy HH:mm",
            "MMM dd, yyyy HH:mm",
            "dd-MM-yyyy HH:mm",
            "dd/MM/yyyy HH:mm",
            "d MMM yyyy, h:mm a",
            "d MMM yyyy h:mm a"
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US).apply {
                    isLenient = true
                    timeZone = TimeZone.getDefault()
                }
                fmt.parse(cleaned)?.time?.let { return it }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun String.encode(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.hashFingerprint(): String =
        Integer.toHexString(hashCode())
}
