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
        Index("hidden"),
        Index("watched"),
        Index(value = ["store", "orderNumber"], unique = true)
    ]
)
data class OrderEntity(
    @PrimaryKey val id: String,
    val store: String,
    val orderNumber: String?,
    val productSummary: String?,
    val trackingNumber: String?,
    val carrier: String?,
    val shipFrom: String? = null,
    val lastLocation: String? = null,
    val orderDate: Long,
    val amount: Double?,
    val currency: String?,
    val paymentStatus: String?,
    val status: OrderStatus,
    val estimatedDelivery: Long?,
    val subject: String,
    val lastMessageId: String,
    val updatedAt: Long,
    /** Soft delete — null means visible. */
    val deletedAt: Long? = null,
    /** User hid from main lists without deleting. */
    val hidden: Boolean = false,
    /** Prioritize for live tracking / notifications. */
    val watched: Boolean = false,
    /** Last successful courier live lookup (epoch ms). */
    val lastLiveCheckAt: Long? = null
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
