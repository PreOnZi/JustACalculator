package com.fictioncutshort.justacalculator.platform

/** Android draws into framebuffer 0, so the off-screen pass restores cleanly. */
actual val supportsOffscreenFeeds: Boolean = true
