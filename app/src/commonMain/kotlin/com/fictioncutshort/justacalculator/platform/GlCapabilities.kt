package com.fictioncutshort.justacalculator.platform

/**
 * Whether this platform can render the Calculator City CCTV feeds.
 *
 * The desk in Building 10 re-renders the whole city into an off-screen
 * framebuffer, once per monitor. On iOS that pass leaves the view flickering:
 * the drawable there lives on a framebuffer the app does not own, and handing
 * it back after an off-screen pass has not proved reliable — attempts to
 * restore either the hard-coded default or the previously-bound target both
 * misbehaved on device. Rather than keep guessing at it blind, iOS skips the
 * feeds entirely and the monitors read as switched off, which is a perfectly
 * good look for a derelict city.
 *
 * Android is unaffected and keeps the live feeds.
 */
expect val supportsOffscreenFeeds: Boolean
