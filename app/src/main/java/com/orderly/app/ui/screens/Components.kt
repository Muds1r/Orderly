package com.orderly.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import com.orderly.app.data.db.OrderEntity
import com.orderly.app.data.db.OrderStatus
import com.orderly.app.data.tracking.LocationNames
import com.orderly.app.ui.MainViewModel
import com.orderly.app.ui.RangePreset
import com.orderly.app.ui.formatAmount
import com.orderly.app.ui.formatDate
import com.orderly.app.ui.statusLabel
import com.orderly.app.ui.theme.StatusDelayed
import com.orderly.app.ui.theme.StatusDelivered
import com.orderly.app.ui.theme.StatusInTransit
import com.orderly.app.ui.theme.StatusProcessing

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    SoftCard(modifier = modifier, onClick = null, content = content)
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        },
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@Composable
fun SoftHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@Composable
fun RangeSelector(viewModel: MainViewModel) {
    val range by viewModel.range.collectAsState()
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        RangePreset.entries.forEachIndexed { index, preset ->
            SegmentedButton(
                selected = range == preset,
                onClick = { viewModel.setRange(preset) },
                shape = SegmentedButtonDefaults.itemShape(index, RangePreset.entries.size),
                label = { Text(preset.label) },
                colors = SegmentedButtonDefaults.colors(
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }
}

fun statusColor(status: OrderStatus): Color = when (status) {
    OrderStatus.DELIVERED -> StatusDelivered
    OrderStatus.DELAYED, OrderStatus.CANCELLED, OrderStatus.RETURNED -> StatusDelayed
    OrderStatus.SHIPPED, OrderStatus.IN_TRANSIT, OrderStatus.OUT_FOR_DELIVERY -> StatusInTransit
    else -> StatusProcessing
}

@Composable
fun OrderRow(
    order: OrderEntity,
    onClick: () -> Unit
) {
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onClick,
        content = {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(statusColor(order.status).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        order.store.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor(order.status)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order.productSummary ?: order.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        buildString {
                            append(order.store)
                            append(" · ")
                            append(statusLabel(order.status))
                            LocationNames.sanitize(order.lastLocation)?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatDate(order.orderDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    formatAmount(order.amount, order.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
