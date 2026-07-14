package com.orderly.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class OrderStatus {
    PROCESSING,
    AWAITING_PAYMENT,
    PAID,
    SHIPPED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELAYED,
    CANCELLED,
    RETURNED,
    UNKNOWN
}

@Entity(
    tableName = "orders",
    indices = [
        Index("store"),
        Index("orderDate"),
        Index("status"),
        Index("trackingNumber"),
        Index(value = ["store", "orderNumber"], unique = true)
    ]
)
data class OrderEntity(
    /**
     * Stable id: "store|orderNumber" when order number is known,
     * otherwise the email Message-ID.
     */
    @PrimaryKey val id: String,
    val store: String,
    val orderNumber: String?,
    /** Short product blurb from the email (often incomplete). */
    val productSummary: String?,
    val trackingNumber: String?,
    val carrier: String?,
    /** Ship-from / origin city or facility when known. */
    val shipFrom: String? = null,
    /** Latest known hub / city from tracking. */
    val lastLocation: String? = null,
    /** Epoch millis of the order / email date. */
    val orderDate: Long,
    val amount: Double?,
    val currency: String?,
    val paymentStatus: String?,
    val status: OrderStatus,
    val estimatedDelivery: Long?,
    /** Latest related email subject (for detail view). */
    val subject: String,
    /** Last email Message-ID that updated this row. */
    val lastMessageId: String,
    val updatedAt: Long
)

@Entity(tableName = "processed_messages")
data class ProcessedMessageEntity(
    @PrimaryKey val messageId: String,
    val orderId: String,
    val processedAt: Long
)

data class StoreSummary(
    val store: String,
    val totalSpent: Double,
    val orderCount: Int
)

data class StatusCount(
    val status: OrderStatus,
    val count: Int
)
