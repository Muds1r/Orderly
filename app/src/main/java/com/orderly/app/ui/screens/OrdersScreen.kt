package com.orderly.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orderly.app.ui.MainViewModel
import com.orderly.app.ui.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: MainViewModel,
    onOrderClick: (String) -> Unit
) {
    val query by viewModel.searchQuery.collectAsState()
    val orders by viewModel.searchedOrders.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val refreshing = syncState is SyncState.Syncing

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Search store, product, order #…") },
            singleLine = true
        )

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.syncNow() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (orders.isEmpty()) {
                EmptyState(
                    if (query.isBlank()) "No orders yet. Pull down to sync."
                    else "No matches for \"$query\"."
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(orders, key = { it.id }) { order ->
                        OrderRow(order) { onOrderClick(order.id) }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}
