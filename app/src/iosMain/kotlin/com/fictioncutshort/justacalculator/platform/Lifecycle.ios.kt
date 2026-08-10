package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import platform.AVFAudio.AVAudioSession
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject

/**
 * UIKit posts these as notifications rather than exposing an observable
 * lifecycle. didEnterBackground is the closest analogue to Android's ON_STOP —
 * it fires only on a genuine backgrounding, not when Control Centre or a
 * notification banner briefly covers the app, which is the distinction step 112
 * depends on.
 */
@Composable
actual fun OnAppLifecycleEvent(onEvent: (AppLifecycleEvent) -> Unit) {
    val current by rememberUpdatedState(onEvent)

    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue

        val mapping = listOf(
            UIApplicationWillEnterForegroundNotification to AppLifecycleEvent.STARTED,
            UIApplicationDidBecomeActiveNotification to AppLifecycleEvent.RESUMED,
            UIApplicationWillResignActiveNotification to AppLifecycleEvent.PAUSED,
            UIApplicationDidEnterBackgroundNotification to AppLifecycleEvent.STOPPED,
        )

        val observers = mapping.map { (name, event) ->
            center.addObserverForName(name, null, queue) { _ -> current(event) }
        }

        onDispose { observers.forEach { center.removeObserver(it) } }
    }
}

@Composable
actual fun rememberPlatformPermissionRequest(
    permission: AppPermission,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val current by rememberUpdatedState(onResult)
    return {
        when (permission) {
            AppPermission.CAMERA ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    current(granted)
                }

            AppPermission.MICROPHONE ->
                AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                    current(granted)
                }

            AppPermission.CONTACTS ->
                CNContactStore().requestAccessForEntityType(
                    CNEntityType.CNEntityTypeContacts,
                ) { granted, _ -> current(granted) }

            AppPermission.LOCATION -> {
                // CoreLocation reports through a delegate rather than a
                // callback, so the manager is retained until it answers.
                val manager = CLLocationManager()
                manager.delegate = LocationPermissionDelegate { granted ->
                    current(granted)
                }
                manager.requestWhenInUseAuthorization()
            }
        }
    }
}

private class LocationPermissionDelegate(
    private val onResult: (Boolean) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onResult(hasPermission(IosAppContext, AppPermission.LOCATION))
    }
}

@Composable
actual fun rememberNotificationPermissionRequest(
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val current by rememberUpdatedState(onResult)
    return {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ -> current(granted) }
    }
}
