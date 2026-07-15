package com.orderly.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.orderly.app.data.OrderShare
import com.orderly.app.data.SettingsStore
import com.orderly.app.data.db.AppDatabase
import com.orderly.app.data.db.OrderEntity
import com.orderly.app.data.db.StoreSummary
import com.orderly.app.data.mail.ImapClient
import com.orderly.app.notify.StatusNotifier
import com.orderly.app.sync.SyncEngine
import com.orderly.app.sync.SyncScheduler
import com.orderly.app.sync.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class RangePreset(val label: String, val days: Int?) {
    MONTH("30d", 30),
    YEAR("1y", 365),
    ALL("All", null)
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val count: Int, val at: Long) : SyncState
    data class Error(val message: String) : SyncState
}

sealed interface TrackingRefreshState {
    data object Idle : TrackingRefreshState
    data object Loading : TrackingRefreshState
    data class Ok(val at: Long) : TrackingRefreshState
    data class Fail(val message: String) : TrackingRefreshState
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _trackingRefresh = MutableStateFlow<TrackingRefreshState>(TrackingRefreshState.Idle)
    val trackingRefresh: StateFlow<TrackingRefreshState> = _trackingRefresh

    init {
        StatusNotifier.ensureChannel(app)
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

    val watchedOrders: StateFlow<List<OrderEntity>> = dao.watchedOrders()
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

    val monthSpent: StateFlow<Double> = dao.totalSpent(monthStart(), Long.MAX_VALUE)
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthOrderCount: StateFlow<Int> = dao.orderCount(monthStart(), Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lifetimeOrderCount: StateFlow<Int> = dao.totalOrderCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun observeOrder(id: String) = dao.observeOrder(id)

    fun observeEvents(id: String) = dao.observeEvents(id)

    fun cachedOrder(id: String): OrderEntity? =
        allOrders.value.find { it.id == id }
            ?: activeOrders.value.find { it.id == id }
            ?: searchedOrders.value.find { it.id == id }
            ?: watchedOrders.value.find { it.id == id }

    fun setRange(preset: RangePreset) {
        _range.value = preset
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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
            dao.deleteAllEvents()
            dao.deleteAllOrders()
            dao.deleteAllProcessed()
            _syncState.value = SyncState.Idle
        }
    }

    fun syncNow() {
        if (_syncState.value is SyncState.Syncing) return
        SyncScheduler.enqueueManual(getApplication(), forceFull = false)
    }

    fun refreshTracking(orderId: String) {
        if (_trackingRefresh.value is TrackingRefreshState.Loading) return
        viewModelScope.launch {
            _trackingRefresh.value = TrackingRefreshState.Loading
            val ok = runCatching {
                SyncEngine.refreshTrackingForOrder(getApplication(), orderId)
            }.getOrElse {
                _trackingRefresh.value = TrackingRefreshState.Fail(it.message ?: "Refresh failed")
                return@launch
            }
            _trackingRefresh.value = if (ok) {
                TrackingRefreshState.Ok(System.currentTimeMillis())
            } else {
                TrackingRefreshState.Fail("Couldn't reach the courier. Try again.")
            }
        }
    }

    fun clearTrackingRefresh() {
        _trackingRefresh.value = TrackingRefreshState.Idle
    }

    fun hideOrder(id: String) {
        viewModelScope.launch { dao.setHidden(id, true) }
    }

    fun softDeleteOrder(id: String) {
        viewModelScope.launch { dao.setDeletedAt(id, System.currentTimeMillis()) }
    }

    fun toggleWatched(id: String, currentlyWatched: Boolean) {
        viewModelScope.launch { dao.setWatched(id, !currentlyWatched) }
    }

    fun exportCsvIntent() = viewModelScope.launch {
        // Intent returned via a one-shot event is awkward; callers use shareCsv()
    }

    suspend fun buildCsvShareIntent() =
        OrderShare.csvShareIntent(getApplication(), dao.listVisibleOrders())
}
