package com.fictioncutshort.justacalculator.platform

import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType

/**
 * iOS splits authorisation across three frameworks. Anything short of an
 * explicit grant — including "not determined" — reads as not granted, which is
 * how the Android side behaves before the first prompt.
 */
actual fun hasPermission(context: AppContext, permission: AppPermission): Boolean =
    when (permission) {
        AppPermission.MICROPHONE ->
            AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) ==
                AVAuthorizationStatusAuthorized

        AppPermission.CAMERA ->
            AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
                AVAuthorizationStatusAuthorized

        AppPermission.LOCATION -> {
            val status = CLLocationManager().authorizationStatus
            status == kCLAuthorizationStatusAuthorizedWhenInUse ||
                status == kCLAuthorizationStatusAuthorizedAlways
        }

        AppPermission.CONTACTS ->
            CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts) ==
                CNAuthorizationStatusAuthorized
    }
