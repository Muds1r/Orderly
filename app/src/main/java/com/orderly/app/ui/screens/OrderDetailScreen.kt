package com.orderly.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orderly.app.data.db.TrackingEventEntity
import com.orderly.app.data.tracking.PakCourier
import com.orderly.app.ui.MainViewModel
import com.orderly.app.ui.formatAmount
import com.orderly.app.ui.formatDate
import com.orderly.app.ui.formatDateTime
import com.orderly.app.ui.statusLabel

@Composable
fun OrderDetailScreen(
    viewModel: MainViewModel,
    orderId: String
) {
    val cached = viewModel.cachedOrder(orderId)
    val observed by viewModel.observeOrder(orderId).collectAsState(initial = cached)
    val order = observed ?: cached
    val events by viewModel.observeEvents(orderId).collectAsState(initial = emptyList())
    val context = LocalContext.current

    if (order == null) {
        EmptyState("Order not found.")
        return
    }

    val courier = PakCourier.detect(order.trackingNumber, order.carrier)
    val trackUrl = order.trackingNumber?.let { courier.trackUrl(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SoftHeroCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    order.store,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    order.productSummary ?: order.subject,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    statusLabel(order.status),
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor(order.status),
                    fontWeight = FontWeight.Medium
                )
                if (!order.lastLocation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Last seen: ${order.lastLocation}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formatAmount(order.amount, order.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow("Order number", order.orderNumber ?: "—")
                DetailRow("Order date", formatDate(order.orderDate))
                DetailRow("Payment", order.paymentStatus ?: "—")
                DetailRow("Tracking", order.trackingNumber ?: "—")
                DetailRow("Carrier", order.carrier ?: "—")
                DetailRow("Shipped from", order.shipFrom ?: "—")
                DetailRow("Last location", order.lastLocation ?: "—")
                DetailRow("Updated", formatDateTime(order.updatedAt))
                if (trackUrl != null && !order.trackingNumber.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(trackUrl))
                            )
                        }
                    ) {
                        Text("Open on ${courier.displayName}")
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Tracking timeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    events.forEachIndexed { index, event ->
                        TimelineRow(
                            event = event,
                            isLast = index == events.lastIndex
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Email subject",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(order.subject, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TimelineRow(event: TrackingEventEntity, isLast: Boolean) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = event.status?.let { statusColor(it) } ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else 12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(if (isLast) 48.dp else 64.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                drawCircle(color = dotColor, radius = 5.dp.toPx(), center = Offset(cx, 8.dp.toPx()))
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(cx, 14.dp.toPx()),
                        end = Offset(cx, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!event.location.isNullOrBlank()) {
                Text(
                    event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                formatDateTime(event.occurredAt) +
                    if (event.source == "live") " · live" else " · email",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
