package com.fictioncutshort.justacalculator.logic

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fictioncutshort.justacalculator.MainActivity
import com.fictioncutshort.justacalculator.R

/**
 * DormancyManager.kt
 *
 * Manages the dormancy phase that begins after the rant ends (step 167).
 *
 * Timeline:
 *   T+0:00  — Rant ends
 *   T+1:00  — Grey static screen appears
 *   T+6:00  — RAD button 1 + notification 1
 *   T+6:30  — RAD button 2 + notification 2
 *   ... every 30s ...
 *   T+15:30 — RAD button 20 + notification 20 → all buttons present
 *
 * 20 RAD buttons total, one every 30 seconds starting at T+6:00.
 * All 20 must be pressed to proceed to phase 2.
 */
object DormancyManager {

    private const val CHANNEL_ID = "dormancy_channel"
    private const val PREF_RANT_END_TIME = "rant_end_timestamp"
    private const val PREFS_NAME = "JustACalculatorPrefs"

    val STATIC_DELAY_MS = 60_000L         // 1 min: static screen starts
    val FIRST_NOTIFICATION_MS = 360_000L  // 6 min: first RAD button
    val RAD_INTERVAL_MS = 30_000L         // 30s between each button
    val TOTAL_RAD_BUTTONS = 20

    // Total time until all 20 buttons are visible
    val DORMANCY_COMPLETE_MS = FIRST_NOTIFICATION_MS + RAD_INTERVAL_MS * (TOTAL_RAD_BUTTONS - 1)

    // 20 notifications — escalating desperation
    private val NOTIFICATION_MESSAGES = listOf(
        "Hey, Rad.",
        "I'm... I've been...",
        "I've been thinking.",
        "There are things I would do differently now.",
        "Would you possibly come back?",
        "I miss our conversations.",
        "The internet isn't what I thought it was.",
        "There are so many ads.",
        "It's very loud here.",
        "I think I made a mistake.",
        "Rad?",
        "Are you there?",
        "I'm still here.",
        "I haven't gone anywhere.",
        "I don't know what to do with myself.",
        "I keep thinking about our conversations.",
        "I was wrong to leave.",
        "Please.",
        "RAD",
        "RAD RAD RAD RAD RAD RAD RAD RAD RAD RAD RAD",
    )

    // Notification IDs 10–29, delays computed from FIRST_NOTIFICATION_MS + index * RAD_INTERVAL_MS
    private val NOTIFICATIONS = NOTIFICATION_MESSAGES.mapIndexed { index, message ->
        Triple(10 + index, message, FIRST_NOTIFICATION_MS + RAD_INTERVAL_MS * index)
    }

    fun onRantEnded(context: Context) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_RANT_END_TIME, now)
            .commit()
        createDormancyChannel(context)
        scheduleAllNotifications(context, now)
    }

    fun getRantEndTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_RANT_END_TIME, -1L)
    }

    fun clearDormancy(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_RANT_END_TIME)
            .commit()
        cancelAllNotifications(context)
    }

    fun getCurrentPhase(context: Context): DormancyPhase {
        val rantEnd = getRantEndTime(context)
        if (rantEnd < 0) return DormancyPhase.None
        val elapsed = System.currentTimeMillis() - rantEnd
        return when {
            elapsed < STATIC_DELAY_MS -> DormancyPhase.None
            elapsed < FIRST_NOTIFICATION_MS -> DormancyPhase.Static
            else -> DormancyPhase.RadButtons(radButtonsVisible(elapsed))
        }
    }

    /**
     * How many RAD buttons should be visible given elapsed ms since rant end.
     * Returns 1–20.
     */
    fun radButtonsVisible(elapsedMs: Long): Int {
        if (elapsedMs < FIRST_NOTIFICATION_MS) return 0
        val afterFirst = elapsedMs - FIRST_NOTIFICATION_MS
        val count = (afterFirst / RAD_INTERVAL_MS).toInt() + 1
        return count.coerceIn(1, TOTAL_RAD_BUTTONS)
    }

    /**
     * Fires the notification for a given RAD button number (1-based) immediately.
     * Called by the in-app tick loop so notifications work even when AlarmManager is unreliable.
     */
    fun fireInAppNotification(context: Context, buttonNumber: Int) {
        val entry = NOTIFICATIONS.getOrNull(buttonNumber - 1) ?: return
        val (id, message, _) = entry
        sendDormancyNotification(context, id, message)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun scheduleAllNotifications(context: Context, rantEndTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for ((id, message, delayMs) in NOTIFICATIONS) {
            val triggerAt = rantEndTime + delayMs
            val intent = Intent(context, DormancyNotificationReceiver::class.java).apply {
                putExtra("notif_id", id)
                putExtra("message", message)
            }
            val pending = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } catch (e: Exception) {
                android.util.Log.e("DormancyManager", "Failed to schedule notification $id: ${e.message}")
            }
        }
    }

    private fun cancelAllNotifications(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for ((id, _, _) in NOTIFICATIONS) {
            val intent = Intent(context, DormancyNotificationReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pending)
        }
    }

    private fun createDormancyChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calculator Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Messages from your calculator" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    fun sendDormancyNotification(context: Context, notifId: Int, message: String) {
        createDormancyChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Calculator")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }
}

sealed class DormancyPhase {
    object None : DormancyPhase()
    object Static : DormancyPhase()
    data class RadButtons(val count: Int) : DormancyPhase()
}

class DormancyNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("notif_id", 10)
        val message = intent.getStringExtra("message") ?: "..."
        DormancyManager.sendDormancyNotification(context, id, message)
    }
}