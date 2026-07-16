package com.orderly.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orderly.app.data.OrderShare
import com.orderly.app.data.db.OrderStatus
import com.orderly.app.data.db.TrackingEventEntity
import com.orderly.app.data.tracking.LocationNames
import com.orderly.app.data.tracking.PakCourier
import com.orderly.app.ui.MainViewModel
import com.orderly.app.ui.formatAmount
import com.orderly.app.ui.formatDate
import com.orderly.app.ui.formatRelativeTime
import com.orderly.app.ui.statusLabel

@Composable
fun OrderDetailScreen(
    viewModel: MainViewModel,
    orderId: String,
    onRemoved: () -> Unit = {}
) {
    val cached = viewModel.cachedOrder(orderId)
    val observed by viewModel.observeOrder(orderId).collectAsState(initial = cached)
    val order = observed ?: cached
    val events by viewModel.observeEvents(orderId).collectAsState(initial = emptyList())
    val refreshing by viewModel.trackingRefreshing.collectAsState()
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(orderId, order?.trackingNumber) {
        if (!order?.trackingNumber.isNullOrBlank()) {
            viewModel.refreshTracking(orderId)
        }
    }

    if (order == null) {
        EmptyState("Order not found.")
        return
    }

    val courier = PakCourier.detect(order.trackingNumber, order.carrier)
    val trackUrl = order.trackingNumber?.let { courier.trackUrl(it) }
    val visibleEvents = events.distinctBy {
        val day = it.occurredAt / 86_400_000L
        "${it.status}|${it.location?.lowercase()}|$day|${it.description.take(40).lowercase()}"
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "a"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        order.store.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order.store.uppercase() + " STORE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        order.productSummary ?: order.subject,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                formatAmount(order.amount, order.currency),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Status pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(statusPillBg(order.status))
                .border(1.dp, statusPillBorder(order.status), RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = pulse }
                    .clip(CircleShape)
                    .background(statusColor(order.status))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                statusLabel(order.status).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(order.status),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
        }

        // Tracking card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Carrier", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        order.carrier ?: courier.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Tracking Number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        order.trackingNumber ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.History,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Last check: " + (order.lastLiveCheckAt?.let { formatRelativeTime(it) } ?: "Not checked yet"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.refreshTracking(order.id) },
                    enabled = !order.trackingNumber.isNullOrBlank() && !refreshing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        if (trackUrl != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trackUrl)))
                        }
                    },
                    enabled = trackUrl != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Track Site", style = MaterialTheme.typography.labelMedium)
                }
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent.createChooser(OrderShare.shareIntent(order), "Share order")
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Order Details", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Timeline
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "TIMELINE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            if (visibleEvents.isEmpty()) {
                Text(
                    if (order.trackingNumber.isNullOrBlank()) "No tracking events yet."
                    else "No checkpoints yet — tap Refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                visibleEvents.forEachIndexed { index, event ->
                    MockTimelineRow(
                        event = event,
                        isFirst = index == 0,
                        isLast = index == visibleEvents.lastIndex
                    )
                }
            }
        }

        // Email subject
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Mail,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "Source Email Subject",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    "\"${order.subject}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Metadata
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("ORDER ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(
                    order.orderNumber?.let { "#$it" } ?: "—",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ORDER DATE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(
                    formatDate(order.orderDate),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        OutlinedButton(
            onClick = { confirmRemove = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Remove order", style = MaterialTheme.typography.labelMedium)
        }
        Text(
            "Won't come back after sync. Use for food delivery or anything you don't want to track.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove this order?") },
            text = {
                Text(
                    "It will be deleted from Orderly and ignored on future email syncs. " +
                        "Your Gmail is never modified."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        viewModel.dismissOrder(order.id) { onRemoved() }
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text("Keep")
                }
            }
        )
    }
}

@Composable
private fun MockTimelineRow(
    event: TrackingEventEntity,
    isFirst: Boolean,
    isLast: Boolean
) {
    val isLive = event.source == "live"
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(if (isLast) 56.dp else 72.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                if (!isLast) {
                    drawLine(
                        color = Color(0xFFCFC4C5),
                        start = Offset(cx, 14.dp.toPx()),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                val r = if (isFirst) 5.5.dp.toPx() else 4.5.dp.toPx()
                drawCircle(
                    color = if (isFirst) Color.Black else Color(0xFF7E7576),
                    radius = r,
                    center = Offset(cx, 10.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    event.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Normal,
                    color = if (isFirst) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatRelativeTime(event.occurredAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            LocationNames.sanitize(event.location)?.let { loc ->
                Text(
                    loc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (isLive) "LIVE" else "EMAIL",
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isLive && isFirst) MaterialTheme.colorScheme.primary
                        else if (isLive) MaterialTheme.colorScheme.surfaceContainerHighest
                        else Color(0xFFEFF6FF)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isLive && isFirst -> MaterialTheme.colorScheme.onPrimary
                    isLive -> MaterialTheme.colorScheme.secondary
                    else -> Color(0xFF2563EB)
                }
            )
        }
    }
}

private fun statusPillBg(status: OrderStatus): Color = when (status) {
    OrderStatus.DELIVERED -> Color(0xFFECFDF5)
    OrderStatus.DELAYED, OrderStatus.CANCELLED, OrderStatus.RETURNED -> Color(0xFFFFF7ED)
    OrderStatus.OUT_FOR_DELIVERY, OrderStatus.IN_TRANSIT, OrderStatus.SHIPPED -> Color(0xFFECFDF5)
    else -> Color(0xFFF3F4F6)
}

private fun statusPillBorder(status: OrderStatus): Color = when (status) {
    OrderStatus.DELIVERED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.IN_TRANSIT -> Color(0xFFD1FAE5)
    OrderStatus.DELAYED -> Color(0xFFFFEDD5)
    else -> Color(0xFFE5E7EB)
}
