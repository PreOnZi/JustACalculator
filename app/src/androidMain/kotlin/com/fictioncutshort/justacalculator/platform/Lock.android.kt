package com.fictioncutshort.justacalculator.platform

actual class PlatformLock actual constructor() {
    private val monitor = Any()
    actual fun <T> withLock(block: () -> T): T = synchronized(monitor) { block() }
}
