package com.orderly.app.sync

import android.content.Context
import com.orderly.app.data.SettingsStore
import com.orderly.app.data.db.AppDatabase
import com.orderly.app.data.db.OrderEntity
import com.orderly.app.data.db.OrderStatus
import com.orderly.app.data.db.OrderStatusMerge
import com.orderly.app.data.db.ProcessedMessageEntity
import com.orderly.app.data.mail.ImapClient
import com.orderly.app.data.parser.OrderParser
import com.orderly.app.data.tracking.LiveTrackingClient
import com.orderly.app.data.tracking.LocationNames
import com.orderly.app.data.tracking.PakCourier
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object SyncEngine {

    const val LOOKBACK_DAYS = 365
    const val SYNC_LOGIC_VERSION = 11
    private val OVERLAP_MS = TimeUnit.DAYS.toMillis(1)
    private val generation = AtomicLong(0)

    fun invalidate() {
        generation.incrementAndGet()
    }

    fun lookbackCutoff(): Long =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(LOOKBACK_DAYS.toLong())

    private suspend fun sinceFor(settings: SettingsStore): Long {
        val full = lookbackCutoff()
        val marker = settings.syncMarker.first()
        val last = settings.lastSync.first()
        return if (marker != SYNC_LOGIC_VERSION || last == null) full
        else maxOf(full, last - OVERLAP_MS)
    }

    suspend fun sync(context: Context, forceFull: Boolean = false): Int? {
        val gen = generation.get()
        val settings = SettingsStore(context)
        val email = settings.accountName.first() ?: return null
        val password = settings.appPassword.first() ?: return null
        val dao = AppDatabase.get(context).orderDao()

        val marker = settings.syncMarker.first()
        if (marker != SYNC_LOGIC_VERSION) {
            dao.deleteAllProcessed()
        }

        val since = if (forceFull) lookbackCutoff() else sinceFor(settings)
        val results = ImapClient(email, password).fetchOrders(since)

        if (generation.get() != gen || settings.accountName.first() != email) {
            throw SyncCancelledException()
        }

        results.forEach { result ->
            if (generation.get() != gen || settings.accountName.first() != email) {
                throw SyncCancelledException()
            }
            if (dao.wasProcessed(result.messageId)) return@forEach

            if (result.trackingOnly) {
                val tracking = result.order.trackingNumber ?: return@forEach
                val existing = dao.getByTrackingNumber(tracking)
                if (existing != null && existing.deletedAt == null) {
                    applyLiveOrEmailUpdate(
                        dao, existing, result.order.carrier, result.order.shipFrom,
                        result.order.lastLocation, result.order.status, result.events, forceStatus = false
                    )
                    dao.insertProcessed(
                        ProcessedMessageEntity(
                            messageId = result.messageId,
                            orderId = existing.id,
                            processedAt = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                dao.upsertOrder(result.order, result.messageId, result.events)
            }
        }

        if (generation.get() != gen || settings.accountName.first() != email) {
            throw SyncCancelledException()
        }

        refreshLiveTracking(context, dao, gen, settings, email)

        if (generation.get() != gen || settings.accountName.first() != email) {
            throw SyncCancelledException()
        }

        cleanupFoulAndDuplicateOrders(dao)

        settings.setLastSync(System.currentTimeMillis())
        settings.setSyncMarker(SYNC_LOGIC_VERSION)
        return results.size
    }

    /**
     * Live-only refresh for one order (no Gmail). Returns true if courier data applied.
     */
    suspend fun refreshTrackingForOrder(context: Context, orderId: String): Boolean {
        val dao = AppDatabase.get(context).orderDao()
        val order = dao.getById(orderId) ?: return false
        val tracking = order.trackingNumber ?: return false
        val hint = PakCourier.detect(tracking, order.carrier)
        val live = runCatching { LiveTrackingClient.track(tracking, hint) }.getOrNull()
        dao.setLastLiveCheckAt(orderId, System.currentTimeMillis())
        if (live == null) return false
        applyLiveOrEmailUpdate(
            dao, order,
            live.carrier.displayName, live.origin,
            live.lastLocation ?: live.destination,
            live.currentStatus, live.events, forceStatus = true
        )
        return true
    }

    /** Live-only batch refresh (no IMAP). */
    suspend fun refreshLiveTrackingOnly(context: Context): Int {
        val settings = SettingsStore(context)
        val email = settings.accountName.first() ?: return 0
        val dao = AppDatabase.get(context).orderDao()
        val gen = generation.get()
        return refreshLiveTracking(context, dao, gen, settings, email)
    }

    private suspend fun applyLiveOrEmailUpdate(
        dao: com.orderly.app.data.db.OrderDao,
        existing: OrderEntity,
        carrier: String?,
        shipFrom: String?,
        lastLocation: String?,
        status: OrderStatus?,
        events: List<com.orderly.app.data.db.TrackingEventDraft>,
        forceStatus: Boolean
    ) {
        dao.applyTrackingUpdate(
            orderId = existing.id,
            carrier = carrier,
            shipFrom = shipFrom,
            lastLocation = lastLocation,
            status = status,
            events = events,
            forceStatus = forceStatus
        )
    }

    private suspend fun cleanupFoulAndDuplicateOrders(dao: com.orderly.app.data.db.OrderDao) {
        val all = dao.listAllOrders()
        all.filter { order ->
            val cn = order.trackingNumber
            !cn.isNullOrBlank() && OrderParser.isPhoneLike(cn) &&
                (order.orderNumber.isNullOrBlank() || cn == order.orderNumber)
        }.forEach { foul ->
            dao.deleteEventsForOrder(foul.id)
            dao.deleteOrderById(foul.id)
        }

        val remaining = dao.listAllOrders()
        remaining
            .filter { order -> !order.orderNumber.isNullOrBlank() && order.deletedAt == null }
            .groupBy { order -> order.store.lowercase() to order.orderNumber!! }
            .values
            .filter { group -> group.size > 1 }
            .forEach { group ->
                val survivor = group.minWith(
                    compareBy<OrderEntity> { if (it.id.startsWith("tracking|")) 1 else 0 }
                        .thenBy { if (it.id.contains("|cn|")) 1 else 0 }
                        .thenBy { it.orderDate }
                )
                group.filter { dup -> dup.id != survivor.id }.forEach { dup ->
                    val events = dao.eventsForOrder(dup.id)
                    events.forEach { ev ->
                        dao.insertEventIgnore(
                            ev.copy(
                                id = "${survivor.id}|${ev.fingerprint}",
                                orderId = survivor.id
                            )
                        )
                    }
                    dao.updateFields(
                        id = survivor.id,
                        productSummary = dup.productSummary,
                        trackingNumber = dup.trackingNumber,
                        carrier = dup.carrier,
                        shipFrom = dup.shipFrom,
                        lastLocation = dup.lastLocation,
                        amount = dup.amount,
                        currency = dup.currency,
                        paymentStatus = dup.paymentStatus,
                        status = OrderStatusMerge.prefer(survivor.status, dup.status),
                        estimatedDelivery = dup.estimatedDelivery,
                        subject = dup.subject.ifBlank { survivor.subject },
                        lastMessageId = dup.lastMessageId,
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.deleteEventsForOrder(dup.id)
                    dao.deleteOrderById(dup.id)
                }
            }

        dao.listAllOrders().forEach { order ->
            val summary = order.productSummary
            if (summary != null && OrderParser.isJunkProductSummary(summary)) {
                dao.setProductSummary(order.id, null)
            }
            val loc = order.lastLocation
            if (loc != null && LocationNames.sanitize(loc) == null) {
                dao.setLastLocation(order.id, null)
            }
        }
    }

    private suspend fun refreshLiveTracking(
        context: Context,
        dao: com.orderly.app.data.db.OrderDao,
        gen: Long,
        settings: SettingsStore,
        email: String
    ): Int {
        var updated = 0
        val candidates = dao.ordersNeedingLiveTrack(40)
        for (order in candidates) {
            if (generation.get() != gen || settings.accountName.first() != email) {
                throw SyncCancelledException()
            }
            val tracking = order.trackingNumber ?: continue
            val hint = PakCourier.detect(tracking, order.carrier)
            val live = runCatching { LiveTrackingClient.track(tracking, hint) }.getOrNull()
            dao.setLastLiveCheckAt(order.id, System.currentTimeMillis())
            if (live == null) continue
            applyLiveOrEmailUpdate(
                dao, order,
                live.carrier.displayName, live.origin,
                live.lastLocation ?: live.destination,
                live.currentStatus, live.events, forceStatus = true
            )
            updated++
        }
        return updated
    }
}

class SyncCancelledException : Exception("Sync cancelled")
