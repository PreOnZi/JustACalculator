package com.fictioncutshort.justacalculator.platform

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fictioncutshort.justacalculator.MainActivity
import com.fictioncutshort.justacalculator.R
import com.fictioncutshort.justacalculator.logic.DormancyNotificationReceiver

private const val CHANNEL_ID = "dormancy_channel"

actual object LocalNotifications {

    actual fun prepare(context: AppContext) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calculator Updates",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Messages from your calculator" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    actual fun isPermitted(context: AppContext): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    actual fun postNow(context: AppContext, id: Int, message: String) {
        prepare(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Calculator")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (isPermitted(context)) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    /**
     * Exact where the OS allows it. `setAndAllowWhileIdle` is *inexact*: the
     * platform batches it with other pending alarms, which is how twenty beats
     * spaced 30s apart once arrived as a single clump minutes late.
     */
    actual fun scheduleAt(context: AppContext, id: Int, message: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingBroadcast(context, id, message)
        val canBeExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
        try {
            if (canBeExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (e: Exception) {
            // SecurityException if the exact-alarm grant was revoked between the
            // check and the call — an inexact alarm still beats none.
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } catch (e2: Exception) {
                logWarn("LocalNotifications", "Failed to schedule $id: ${e2.message}")
            }
        }
    }

    actual fun cancel(context: AppContext, ids: List<Int>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ids.forEach { alarmManager.cancel(pendingBroadcast(context, it, null)) }
    }

    private fun pendingBroadcast(context: AppContext, id: Int, message: String?): PendingIntent {
        val intent = Intent(context, DormancyNotificationReceiver::class.java).apply {
            putExtra("notif_id", id)
            if (message != null) putExtra("message", message)
        }
        return PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
