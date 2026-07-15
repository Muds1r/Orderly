package com.orderly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orderly.app.ui.MainViewModel
import com.orderly.app.ui.SyncState
import com.orderly.app.ui.formatAmount
import com.orderly.app.ui.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onOrderClick: (String) -> Unit
) {
    val active by viewModel.activeOrders.collectAsState()
    val inTransit by viewModel.inTransitOrders.collectAsState()
    val delayed by viewModel.delayedOrders.collectAsState()
    val delivered by viewModel.recentlyDelivered.collectAsState()
    val monthSpent by viewModel.monthSpent.collectAsState()
    val monthCount by viewModel.monthOrderCount.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val refreshing = syncState is SyncState.Syncing

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { viewModel.syncNow() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                SoftHeroCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "This month",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            formatAmount(monthSpent, "PKR"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "$monthCount orders",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lastSync != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Last sync ${formatDateTime(lastSync!!)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip("Active", active.size.toString(), Modifier.weight(1f))
                    StatChip("In transit", inTransit.size.toString(), Modifier.weight(1f))
                    StatChip("Delayed", delayed.size.toString(), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (delayed.isNotEmpty()) {
                item { SectionHeader("Delayed") }
                items(delayed.take(5), key = { it.id }) { order ->
                    OrderRow(order) { onOrderClick(order.id) }
                }
            }

            if (inTransit.isNotEmpty()) {
                item { SectionHeader("In transit") }
                items(inTransit.take(8), key = { it.id }) { order ->
                    OrderRow(order) { onOrderClick(order.id) }
                }
            }

            if (active.isNotEmpty() && inTransit.isEmpty() && delayed.isEmpty()) {
                item { SectionHeader("Active orders") }
                items(active.take(10), key = { it.id }) { order ->
                    OrderRow(order) { onOrderClick(order.id) }
                }
            }

            if (delivered.isNotEmpty()) {
                item { SectionHeader("Recently delivered") }
                items(delivered, key = { "d-${it.id}" }) { order ->
                    OrderRow(order) { onOrderClick(order.id) }
                }
            }

            if (active.isEmpty() && delivered.isEmpty()) {
                item {
                    EmptyState("No orders yet. Pull down to sync Gmail.")
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
