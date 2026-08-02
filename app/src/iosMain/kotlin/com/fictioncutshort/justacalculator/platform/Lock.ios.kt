package com.fictioncutshort.justacalculator.platform

import platform.Foundation.NSLock

actual class PlatformLock actual constructor() {
    private val lock = NSLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
