package com.fictioncutshort.justacalculator.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fictioncutshort.justacalculator.MainActivity

/**
 * Notifications.kt
 *
 * Handles push notifications for the "repair complete" feature.
 * After the whack-a-mole mini-game, the calculator asks the user to close
 * the app and wait for a notification saying the repair is done.
 */

private const val CHANNEL_ID = "calculator_channel"
private const val NOTIFICATION_ID = 1

/**
 * Creates the notification channel required for Android 8.0+.
 * Must be called before sending any notifications.
 */
fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Calculator Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications from your calculator"
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

/**
 * Schedules a notification to appear after a delay.
 * Uses AlarmManager for reliable delivery even when app is closed.
 *
 * @param context Android context
 * @param delayMs Delay in milliseconds before notification appears (default 5 seconds)
 */
fun scheduleNotification(context: Context, delayMs: Long = 5000) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, NotificationReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerTime = System.currentTimeMillis() + delayMs

    try {
        // Android 12+ requires checking permission for exact alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                // Fall back to inexact alarm if exact not allowed
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    } catch (_: Exception) {
        // Last resort fallback - use Handler (only works while app is alive)
        Handler(Looper.getMainLooper()).postDelayed({
            sendReadyNotification(context)
        }, delayMs)
    }
}

/**
 * BroadcastReceiver that handles the alarm trigger.
 * Registered in AndroidManifest.xml.
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        sendReadyNotification(context)
    }
}

/**
 * Sends the "repair complete" notification that opens the app when tapped.
 */
fun sendReadyNotification(context: Context) {
    createNotificationChannel(context)

    // Intent to open app when notification is tapped
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Calculator")
        .setContentText("Hey Rad, I'm pretty sure I got it. Please click here to check!")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)  // Dismiss when tapped
        .build()

    // Check permission before sending (required on Android 13+)
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}