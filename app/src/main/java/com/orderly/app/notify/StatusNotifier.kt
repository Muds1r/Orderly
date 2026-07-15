package com.orderly.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.orderly.app.MainActivity
import com.orderly.app.R
import com.orderly.app.data.db.OrderEntity
import com.orderly.app.data.db.OrderStatus
import com.orderly.app.ui.statusLabel

/** Posts a local notification when an order status meaningfully advances. */
object StatusNotifier {

    private const val CHANNEL_ID = "orderly_status"
    private const val CHANNEL_NAME = "Order updates"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shipping status changes for your orders"
        }
        mgr.createNotificationChannel(channel)
    }

    fun notifyIfChanged(
        context: Context,
        order: OrderEntity,
        previous: OrderStatus,
        next: OrderStatus
    ) {
        if (previous == next) return
        if (!shouldNotify(previous, next)) return
        ensureChannel(context)

        val title = order.store
        val body = buildString {
            append(statusLabel(next))
            order.productSummary?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it.take(40))
            }
            order.lastLocation?.let {
                append(" · ")
                append(it)
            }
        }

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_order_id", order.id)
        }
        val pending = PendingIntent.getActivity(
            context,
            order.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(order.id.hashCode() and 0x7FFFFFFF, notification)
        }
    }

    private fun shouldNotify(previous: OrderStatus, next: OrderStatus): Boolean {
        val interesting = setOf(
            OrderStatus.SHIPPED,
            OrderStatus.IN_TRANSIT,
            OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED,
            OrderStatus.DELAYED,
            OrderStatus.RETURNED
        )
        return next in interesting && previous != next
    }
}
