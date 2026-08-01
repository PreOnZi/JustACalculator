package com.fictioncutshort.justacalculator.logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android-only: AlarmManager wakes this to post a dormancy beat. iOS has no
 * equivalent — UNUserNotificationCenter both schedules and delivers, so the
 * shared DormancyManager never needs a receiver.
 */
class DormancyNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("notif_id", 10)
        val message = intent.getStringExtra("message") ?: "..."
        DormancyManager.postSpaced(context, id, message)
    }
}
