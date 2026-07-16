package com.orderly.app.ui

import com.orderly.app.data.db.OrderStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val moneyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "PK"))

fun formatAmount(amount: Double?, currency: String?): String {
    if (amount == null) return "—"
    return when (currency?.uppercase()) {
        "USD" -> NumberFormat.getCurrencyInstance(Locale.US).format(amount)
        "EUR" -> NumberFormat.getCurrencyInstance(Locale.GERMANY).format(amount)
        "GBP" -> NumberFormat.getCurrencyInstance(Locale.UK).format(amount)
        else -> moneyFormat.format(amount)
    }
}

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))

fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(timestamp))

/** Relative label: "Just now", "2 mins ago", "Yesterday", "3 days ago", or a short date. */
fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - timestamp
    if (diff < 0) return formatDate(timestamp)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min${if (minutes == 1L) "" else "s"} ago"
        hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> formatDate(timestamp)
    }
}

/**
 * Human ETA copy when [estimatedDelivery] is known.
 * e.g. "Arriving Tomorrow", "Arriving today", "Arrived", "Arriving in 3 days".
 */
fun formatEta(estimatedDelivery: Long?, now: Long = System.currentTimeMillis()): String? {
    if (estimatedDelivery == null || estimatedDelivery <= 0L) return null
    val startOfToday = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfEta = Calendar.getInstance().apply {
        timeInMillis = estimatedDelivery
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayDiff = TimeUnit.MILLISECONDS.toDays(startOfEta - startOfToday)
    return when {
        dayDiff < 0 -> "Delivered by estimate"
        dayDiff == 0L -> "Arriving today"
        dayDiff == 1L -> "Arriving Tomorrow"
        dayDiff <= 14L -> "Arriving in $dayDiff days"
        else -> "ETA ${formatDate(estimatedDelivery)}"
    }
}

/** Footer line for list cards: ETA preferred, else last location, else order date. */
fun orderFooterLine(
    estimatedDelivery: Long?,
    lastLocation: String?,
    orderDate: Long,
    status: OrderStatus
): String {
    if (status == OrderStatus.DELIVERED) {
        return "Delivered · ${formatRelativeTime(orderDate)}"
    }
    formatEta(estimatedDelivery)?.let { return it }
    if (!lastLocation.isNullOrBlank()) return "Last scan: $lastLocation"
    return formatRelativeTime(orderDate)
}

fun formatPeriodChange(percent: Double?): String? {
    if (percent == null) return null
    if (percent.isNaN() || percent.isInfinite()) return null
    val rounded = kotlin.math.round(percent * 10.0) / 10.0
    val sign = if (rounded > 0) "+" else ""
    return "$sign$rounded% from last period"
}

fun statusLabel(status: OrderStatus): String = when (status) {
    OrderStatus.PROCESSING -> "Processing"
    OrderStatus.AWAITING_PAYMENT -> "Awaiting payment"
    OrderStatus.PAID -> "Paid"
    OrderStatus.SHIPPED -> "Shipped"
    OrderStatus.IN_TRANSIT -> "In transit"
    OrderStatus.OUT_FOR_DELIVERY -> "Out for delivery"
    OrderStatus.DELIVERED -> "Delivered"
    OrderStatus.DELAYED -> "Delayed"
    OrderStatus.CANCELLED -> "Cancelled"
    OrderStatus.RETURNED -> "Returned"
    OrderStatus.UNKNOWN -> "Unknown"
}

fun monthLabel(monthKey: String): String {
    // yyyy-MM → "Jan"
    return try {
        val parts = monthKey.split("-")
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, parts[0].toInt())
            set(Calendar.MONTH, parts[1].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
    } catch (_: Exception) {
        monthKey
    }
}

fun percentChange(current: Double, previous: Double): Double? {
    if (previous <= 0.0 && current <= 0.0) return null
    if (previous <= 0.0) return 100.0
    return ((current - previous) / previous) * 100.0
}
