package com.fictioncutshort.justacalculator.platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/**
 * UNUserNotificationCenter both schedules and delivers, so there is no
 * BroadcastReceiver equivalent — a time-interval trigger replaces the whole
 * AlarmManager round trip.
 */
actual object LocalNotifications {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    // Cached because isPermitted must answer synchronously, while iOS only
    // reports authorisation through an async callback.
    private var authorized = false

    actual fun prepare(context: AppContext) {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            authorized = granted
        }
        center.getNotificationSettingsWithCompletionHandler { settings ->
            authorized = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
        }
    }

    actual fun isPermitted(context: AppContext): Boolean = authorized

    // The trigger lives in UNUserNotificationCenter and fires on its own.
    actual val deliversScheduledReliably: Boolean = true

    actual fun postNow(context: AppContext, id: Int, message: String) {
        // A zero interval is rejected, so "now" is the smallest allowed delay.
        submit(id, message, delaySeconds = 0.1)
    }

    actual fun scheduleAt(
        context: AppContext,
        id: Int,
        message: String,
        triggerAtMillis: Long,
        spaced: Boolean,
    ) {
        val nowMs = NSDate().timeIntervalSince1970 * 1000.0
        val delaySeconds = ((triggerAtMillis - nowMs) / 1000.0).coerceAtLeast(0.1)
        submit(id, message, delaySeconds)
    }

    actual fun cancel(context: AppContext, ids: List<Int>) {
        center.removePendingNotificationRequestsWithIdentifiers(ids.map { it.toString() })
    }

    private fun submit(id: Int, message: String, delaySeconds: Double) {
        val content = UNMutableNotificationContent().apply {
            setTitle("Calculator")
            setBody(message)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = delaySeconds,
            repeats = false,
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = id.toString(),
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request, null)
    }
}
