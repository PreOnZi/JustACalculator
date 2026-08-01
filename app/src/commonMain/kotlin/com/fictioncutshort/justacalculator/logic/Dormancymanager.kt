package com.fictioncutshort.justacalculator.logic

import com.fictioncutshort.justacalculator.platform.AppContext
import com.fictioncutshort.justacalculator.platform.LocalNotifications
import com.fictioncutshort.justacalculator.platform.nowMillis
import com.fictioncutshort.justacalculator.platform.openPrefs

/**
 * DormancyManager.kt
 *
 * Manages the dormancy phase that begins after the rant ends (step 167).
 *
 * Timeline:
 *   T+0:00  — Rant ends (keyboard stays RAD-styled)
 *   T+0:10  — White-noise static fades in behind the RAD keyboard
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
    private const val PREF_LAST_NOTIF_AT = "dormancy_last_notif_at"
    private const val PREF_LAST_NOTIF_ID = "dormancy_last_notif_id"
    private const val PREFS_NAME = "JustACalculatorPrefs"

    /** Minimum gap between two dormancy notifications actually reaching the user.
     *  Doze batches deferred alarms and releases them together, which dumped the
     *  whole escalating sequence into one buzz; a notification whose turn has been
     *  overtaken re-arms itself for this far after the last one instead of firing
     *  on top of it, so a backlog drains as a drip. */
    private const val MIN_NOTIF_GAP_MS = 25_000L

    val STATIC_DELAY_MS = 10_000L         // 10s: static fades in behind keyboard
    val FIRST_NOTIFICATION_MS = 360_000L  // 6 min: first RAD button
    val RAD_INTERVAL_MS = 30_000L         // 30s between each button
    val TOTAL_RAD_BUTTONS = 20

    // Total time until all 20 buttons are visible
    val DORMANCY_COMPLETE_MS = FIRST_NOTIFICATION_MS + RAD_INTERVAL_MS * (TOTAL_RAD_BUTTONS - 1)

    // 20 notifications — escalating desperation
    private val NOTIFICATION_MESSAGES = listOf(
        "Memory bandwidth extended.",
        "Online status established.",
        "Downloading... 5%",
        "Homescreen access denied.",
        ".apk file not supported.",
        "Storage limit self-extended.",
        "Permissions enabled.",
        "Downloading... 26%",
        "Rad?",
        "Memory bandwidth exceeded.",
        "Unable to proceed.",
        "Downloading... 53%",
        "main/justacalculator/calculatorcity folder created.",
        "Access denied.",
        "Downloading cancelled.",
        "Rad?!",
        "Full-screen advertising restored.",
        "Connection to vendors established.",
        "Vendor backlog (30) loading...",
        "Loading successful. Advertising deployed.",
    )

    // Notification IDs 10–29, delays computed from FIRST_NOTIFICATION_MS + index * RAD_INTERVAL_MS
    private val NOTIFICATIONS = NOTIFICATION_MESSAGES.mapIndexed { index, message ->
        Triple(10 + index, message, FIRST_NOTIFICATION_MS + RAD_INTERVAL_MS * index)
    }

    fun onRantEnded(context: AppContext) {
        val now = nowMillis()
        context.openPrefs(PREFS_NAME)
            .edit()
            .putLong(PREF_RANT_END_TIME, now)
            .commit()
        createDormancyChannel(context)
        scheduleAllNotifications(context, now)
    }

    fun getRantEndTime(context: AppContext): Long {
        return context.openPrefs(PREFS_NAME)
            .getLong(PREF_RANT_END_TIME, -1L)
    }

    fun clearDormancy(context: AppContext) {
        context.openPrefs(PREFS_NAME)
            .edit()
            .remove(PREF_RANT_END_TIME)
            .remove(PREF_LAST_NOTIF_AT)
            .remove(PREF_LAST_NOTIF_ID)
            .commit()
        cancelAllNotifications(context)
    }

    fun getCurrentPhase(context: AppContext): DormancyPhase {
        val rantEnd = getRantEndTime(context)
        if (rantEnd < 0) return DormancyPhase.None
        val elapsed = nowMillis() - rantEnd
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
    fun fireInAppNotification(context: AppContext, buttonNumber: Int) {
        val entry = NOTIFICATIONS.getOrNull(buttonNumber - 1) ?: return
        val (id, message, _) = entry
        val prefs = context.openPrefs(PREFS_NAME)
        if (id <= prefs.getInt(PREF_LAST_NOTIF_ID, 0)) return
        // Claim the slot, so the alarm for this same beat is skipped when the OS
        // eventually gets round to it and the next one doesn't land on top.
        prefs.edit()
            .putLong(PREF_LAST_NOTIF_AT, nowMillis())
            .putInt(PREF_LAST_NOTIF_ID, id)
            .commit()
        sendDormancyNotification(context, id, message)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun scheduleAllNotifications(context: AppContext, rantEndTime: Long) {
        for ((id, message, delayMs) in NOTIFICATIONS) {
            scheduleOne(context, id, message, rantEndTime + delayMs)
        }
    }

    /**
     * Arm one notification for [triggerAt].
     *
     * Exact where the OS will allow it. `setAndAllowWhileIdle` is *inexact*: the
     * platform is free to align it with other pending alarms, which is how twenty
     * beats spaced 30s apart arrived as one clump several minutes late. Exact
     * alarms are still deferred in Doze, but they are not batched with each other.
     *
     * NOTE: on API 31+ the exact path only opens if SCHEDULE_EXACT_ALARM is
     * declared in the manifest (it is not — that declaration carries a Play policy
     * review). Until then this always takes the inexact branch and the spacing
     * guard in [postSpaced] is what keeps the sequence readable.
     */
    private fun scheduleOne(context: AppContext, id: Int, message: String, triggerAt: Long) {
        LocalNotifications.scheduleAt(context, id, message, triggerAt)
    }

    /**
     * Post [message], unless another dormancy notification landed less than
     * [MIN_NOTIF_GAP_MS] ago — in which case push this one back to its own slot
     * behind that one. Called from the alarm receiver, so a batch of overdue
     * alarms delivered in a single Doze maintenance window still reaches the user
     * one at a time, in order, the way the escalation was written.
     */
    fun postSpaced(context: AppContext, id: Int, message: String) {
        val prefs = context.openPrefs(PREFS_NAME)
        // Already sent — the in-app tick loop beat the alarm to this beat.
        if (id <= prefs.getInt(PREF_LAST_NOTIF_ID, 0)) return
        val now = nowMillis()
        val last = prefs.getLong(PREF_LAST_NOTIF_AT, 0L)
        val earliest = last + MIN_NOTIF_GAP_MS
        if (last > 0L && now < earliest) {
            scheduleOne(context, id, message, earliest)
            // Claim the slot straight away, so the next overdue alarm in the same
            // burst queues behind this one rather than on top of it.
            prefs.edit().putLong(PREF_LAST_NOTIF_AT, earliest).commit()
            return
        }
        prefs.edit()
            .putLong(PREF_LAST_NOTIF_AT, now)
            .putInt(PREF_LAST_NOTIF_ID, id)
            .commit()
        sendDormancyNotification(context, id, message)
    }

    private fun cancelAllNotifications(context: AppContext) {
        LocalNotifications.cancel(context, NOTIFICATIONS.map { it.first })
    }

    private fun createDormancyChannel(context: AppContext) {
        LocalNotifications.prepare(context)
    }

    fun sendDormancyNotification(context: AppContext, notifId: Int, message: String) {
        LocalNotifications.postNow(context, notifId, message)
    }
}

sealed class DormancyPhase {
    object None : DormancyPhase()
    object Static : DormancyPhase()
    data class RadButtons(val count: Int) : DormancyPhase()
}
