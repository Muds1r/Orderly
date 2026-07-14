package com.orderly.app.ui

import com.orderly.app.data.db.OrderStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
