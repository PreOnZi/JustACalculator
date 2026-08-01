package com.fictioncutshort.justacalculator.platform

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.stringWithFormat
import platform.Foundation.timeIntervalSince1970
import kotlin.math.roundToLong

actual fun nowMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).roundToLong()

// NSString format specifiers match Java's for %e and %f, so the calculator
// display renders identically on both platforms. stringWithFormat is not
// locale-aware by default, which matches the Locale.ROOT used on Android.
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun formatScientific(value: Double, digits: Int): String =
    NSString.stringWithFormat("%.${digits}e", value)

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun formatFixed(value: Double, digits: Int): String =
    NSString.stringWithFormat("%.${digits}f", value)

actual fun logDebug(tag: String, message: String) {
    NSLog("%s: %s", tag, message)
}

actual fun logWarn(tag: String, message: String) {
    NSLog("%s: WARN %s", tag, message)
}

actual fun currentTimeOfDay(): TimeOfDay {
    val components = NSCalendar.currentCalendar.components(
        NSCalendarUnitHour or NSCalendarUnitMinute,
        fromDate = NSDate(),
    )
    return TimeOfDay(hour = components.hour.toInt(), minute = components.minute.toInt())
}
