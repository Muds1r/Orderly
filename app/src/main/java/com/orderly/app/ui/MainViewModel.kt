package com.orderly.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.orderly.app.data.OrderShare
import com.orderly.app.data.SettingsStore
import com.orderly.app.data.db.AppDatabase
import com.orderly.app.data.db.ExcludedOrderEntity
import com.orderly.app.data.db.ExclusionKeys
import com.orderly.app.data.db.MonthlySpend
import com.orderly.app.data.db.OrderEntity
import com.orderly.app.data.db.OrderStatus
import com.orderly.app.data.db.StoreSummary
import com.orderly.app.data.mail.ImapClient
import com.orderly.app.sync.SyncEngine
import com.orderly.app.sync.SyncScheduler
import com.orderly.app.sync.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class RangePreset(val label: String, val days: Int?) {
    MONTH("30d", 30),
    YEAR("1y", 365),
    ALL("All", null)
}

/**
 * Dashboard chips.
 * ACTIVE = overview (stacked delayed / transit / delivered) — matches mock "Active" selected.
 */
enum class DashboardFilter(val label: String) {
    ACTIVE("Active"),
    IN_TRANSIT("In Transit"),
    DELAYED("Delayed")
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val count: Int, val at: Long) : SyncState
    data class Error(val message: String) : SyncState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).orderDao()
    private val settings = SettingsStore(app)

    val accountName: StateFlow<String?> = settings.accountName
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lastSync: StateFlow<Long?> = settings.lastSync
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _range = MutableStateFlow(RangePreset.MONTH)
    val range: StateFlow<RangePreset> = _range

    private val _dashboardFilter = MutableStateFlow(DashboardFilter.ACTIVE)
    val dashboardFilter: StateFlow<DashboardFilter> = _dashboardFilter

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _trackingRefreshing = MutableStateFlow(false)
    val trackingRefreshing: StateFlow<Boolean> = _trackingRefreshing

    init {
        viewModelScope.launch {
            SyncScheduler.manualSyncFlow(getApplication()).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                        _syncState.value = SyncState.Syncing
                    WorkInfo.State.SUCCEEDED -> {
                        val count = info.outputData.getInt(SyncWorker.KEY_COUNT, 0)
                        val at = info.outputData.getLong(SyncWorker.KEY_AT, System.currentTimeMillis())
                        _syncState.value = SyncState.Success(count, at)
                    }
                    WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString(SyncWorker.KEY_ERROR) ?: "Sync failed"
                        if (settings.lastSync.first() == null) {
                            settings.clear()
                            SyncScheduler.cancel(getApplication())
                        }
                        _syncState.value = SyncState.Error(err)
                    }
                    WorkInfo.State.CANCELLED -> _syncState.value = SyncState.Idle
                }
            }
        }
    }

    private fun rangeStart(preset: RangePreset): Long {
        val days = preset.days ?: return 0L
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
    }

    private fun monthStart(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val allOrders: StateFlow<List<OrderEntity>> = dao.allOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeOrders: StateFlow<List<OrderEntity>> = dao.activeOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inTransitOrders: StateFlow<List<OrderEntity>> = dao.inTransitOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val delayedOrders: StateFlow<List<OrderEntity>> = dao.delayedOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyDelivered: StateFlow<List<OrderEntity>> = dao.recentlyDelivered(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchedOrders: StateFlow<List<OrderEntity>> = _searchQuery
        .flatMapLatest { dao.searchOrders(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storeSummaries: StateFlow<List<StoreSummary>> = _range
        .flatMapLatest { dao.storeSummaries(rangeStart(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val periodSpent: StateFlow<Double> = _range
        .flatMapLatest { dao.totalSpent(rangeStart(it), Long.MAX_VALUE).map { v -> v ?: 0.0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val periodOrderCount: StateFlow<Int> = _range
        .flatMapLatest { dao.orderCount(rangeStart(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Spend in the previous equivalent window (null for All / no prior data). */
    val previousPeriodSpent: StateFlow<Double?> = _range
        .flatMapLatest { preset ->
            val prev = previousRange(preset)
            if (prev == null) {
                kotlinx.coroutines.flow.flowOf(null)
            } else {
                dao.totalSpent(prev.first, prev.second).map { it }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val periodChangePercent: StateFlow<Double?> =
        combine(periodSpent, previousPeriodSpent) { current, previous ->
            if (previous == null) null else percentChange(current, previous)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Last ~12 months of spend buckets for the Insights bar chart. */
    val monthlySpendTrend: StateFlow<List<MonthlySpend>> = _range
        .flatMapLatest {
            val end = System.currentTimeMillis()
            val start = end - TimeUnit.DAYS.toMillis(365)
            dao.monthlySpend(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthSpent: StateFlow<Double> = dao.totalSpent(monthStart(), Long.MAX_VALUE)
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthOrderCount: StateFlow<Int> = dao.orderCount(monthStart(), Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lifetimeOrderCount: StateFlow<Int> = dao.totalOrderCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Orders shown on Dashboard when a chip other than ALL is selected. */
    val filteredDashboardOrders: StateFlow<List<OrderEntity>> = combine(
        _dashboardFilter,
        activeOrders,
        inTransitOrders,
        delayedOrders
    ) { filter, active, transit, delayed ->
        when (filter) {
            DashboardFilter.ACTIVE -> emptyList() // overview uses stacked sections
            DashboardFilter.IN_TRANSIT -> transit
            DashboardFilter.DELAYED -> delayed
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeOrder(id: String) = dao.observeOrder(id)

    fun observeEvents(id: String) = dao.observeEvents(id)

    fun cachedOrder(id: String): OrderEntity? =
        allOrders.value.find { it.id == id }
            ?: activeOrders.value.find { it.id == id }
            ?: searchedOrders.value.find { it.id == id }

    fun setRange(preset: RangePreset) {
        _range.value = preset
    }

    fun setDashboardFilter(filter: DashboardFilter) {
        _dashboardFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Manual order (not from Gmail). Used by the Orders FAB.
     * Returns the new order id, or null if store is blank.
     */
    fun addManualOrder(
        store: String,
        product: String?,
        orderNumber: String?,
        amount: Double?,
        currency: String?,
        trackingNumber: String?,
        carrier: String?,
        status: OrderStatus = OrderStatus.PROCESSING,
        estimatedDelivery: Long? = null
    ) {
        val cleanStore = store.trim()
        if (cleanStore.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = "manual|${UUID.randomUUID()}"
            val cn = trackingNumber?.trim()?.takeIf { it.isNotBlank() }
            dao.insertIgnore(
                OrderEntity(
                    id = id,
                    store = cleanStore,
                    orderNumber = orderNumber?.trim()?.takeIf { it.isNotBlank() },
                    productSummary = product?.trim()?.takeIf { it.isNotBlank() },
                    trackingNumber = cn,
                    carrier = carrier?.trim()?.takeIf { it.isNotBlank() },
                    orderDate = now,
                    amount = amount,
                    currency = currency?.trim()?.takeIf { it.isNotBlank() } ?: "PKR",
                    paymentStatus = if (amount != null && amount > 0) "Paid" else null,
                    status = status,
                    estimatedDelivery = estimatedDelivery,
                    subject = product?.trim()?.takeIf { it.isNotBlank() }
                        ?: "Manual order · $cleanStore",
                    lastMessageId = "manual|$id",
                    updatedAt = now
                )
            )
        }
    }

    private fun previousRange(preset: RangePreset): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        val days = preset.days ?: return null
        val length = TimeUnit.DAYS.toMillis(days.toLong())
        val currentStart = now - length
        val prevEnd = currentStart - 1
        val prevStart = currentStart - length
        return prevStart to prevEnd
    }

    fun connect(email: String, appPassword: String) {
        if (_syncState.value is SyncState.Syncing) return
        val cleanEmail = email.trim()
        val cleanPassword = ImapClient.cleanAppPassword(appPassword)
        if (cleanEmail.isBlank() || cleanPassword.isBlank()) return

        viewModelScope.launch {
            settings.setCredentials(cleanEmail, cleanPassword)
            SyncScheduler.schedulePeriodic(getApplication())
            SyncScheduler.enqueueManual(getApplication(), forceFull = true)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            SyncEngine.invalidate()
            SyncScheduler.cancel(getApplication())
            settings.clear()
            dao.deleteAllEvents()
            dao.deleteAllOrders()
            dao.deleteAllProcessed()
            dao.deleteAllExclusions()
            _syncState.value = SyncState.Idle
        }
    }

    fun syncNow() {
        if (_syncState.value is SyncState.Syncing) return
        SyncScheduler.enqueueManual(getApplication(), forceFull = false)
    }

    fun refreshTracking(orderId: String) {
        if (_trackingRefreshing.value) return
        viewModelScope.launch {
            _trackingRefreshing.value = true
            runCatching { SyncEngine.refreshTrackingForOrder(getApplication(), orderId) }
            _trackingRefreshing.value = false
        }
    }

    /**
     * Permanently remove an order. Matching emails are skipped on future syncs.
     */
    fun dismissOrder(orderId: String, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            val order = dao.getById(orderId) ?: return@launch
            val now = System.currentTimeMillis()
            val label = buildString {
                append(order.store)
                order.orderNumber?.let { append(" #").append(it) }
            }
            ExclusionKeys.from(order).forEach { key ->
                dao.insertExclusion(
                    ExcludedOrderEntity(
                        exclusionKey = key,
                        excludedAt = now,
                        label = label
                    )
                )
            }
            dao.deleteEventsForOrder(orderId)
            dao.deleteOrderById(orderId)
            onDone?.invoke()
        }
    }

    suspend fun buildCsvShareIntent() =
        OrderShare.csvShareIntent(getApplication(), dao.listVisibleOrders())
}
