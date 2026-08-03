package com.fictioncutshort.justacalculator.platform

/**
 * Display names from the device address book, for the in-fiction phone.
 *
 * Returns an empty list when permission has not been granted — callers
 * substitute their own invented names, so the beat still reads as a phone book
 * whether or not the player allowed access. That fallback is the point: the
 * scene works either way, and the creepiness lands harder when the names are
 * real.
 */
expect fun readContactNames(max: Int = 60): List<String>

/** A contact as the in-fiction phonebook shows it. */
data class ContactEntry(val name: String, val number: String)

/**
 * Names *and* numbers, for the phonebook screen. Empty when permission is
 * refused; the caller substitutes its own invented entries.
 */
expect fun readContacts(max: Int = 80): List<ContactEntry>
