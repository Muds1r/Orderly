package com.orderly.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Keys for orders the user permanently removed.
 * Sync skips matching emails so they never reappear.
 *
 * Formats:
 * - `id|<orderId>`
 * - `store|<store>|<orderNumber>`
 * - `cn|<trackingNumber>`
 */
@Entity(tableName = "excluded_orders")
data class ExcludedOrderEntity(
    @PrimaryKey val exclusionKey: String,
    val excludedAt: Long,
    val label: String? = null
)

object ExclusionKeys {
    fun from(order: OrderEntity): List<String> {
        val keys = mutableListOf("id|${order.id.lowercase()}")
        val store = order.store.trim().lowercase()
        val number = order.orderNumber?.trim()?.lowercase()
        if (!number.isNullOrBlank()) {
            keys += "store|$store|$number"
        }
        val cn = order.trackingNumber?.trim()?.lowercase()
        if (!cn.isNullOrBlank()) {
            keys += "cn|$cn"
        }
        return keys.distinct()
    }

    fun forIncoming(order: OrderEntity): List<String> = from(order)
}
