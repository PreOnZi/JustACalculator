package com.fictioncutshort.justacalculator.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fictioncutshort.justacalculator.platform.LocalNotifications

/**
 * Android-only: AlarmManager wakes this to post the "repair complete" beat.
 * Registered in AndroidManifest.xml. iOS needs no equivalent —
 * UNUserNotificationCenter delivers scheduled notifications itself.
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("notif_id", 1)
        val message = intent.getStringExtra("message")
            ?: "Hey, Rad, I'm pretty sure I got it. Please click here to check!"
        LocalNotifications.postNow(context, id, message)
    }
}
