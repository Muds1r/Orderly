package com.orderly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orderly.app.data.db.OrderEntity
import com.orderly.app.data.db.OrderStatus
import com.orderly.app.data.tracking.LocationNames
import com.orderly.app.ui.DashboardFilter
import com.orderly.app.ui.MainViewModel
import com.orderly.app.ui.SyncState
import com.orderly.app.ui.formatAmount
import com.orderly.app.ui.formatEta
import com.orderly.app.ui.formatRelativeTime
import com.orderly.app.ui.orderFooterLine
import com.orderly.app.ui.theme.StatusDelayed
import com.orderly.app.ui.theme.StatusInTransit

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
    val filter by viewModel.dashboardFilter.collectAsState()
    val filtered by viewModel.filteredDashboardOrders.collectAsState()
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
                // Hero stats
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                "THIS MONTH SPEND",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                formatAmount(monthSpent, "PKR"),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "$monthCount",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Orders",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    if (lastSync != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalHairline()
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Last sync: ${formatRelativeTime(lastSync!!)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashChip(
                        label = "Active",
                        count = active.size,
                        selected = filter == DashboardFilter.ACTIVE,
                        onClick = { viewModel.setDashboardFilter(DashboardFilter.ACTIVE) }
                    )
                    DashChip(
                        label = "In Transit",
                        count = inTransit.size,
                        selected = filter == DashboardFilter.IN_TRANSIT,
                        onClick = { viewModel.setDashboardFilter(DashboardFilter.IN_TRANSIT) }
                    )
                    DashChip(
                        label = "Delayed",
                        count = delayed.size,
                        selected = filter == DashboardFilter.DELAYED,
                        onClick = { viewModel.setDashboardFilter(DashboardFilter.DELAYED) }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (filter != DashboardFilter.ACTIVE) {
                item {
                    SectionRule(
                        title = filter.label.uppercase(),
                        trailing = if (filter == DashboardFilter.DELAYED && delayed.isNotEmpty()) {
                            "${delayed.size} Action Needed"
                        } else null,
                        trailingError = filter == DashboardFilter.DELAYED
                    )
                }
                if (filtered.isEmpty()) {
                    item { EmptyState("No orders in this filter.") }
                } else {
                    items(filtered, key = { it.id }) { order ->
                        if (filter == DashboardFilter.DELAYED) {
                            DelayedCard(order) { onOrderClick(order.id) }
                        } else {
                            TransitCard(order) { onOrderClick(order.id) }
                        }
                    }
                }
            } else {
                if (delayed.isNotEmpty()) {
                    item {
                        SectionRule(
                            title = "DELAYED",
                            trailing = "${delayed.size} Action Needed",
                            trailingError = true
                        )
                    }
                    items(delayed.take(5), key = { it.id }) { order ->
                        DelayedCard(order) { onOrderClick(order.id) }
                    }
                }

                if (inTransit.isNotEmpty()) {
                    item { SectionRule(title = "IN TRANSIT") }
                    items(inTransit.take(8), key = { it.id }) { order ->
                        TransitCard(order) { onOrderClick(order.id) }
                    }
                }

                if (active.isNotEmpty() && inTransit.isEmpty() && delayed.isEmpty()) {
                    item { SectionRule(title = "ACTIVE ORDERS") }
                    items(active.take(10), key = { it.id }) { order ->
                        TransitCard(order) { onOrderClick(order.id) }
                    }
                }

                if (delivered.isNotEmpty()) {
                    item { SectionRule(title = "RECENTLY DELIVERED") }
                    items(delivered, key = { "d-${it.id}" }) { order ->
                        DeliveredCard(order) { onOrderClick(order.id) }
                    }
                }

                if (active.isEmpty() && delivered.isEmpty()) {
                    item {
                        EmptyState("No orders yet. Pull down to sync Gmail.")
                    }
                }
            }

            item {
                Text(
                    "Made By Muds1r",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DashChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .then(
                if (!selected) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.Medium
        )
        Text(
            "$count",
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(fg.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SectionRule(
    title: String,
    trailing: String? = null,
    trailingError: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            letterSpacing = 1.sp
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = if (trailingError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun DelayedCard(order: OrderEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFEDD5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Warning, null, tint = StatusDelayed, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    order.store,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StatusPill("Delayed", Color(0xFFFFEDD5), StatusDelayed)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                LocationNames.sanitize(order.lastLocation)?.let { "Delayed in $it" }
                    ?: (order.productSummary ?: order.subject),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            order.orderNumber?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "ID: $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun TransitCard(order: OrderEntity, onClick: () -> Unit) {
    val line = formatEta(order.estimatedDelivery)
        ?: orderFooterLine(
            order.estimatedDelivery,
            LocationNames.sanitize(order.lastLocation),
            order.orderDate,
            order.status
        )
    val dot = when (order.status) {
        OrderStatus.OUT_FOR_DELIVERY, OrderStatus.IN_TRANSIT -> StatusInTransit
        OrderStatus.DELAYED -> StatusDelayed
        else -> Color(0xFFF59E0B)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StoreAvatar(
                store = order.store,
                modifier = Modifier.size(40.dp),
                productSummary = order.productSummary ?: order.subject
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    order.store,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    order.productSummary ?: order.subject,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                line,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeliveredCard(order: OrderEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                order.store,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${formatRelativeTime(order.updatedAt)} · ${formatAmount(order.amount, order.currency)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Text(
        text.uppercase(),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp
    )
}

@Composable
private fun HorizontalHairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    )
}
