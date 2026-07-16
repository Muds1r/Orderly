package com.orderly.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.orderly.app.data.db.OrderEntity
import com.orderly.app.ui.statusLabel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OrderShare {

    fun shareText(order: OrderEntity): String = buildString {
        append(order.store)
        order.orderNumber?.let { append(" #").append(it) }
        append('\n')
        append(order.productSummary ?: order.subject)
        append('\n')
        append("Status: ").append(statusLabel(order.status))
        order.amount?.let {
            append('\n')
            append("Amount: ").append(it)
            order.currency?.let { c -> append(' ').append(c) }
        }
        order.trackingNumber?.let {
            append('\n')
            append("Tracking: ").append(it)
            order.carrier?.let { c -> append(" (").append(c).append(')') }
        }
        order.lastLocation?.let {
            append('\n')
            append("Location: ").append(it)
        }
    }

    fun shareIntent(order: OrderEntity): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${order.store} order")
            putExtra(Intent.EXTRA_TEXT, shareText(order))
        }

    fun exportCsv(context: Context, orders: List<OrderEntity>): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "orderly-export-$stamp.csv")
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        file.bufferedWriter().use { out ->
            out.appendLine(
                "store,orderNumber,product,status,amount,currency,tracking,carrier,location,orderDate"
            )
            orders.forEach { o ->
                out.appendLine(
                    listOf(
                        o.store,
                        o.orderNumber.orEmpty(),
                        o.productSummary ?: o.subject,
                        statusLabel(o.status),
                        o.amount?.toString().orEmpty(),
                        o.currency.orEmpty(),
                        o.trackingNumber.orEmpty(),
                        o.carrier.orEmpty(),
                        o.lastLocation.orEmpty(),
                        df.format(Date(o.orderDate))
                    ).joinToString(",") { csvEscape(it) }
                )
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun csvShareIntent(context: Context, orders: List<OrderEntity>): Intent {
        val uri = exportCsv(context, orders)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Orderly export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun csvEscape(value: String): String {
        val v = value.replace("\"", "\"\"")
        return if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"$v\"" else v
    }
}
