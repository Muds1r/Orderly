package com.orderly.app.sync

import android.content.Context
import com.orderly.app.data.SettingsStore
import com.orderly.app.data.db.AppDatabase
import com.orderly.app.data.db.OrderEntity
import com.orderly.app.data.db.OrderStatusMerge
import com.orderly.app.data.db.ProcessedMessageEntity
import com.orderly.app.data.mail.ImapClient
import com.orderly.app.data.parser.OrderParser
import com.orderly.app.data.tracking.LiveTrackingClient
import com.orderly.app.data.tracking.PakCourier
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared sync logic for manual sync and the background worker.
 *
 * 1) Pull store + courier emails from Gmail (IMAP read-only)
 * 2) Upsert orders / append email timeline events
 * 3) Live-refresh in-transit packages via PostEx / Leopards / TCS public track
 */
object SyncEngine {

    const val LOOKBACK_DAYS = 365
    const val SYNC_LOGIC_VERSION = 8
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

    /**
     * @return number of emails parsed this run, or null if not signed in.
     */
    suspend fun sync(context: Context, forceFull: Boolean = false): Int? {
        val gen = generation.get()
        val settings = SettingsStore(context)
        val email = settings.accountName.first() ?: return null
        val password = settings.appPassword.first() ?: return null
        val dao = AppDatabase.get(context).orderDao()

        val marker = settings.syncMarker.first()
        // New parser rules: re-read already-seen emails so amounts/status/duplicates fix.
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
                if (existing != null) {
                    dao.applyTrackingUpdate(
                        orderId = existing.id,
                        carrier = result.order.carrier,
                        shipFrom = result.order.shipFrom,
                        lastLocation = result.order.lastLocation,
                        status = result.order.status,
                        events = result.events
                    )
                    dao.insertProcessed(
                        ProcessedMessageEntity(
                            messageId = result.messageId,
                            orderId = existing.id,
                            processedAt = System.currentTimeMillis()
                        )
                    )
                }
                // No matching store order yet — skip placeholder so insights stay clean.
                // Store email with the same CN will create the order; later courier mail merges.
            } else {
                dao.upsertOrder(result.order, result.messageId, result.events)
            }
        }

        if (generation.get() != gen || settings.accountName.first() != email) {
            throw SyncCancelledException()
        }

        refreshLiveTracking(dao, gen, settings, email)

        if (generation.get() != gen || settings.accountName.first() != email) {
            throw SyncCancelledException()
        }

        cleanupFoulAndDuplicateOrders(dao)

        if (generation.get() != gen || settings.accountName.first() != email) {
            throw SyncCancelledException()
        }

        settings.setLastSync(System.currentTimeMillis())
        settings.setSyncMarker(SYNC_LOGIC_VERSION)
        return results.size
    }

    /**
     * Drop phone-number "tracking" junk and merge same store+order # rows
     * left over from older parsers.
     */
    private suspend fun cleanupFoulAndDuplicateOrders(dao: com.orderly.app.data.db.OrderDao) {
        val all = dao.listAllOrders()
        // Phone / hotline mistaken as CN (e.g. Saeed Ghani WhatsApp 021…)
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
            .filter { order -> !order.orderNumber.isNullOrBlank() }
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
    }

    private suspend fun refreshLiveTracking(
        dao: com.orderly.app.data.db.OrderDao,
        gen: Long,
        settings: SettingsStore,
        email: String
    ) {
        val candidates = dao.ordersNeedingLiveTrack(40)
        for (order in candidates) {
            if (generation.get() != gen || settings.accountName.first() != email) {
                throw SyncCancelledException()
            }
            val tracking = order.trackingNumber ?: continue
            val hint = PakCourier.detect(tracking, order.carrier)
            val live = runCatching { LiveTrackingClient.track(tracking, hint) }.getOrNull()
                ?: continue

            // Live courier status is authoritative (fixes false "Delivered" from email wording).
            dao.applyTrackingUpdate(
                orderId = order.id,
                carrier = live.carrier.displayName,
                shipFrom = live.origin,
                lastLocation = live.lastLocation ?: live.destination,
                status = live.currentStatus,
                events = live.events,
                forceStatus = true
            )
        }
    }
}

class SyncCancelledException : Exception("Sync cancelled")
