package com.orderly.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orderly.app.ui.MainViewModel
import com.orderly.app.ui.RangePreset
import com.orderly.app.ui.formatAmount
import com.orderly.app.ui.formatPeriodChange
import com.orderly.app.ui.monthLabel
import kotlinx.coroutines.launch

@Composable
fun InsightsScreen(viewModel: MainViewModel) {
    val spent by viewModel.periodSpent.collectAsState()
    val count by viewModel.periodOrderCount.collectAsState()
    val stores by viewModel.storeSummaries.collectAsState()
    val change by viewModel.periodChangePercent.collectAsState()
    val trend by viewModel.monthlySpendTrend.collectAsState()
    val range by viewModel.range.collectAsState()
    val avg = if (count > 0) spent / count else 0.0
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changeLabel = formatPeriodChange(change)
    val maxBar = trend.maxOfOrNull { it.totalSpent }?.takeIf { it > 0 } ?: 1.0
    val bars = trend.takeLast(6)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "SPENDING OVERVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Insights",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    RangePreset.entries.forEach { preset ->
                        val selected = range == preset
                        Text(
                            preset.label,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color.White else Color.Transparent)
                                .clickable { viewModel.setRange(preset) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightStatCard(
                    label = "Total Spent",
                    value = formatAmount(spent, "PKR"),
                    subtitle = changeLabel,
                    showTrend = change != null && change!! > 0
                )
                InsightStatCard(
                    label = "Orders count",
                    value = "$count Orders",
                    subtitle = "Processed & Tracked"
                )
                InsightStatCard(
                    label = "Average / Order",
                    value = formatAmount(avg, "PKR"),
                    subtitle = "Unit cost efficiency"
                )
            }
        }

        if (bars.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Monthly Spend Trend",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Spend (PKR)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        bars.forEachIndexed { index, bucket ->
                            val fraction = (bucket.totalSpent / maxBar).toFloat().coerceIn(0.06f, 1f)
                            val isLatest = index == bars.lastIndex
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    abbreviateSpend(bucket.totalSpent),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((160 * fraction).dp)
                                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                        .background(
                                            if (isLatest) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    monthLabel(bucket.monthKey),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontSize = 10.sp,
                                    fontWeight = if (isLatest) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isLatest) MaterialTheme.colorScheme.onBackground
                                    else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Top Stores by Spend",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (stores.isEmpty()) {
            item { EmptyState("No spending data for this period.") }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                ) {
                    stores.forEachIndexed { index, summary ->
                        val pct = if (spent > 0) (summary.totalSpent / spent) * 100.0 else 0.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StoreAvatar(summary.store, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    summary.store,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${summary.orderCount} orders",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatAmount(summary.totalSpent, "PKR"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${pct.toInt()}% of total",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        if (index != stores.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    scope.launch {
                        val intent = viewModel.buildCsvShareIntent()
                        context.startActivity(Intent.createChooser(intent, "Export CSV"))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.IosShare, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export CSV (Share)", fontWeight = FontWeight.Bold)
            }
            Text(
                "Made By Muds1r",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun InsightStatCard(
    label: String,
    value: String,
    subtitle: String?,
    showTrend: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Column {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showTrend) {
                        Icon(
                            Icons.Filled.TrendingUp,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private fun abbreviateSpend(amount: Double): String = when {
    amount >= 1_000_000 -> String.format("%.0fM", amount / 1_000_000.0)
    amount >= 1_000 -> String.format("%.0fk", amount / 1_000.0)
    else -> String.format("%.0f", amount)
}
