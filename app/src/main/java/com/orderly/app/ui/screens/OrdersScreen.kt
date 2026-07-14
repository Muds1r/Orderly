package com.orderly.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orderly.app.ui.MainViewModel

@Composable
fun OrdersScreen(
    viewModel: MainViewModel,
    onOrderClick: (String) -> Unit
) {
    val query by viewModel.searchQuery.collectAsState()
    val orders by viewModel.searchedOrders.collectAsState()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Search store, product, order #…") },
            singleLine = true
        )

        if (orders.isEmpty()) {
            EmptyState(
                if (query.isBlank()) "No orders yet."
                else "No matches for \"$query\"."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(orders, key = { it.id }) { order ->
                    OrderRow(order) { onOrderClick(order.id) }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
