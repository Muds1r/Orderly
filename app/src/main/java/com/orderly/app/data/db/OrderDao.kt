package com.orderly.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcessed(message: ProcessedMessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventIgnore(event: TrackingEventEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM processed_messages WHERE messageId = :messageId)")
    suspend fun wasProcessed(messageId: String): Boolean

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): OrderEntity?

    @Query(
        """
        SELECT * FROM orders
        WHERE store = :store AND orderNumber = :orderNumber
        LIMIT 1
        """
    )
    suspend fun getByStoreAndOrderNumber(store: String, orderNumber: String): OrderEntity?

    @Query(
        """
        SELECT * FROM orders
        WHERE trackingNumber = :trackingNumber
        LIMIT 1
        """
    )
    suspend fun getByTrackingNumber(trackingNumber: String): OrderEntity?

    @Query(
        """
        UPDATE orders SET
            productSummary = COALESCE(:productSummary, productSummary),
            trackingNumber = COALESCE(:trackingNumber, trackingNumber),
            carrier = COALESCE(:carrier, carrier),
            shipFrom = COALESCE(:shipFrom, shipFrom),
            lastLocation = COALESCE(:lastLocation, lastLocation),
            amount = COALESCE(:amount, amount),
            currency = COALESCE(:currency, currency),
            paymentStatus = COALESCE(:paymentStatus, paymentStatus),
            status = :status,
            estimatedDelivery = COALESCE(:estimatedDelivery, estimatedDelivery),
            subject = :subject,
            lastMessageId = :lastMessageId,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateFields(
        id: String,
        productSummary: String?,
        trackingNumber: String?,
        carrier: String?,
        shipFrom: String?,
        lastLocation: String?,
        amount: Double?,
        currency: String?,
        paymentStatus: String?,
        status: OrderStatus,
        estimatedDelivery: Long?,
        subject: String,
        lastMessageId: String,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE orders SET
            carrier = COALESCE(:carrier, carrier),
            shipFrom = COALESCE(:shipFrom, shipFrom),
            lastLocation = COALESCE(:lastLocation, lastLocation),
            status = :status,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateTrackingSnapshot(
        id: String,
        carrier: String?,
        shipFrom: String?,
        lastLocation: String?,
        status: OrderStatus,
        updatedAt: Long
    )

    @Query("SELECT * FROM tracking_events WHERE orderId = :orderId")
    suspend fun eventsForOrder(orderId: String): List<TrackingEventEntity>

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrderById(id: String)

    @Query("DELETE FROM tracking_events WHERE orderId = :orderId")
    suspend fun deleteEventsForOrder(orderId: String)

    /**
     * Insert a new order or merge into an existing one (same store + order number,
     * or same tracking number so courier placeholders merge into Daraz/Temu orders).
     */
    @Transaction
    suspend fun upsertOrder(
        incoming: OrderEntity,
        messageId: String,
        events: List<TrackingEventDraft> = emptyList()
    ) {
        if (wasProcessed(messageId)) return

        var existing = when {
            !incoming.orderNumber.isNullOrBlank() ->
                getByStoreAndOrderNumber(incoming.store, incoming.orderNumber)
            else -> null
        }
        // Courier mail may have created tracking|<cn> before the store email arrived.
        if (existing == null && !incoming.trackingNumber.isNullOrBlank()) {
            existing = getByTrackingNumber(incoming.trackingNumber)
        }
        if (existing == null) {
            existing = getById(incoming.id)
        }

        val orderId: String
        if (existing == null) {
            insertIgnore(incoming)
            // If unique conflict ignored, resolve the real row.
            val resolved = when {
                !incoming.orderNumber.isNullOrBlank() ->
                    getByStoreAndOrderNumber(incoming.store, incoming.orderNumber)
                !incoming.trackingNumber.isNullOrBlank() ->
                    getByTrackingNumber(incoming.trackingNumber)
                else -> getById(incoming.id)
            } ?: getById(incoming.id)
            orderId = resolved?.id ?: incoming.id
        } else {
            orderId = mergeIntoExisting(existing, incoming)
        }

        appendEvents(orderId, events)

        insertProcessed(
            ProcessedMessageEntity(
                messageId = messageId,
                orderId = orderId,
                processedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Prefer the store order id (daraz|…) over a courier placeholder (tracking|…).
     * Moves timeline events onto the surviving row when ids differ.
     */
    private suspend fun mergeIntoExisting(existing: OrderEntity, incoming: OrderEntity): String {
        val preferIncomingId = existing.id.startsWith("tracking|") &&
            !incoming.id.startsWith("tracking|")
        val survivorId = if (preferIncomingId) incoming.id else existing.id
        val dropId = if (preferIncomingId) existing.id else null

        if (preferIncomingId) {
            // Promote placeholder → real store order: insert survivor, move events, delete old.
            val promoted = incoming.copy(
                trackingNumber = incoming.trackingNumber ?: existing.trackingNumber,
                carrier = incoming.carrier ?: existing.carrier,
                shipFrom = incoming.shipFrom ?: existing.shipFrom,
                lastLocation = incoming.lastLocation ?: existing.lastLocation,
                amount = incoming.amount ?: existing.amount,
                currency = incoming.currency ?: existing.currency,
                paymentStatus = incoming.paymentStatus ?: existing.paymentStatus,
                productSummary = incoming.productSummary ?: existing.productSummary,
                status = OrderStatusMerge.prefer(existing.status, incoming.status),
                estimatedDelivery = incoming.estimatedDelivery ?: existing.estimatedDelivery,
                orderDate = minOf(existing.orderDate, incoming.orderDate)
            )
            insertIgnore(promoted)
            // Re-copy events under new order id, then drop placeholder.
            val oldEvents = eventsForOrder(existing.id)
            oldEvents.forEach { ev ->
                insertEventIgnore(
                    ev.copy(
                        id = "$survivorId|${ev.fingerprint}",
                        orderId = survivorId
                    )
                )
            }
            deleteEventsForOrder(existing.id)
            deleteOrderById(existing.id)
        } else {
            val mergedStatus = OrderStatusMerge.prefer(existing.status, incoming.status)
            updateFields(
                id = existing.id,
                productSummary = incoming.productSummary,
                trackingNumber = incoming.trackingNumber,
                carrier = incoming.carrier,
                shipFrom = incoming.shipFrom,
                lastLocation = incoming.lastLocation,
                amount = incoming.amount,
                currency = incoming.currency,
                paymentStatus = incoming.paymentStatus,
                status = mergedStatus,
                estimatedDelivery = incoming.estimatedDelivery,
                subject = incoming.subject.ifBlank { existing.subject },
                lastMessageId = incoming.lastMessageId,
                updatedAt = incoming.updatedAt
            )
        }
        return survivorId
    }

    @Transaction
    suspend fun applyTrackingUpdate(
        orderId: String,
        carrier: String?,
        shipFrom: String?,
        lastLocation: String?,
        status: OrderStatus?,
        events: List<TrackingEventDraft>
    ) {
        val existing = getById(orderId) ?: return
        val mergedStatus = if (status != null) {
            OrderStatusMerge.prefer(existing.status, status)
        } else {
            existing.status
        }
        updateTrackingSnapshot(
            id = orderId,
            carrier = carrier,
            shipFrom = shipFrom,
            lastLocation = lastLocation,
            status = mergedStatus,
            updatedAt = System.currentTimeMillis()
        )
        appendEvents(orderId, events)
    }

    suspend fun appendEvents(orderId: String, events: List<TrackingEventDraft>) {
        events.forEach { draft ->
            insertEventIgnore(
                TrackingEventEntity(
                    id = "$orderId|${draft.fingerprint}",
                    orderId = orderId,
                    occurredAt = draft.occurredAt,
                    status = draft.status,
                    location = draft.location,
                    description = draft.description,
                    source = draft.source,
                    fingerprint = draft.fingerprint,
                    messageId = draft.messageId
                )
            )
        }
    }

    @Transaction
    suspend fun upsertOrders(orders: List<Triple<OrderEntity, String, List<TrackingEventDraft>>>) {
        orders.forEach { (order, messageId, events) ->
            upsertOrder(order, messageId, events)
        }
    }

    @Query("SELECT * FROM orders ORDER BY orderDate DESC")
    fun allOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    fun observeOrder(id: String): Flow<OrderEntity?>

    @Query(
        """
        SELECT * FROM tracking_events
        WHERE orderId = :orderId
        ORDER BY occurredAt DESC
        """
    )
    fun observeEvents(orderId: String): Flow<List<TrackingEventEntity>>

    @Query(
        """
        SELECT * FROM orders
        WHERE status IN ('SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELAYED')
          AND trackingNumber IS NOT NULL
          AND trackingNumber != ''
        ORDER BY updatedAt ASC
        LIMIT :limit
        """
    )
    suspend fun ordersNeedingLiveTrack(limit: Int = 40): List<OrderEntity>

    @Query(
        """
        SELECT * FROM orders
        WHERE status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED')
        ORDER BY orderDate DESC
        """
    )
    fun activeOrders(): Flow<List<OrderEntity>>

    @Query(
        """
        SELECT * FROM orders
        WHERE status IN ('SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELAYED')
        ORDER BY orderDate DESC
        """
    )
    fun inTransitOrders(): Flow<List<OrderEntity>>

    @Query(
        """
        SELECT * FROM orders
        WHERE status = 'DELAYED'
        ORDER BY orderDate DESC
        """
    )
    fun delayedOrders(): Flow<List<OrderEntity>>

    @Query(
        """
        SELECT * FROM orders
        WHERE status = 'DELIVERED'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    fun recentlyDelivered(limit: Int = 10): Flow<List<OrderEntity>>

    @Query(
        """
        SELECT * FROM orders
        WHERE (:query = '' OR
               store LIKE '%' || :query || '%' OR
               IFNULL(orderNumber, '') LIKE '%' || :query || '%' OR
               IFNULL(productSummary, '') LIKE '%' || :query || '%' OR
               IFNULL(trackingNumber, '') LIKE '%' || :query || '%' OR
               IFNULL(carrier, '') LIKE '%' || :query || '%' OR
               IFNULL(lastLocation, '') LIKE '%' || :query || '%')
        ORDER BY orderDate DESC
        """
    )
    fun searchOrders(query: String): Flow<List<OrderEntity>>

    @Query(
        """
        SELECT store,
               SUM(IFNULL(amount, 0)) AS totalSpent,
               COUNT(*) AS orderCount
        FROM orders
        WHERE orderDate BETWEEN :start AND :end
        GROUP BY store
        ORDER BY totalSpent DESC
        """
    )
    fun storeSummaries(start: Long, end: Long): Flow<List<StoreSummary>>

    @Query(
        """
        SELECT SUM(IFNULL(amount, 0)) FROM orders
        WHERE orderDate BETWEEN :start AND :end
        """
    )
    fun totalSpent(start: Long, end: Long): Flow<Double?>

    @Query(
        """
        SELECT COUNT(*) FROM orders
        WHERE orderDate BETWEEN :start AND :end
        """
    )
    fun orderCount(start: Long, end: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders")
    fun totalOrderCount(): Flow<Int>

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    @Query("DELETE FROM processed_messages")
    suspend fun deleteAllProcessed()

    @Query("DELETE FROM tracking_events")
    suspend fun deleteAllEvents()
}

/** Prefer later-in-lifecycle statuses when merging emails for one order. */
object OrderStatusMerge {
    fun prefer(current: OrderStatus, incoming: OrderStatus): OrderStatus {
        val rank = mapOf(
            OrderStatus.UNKNOWN to 0,
            OrderStatus.AWAITING_PAYMENT to 1,
            OrderStatus.PROCESSING to 2,
            OrderStatus.PAID to 3,
            OrderStatus.SHIPPED to 4,
            OrderStatus.IN_TRANSIT to 5,
            OrderStatus.OUT_FOR_DELIVERY to 6,
            OrderStatus.DELIVERED to 7,
            OrderStatus.DELAYED to 5,
            OrderStatus.CANCELLED to 8,
            OrderStatus.RETURNED to 9
        )
        if (incoming == OrderStatus.CANCELLED || incoming == OrderStatus.RETURNED) {
            return incoming
        }
        if (current == OrderStatus.CANCELLED || current == OrderStatus.RETURNED) {
            return current
        }
        return if ((rank[incoming] ?: 0) >= (rank[current] ?: 0)) incoming else current
    }
}
