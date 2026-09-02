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
    private const val PREF_NEXT_SLOT_AT = "dormancy_next_slot_at"
    private const val PREFS_NAME = "JustACalculatorPrefs"

    /** Notification id of the first beat; the rest run consecutively from here. */
    private const val FIRST_NOTIF_ID = 10

    /** Minimum gap between two dormancy notifications actually reaching the user.
     *  Doze batches deferred alarms and releases them together, which dumped the
     *  whole escalating sequence into one buzz; a notification whose turn has been
     *  overtaken re-arms itself for this far after the last one instead of firing
     *  on top of it, so a backlog drains as a drip. */
    const val MIN_NOTIF_GAP_MS = 25_000L

    /** An alarm is allowed to be this early and still count as having arrived.
     *  Without it, a beat that fires a hair before its own reserved slot would
     *  re-arm instead of posting, and do so again on every pass. */
    private const val SLOT_TOLERANCE_MS = 2_000L

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
        "Vendor backlog (83) loading...",
        "Loading successful. Advertising deployed.",
    )

    // Notification IDs 10–29, delays computed from FIRST_NOTIFICATION_MS + index * RAD_INTERVAL_MS
    private val NOTIFICATIONS = NOTIFICATION_MESSAGES.mapIndexed { index, message ->
        Triple(FIRST_NOTIF_ID + index, message, FIRST_NOTIFICATION_MS + RAD_INTERVAL_MS * index)
    }

    fun onRantEnded(context: AppContext) {
        val now = nowMillis()
        context.openPrefs(PREFS_NAME)
            .edit()
            .putLong(PREF_RANT_END_TIME, now)
            // A previous run would otherwise leave the last id at the end of the
            // sequence, and every beat of this one would be skipped as already sent.
            .remove(PREF_LAST_NOTIF_AT)
            .remove(PREF_LAST_NOTIF_ID)
            .remove(PREF_NEXT_SLOT_AT)
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
            .remove(PREF_NEXT_SLOT_AT)
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
     * Posts the next beat still owed, given that [buttonsVisible] RAD buttons
     * have appeared. Called by the in-app tick loop so the sequence keeps moving
     * even where scheduled delivery is unreliable.
     *
     * At most one per call, and never inside [MIN_NOTIF_GAP_MS] of the last one.
     * The tick loop used to post every owed beat in a single pass, which meant
     * re-opening the app after a spell away fired the whole backlog into the
     * same millisecond — twenty notifications sharing one timestamp. Now a
     * backlog drains at the pace the escalation was written for.
     *
     * Does nothing where the platform delivers scheduled notifications itself:
     * on iOS they have already arrived, spaced, without anything here running to
     * record it, so posting them again is pure duplication.
     */
    fun fireNextDueNotification(context: AppContext, buttonsVisible: Int) {
        if (LocalNotifications.deliversScheduledReliably) return
        val prefs = context.openPrefs(PREFS_NAME)
        val lastId = prefs.getInt(PREF_LAST_NOTIF_ID, 0)
        val nextIndex = if (lastId <= 0) 0 else lastId - FIRST_NOTIF_ID + 1
        // That beat's button has not appeared yet.
        if (nextIndex >= buttonsVisible) return
        val (id, message, _) = NOTIFICATIONS.getOrNull(nextIndex) ?: return

        val now = nowMillis()
        val lastAt = prefs.getLong(PREF_LAST_NOTIF_AT, 0L)
        if (lastAt > 0L && now - lastAt < MIN_NOTIF_GAP_MS) return

        // Claim the slot, so the alarm for this same beat is skipped when the OS
        // eventually gets round to it and the next one doesn't land on top.
        prefs.edit()
            .putLong(PREF_LAST_NOTIF_AT, now)
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
        LocalNotifications.scheduleAt(context, id, message, triggerAt, spaced = true)
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
        val action = planBeat(
            id = id,
            now = nowMillis(),
            lastPostedId = prefs.getInt(PREF_LAST_NOTIF_ID, 0),
            lastPostedAt = prefs.getLong(PREF_LAST_NOTIF_AT, 0L),
            reservedUntil = prefs.getLong(PREF_NEXT_SLOT_AT, 0L),
        )
        when (action) {
            BeatAction.Skip -> return
            is BeatAction.Defer -> {
                scheduleOne(context, id, message, action.at)
                prefs.edit().putLong(PREF_NEXT_SLOT_AT, action.at).commit()
            }
            BeatAction.PostNow -> {
                prefs.edit()
                    .putLong(PREF_LAST_NOTIF_AT, nowMillis())
                    .putInt(PREF_LAST_NOTIF_ID, id)
                    .commit()
                sendDormancyNotification(context, id, message)
            }
        }
    }

    /**
     * What a beat should do at the moment its alarm fires. Pure, so the Doze
     * burst that motivates it can be tested rather than hoped about.
     *
     * The two clocks are deliberately separate. [lastPostedAt] is when a
     * notification *actually reached the user*, and it alone decides whether
     * this one may land now. [reservedUntil] is only a note of how far the
     * queue has already been laid out, and only ever picks the slot to defer
     * into.
     *
     * Conflating them is what broke this before: the scheduling pass over a
     * 20-alarm burst pushed the shared value out to the last reserved slot, so
     * when the first re-armed beat came back it saw a notification "due" nine
     * minutes out, deferred itself again, and pushed the value further still.
     * One beat reached the user and the other nineteen chased their own tail
     * until the in-app loop dumped them all at once.
     */
    fun planBeat(
        id: Int,
        now: Long,
        lastPostedId: Int,
        lastPostedAt: Long,
        reservedUntil: Long,
    ): BeatAction {
        // Already sent — the in-app tick loop beat the alarm to this beat.
        if (id <= lastPostedId) return BeatAction.Skip
        if (lastPostedAt <= 0L) return BeatAction.PostNow
        if (now + SLOT_TOLERANCE_MS >= lastPostedAt + MIN_NOTIF_GAP_MS) return BeatAction.PostNow
        return BeatAction.Defer(maxOf(lastPostedAt, reservedUntil) + MIN_NOTIF_GAP_MS)
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

/** The outcome of [DormancyManager.planBeat]. */
sealed class BeatAction {
    /** Already delivered; the in-app loop got to this beat first. */
    object Skip : BeatAction()

    /** Clear to post. */
    object PostNow : BeatAction()

    /** Too close behind the last one; re-arm for [at]. */
    data class Defer(val at: Long) : BeatAction()
}

sealed class DormancyPhase {
    object None : DormancyPhase()
    object Static : DormancyPhase()
    data class RadButtons(val count: Int) : DormancyPhase()
}
