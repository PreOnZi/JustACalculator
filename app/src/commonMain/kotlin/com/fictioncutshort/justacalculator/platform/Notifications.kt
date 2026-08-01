package com.fictioncutshort.justacalculator.platform

/**
 * Local (not push) notifications — the dormancy phase posts twenty of them on a
 * timer after the rant ends, and that escalation is a story beat rather than a
 * convenience feature, so the timing has to survive the port.
 *
 * Android schedules via AlarmManager + a BroadcastReceiver that posts a
 * NotificationCompat; iOS hands UNUserNotificationCenter a time-interval
 * trigger, which does the waiting and the posting in one step.
 */
expect object LocalNotifications {

    /**
     * Prepare the delivery channel. Creates the NotificationChannel on Android;
     * on iOS requests authorisation, which must happen before anything can be
     * scheduled.
     */
    fun prepare(context: AppContext)

    /** Whether the user has allowed notifications. */
    fun isPermitted(context: AppContext): Boolean

    /** Post [message] immediately under [id]. */
    fun postNow(context: AppContext, id: Int, message: String)

    /**
     * Arm [message] to fire at absolute wall-clock [triggerAtMillis].
     *
     * [spaced] opts the notification into the dormancy sequence's minimum-gap
     * guard. Only the dormancy beats want it: that guard drops any id at or
     * below the last one delivered, so a low-id one-off like the repair-complete
     * beat would be silently swallowed once dormancy has run. Ignored on iOS,
     * which does not batch scheduled notifications.
     */
    fun scheduleAt(
        context: AppContext,
        id: Int,
        message: String,
        triggerAtMillis: Long,
        spaced: Boolean = false,
    )

    /** Cancel every pending notification in [ids] that has not fired yet. */
    fun cancel(context: AppContext, ids: List<Int>)
}
