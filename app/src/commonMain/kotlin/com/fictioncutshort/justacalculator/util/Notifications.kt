package com.fictioncutshort.justacalculator.util

import com.fictioncutshort.justacalculator.platform.AppContext
import com.fictioncutshort.justacalculator.platform.LocalNotifications
import com.fictioncutshort.justacalculator.platform.nowMillis

/**
 * Notifications.kt
 *
 * The "repair complete" beat: after the whack-a-mole repair the calculator goes
 * quiet, then nudges the player back with a notification. The delay is the
 * point — it fires while the app is backgrounded — so it is a scheduled
 * notification rather than an in-app message.
 */

/** Distinct from the dormancy ids (10–29), which are scheduled separately. */
private const val READY_NOTIFICATION_ID = 1

private const val READY_MESSAGE =
    "Hey, Rad, I'm pretty sure I got it. Please click here to check!"

/**
 * Arms the "repair complete" notification [delayMs] from now.
 */
fun scheduleNotification(context: AppContext, delayMs: Long = 5000) {
    LocalNotifications.prepare(context)
    LocalNotifications.scheduleAt(
        context,
        READY_NOTIFICATION_ID,
        READY_MESSAGE,
        nowMillis() + delayMs,
    )
}
