package com.orderly.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One checkpoint in a package timeline — e.g. "Arrived at Lahore hub".
 * Sourced from store/courier emails or live courier lookups (PostEx, Leopards, …).
 */
@Entity(
    tableName = "tracking_events",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("orderId"),
        Index(value = ["orderId", "fingerprint"], unique = true)
    ]
)
data class TrackingEventEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    /** When the courier scanned / emailed this update. */
    val occurredAt: Long,
    val status: OrderStatus?,
    /** City / hub / facility when known. */
    val location: String?,
    val description: String,
    /** "email" | "live" */
    val source: String,
    /** Dedupes the same checkpoint across syncs. */
    val fingerprint: String,
    val messageId: String? = null
)

/** Draft event before it is tied to an order id. */
data class TrackingEventDraft(
    val occurredAt: Long,
    val status: OrderStatus?,
    val location: String?,
    val description: String,
    val source: String,
    val fingerprint: String,
    val messageId: String? = null
)
