package com.orderly.app.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import javax.mail.AuthenticationFailedException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val forceFull = inputData.getBoolean(SyncScheduler.KEY_FORCE_FULL, false)
        return try {
            val count = SyncEngine.sync(applicationContext, forceFull)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Not signed in"))
            Result.success(
                workDataOf(KEY_COUNT to count, KEY_AT to System.currentTimeMillis())
            )
        } catch (_: SyncCancelledException) {
            Result.success(workDataOf(KEY_COUNT to 0, KEY_AT to System.currentTimeMillis()))
        } catch (e: AuthenticationFailedException) {
            Result.failure(
                workDataOf(
                    KEY_ERROR to "Gmail rejected the login. Check that the email is correct and " +
                        "that you pasted a valid App Password (not your normal password). " +
                        "Create one at myaccount.google.com/apppasswords."
                )
            )
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Sync failed")))
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Syncing orders")
            .setContentText("Fetching store emails from Gmail…")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_COUNT = "count"
        const val KEY_AT = "at"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "orderly_sync"
        private const val NOTIFICATION_ID = 2001
    }
}
