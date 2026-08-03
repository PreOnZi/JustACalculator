package com.fictioncutshort.justacalculator.platform

import platform.Contacts.CNContactFormatter
import platform.Contacts.CNContactFormatterStyle
import platform.Contacts.CNContactStore
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContact
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value

@OptIn(ExperimentalForeignApi::class)
actual fun readContactNames(max: Int): List<String> {
    if (!hasPermission(IosAppContext, AppPermission.CONTACTS)) return emptyList()
    val out = LinkedHashSet<String>()
    runCatching {
        // CNContactFormatter decides which keys it needs, which varies by
        // locale — asking it rather than hardcoding given/family name is what
        // makes non-Western name orders come out right.
        val keys = listOf(CNContactFormatter.descriptorForRequiredKeysForStyle(
            CNContactFormatterStyle.CNContactFormatterStyleFullName,
        ))
        val request = CNContactFetchRequest(keysToFetch = keys)
        CNContactStore().enumerateContactsWithFetchRequest(request, null) { contact, stop ->
            val c = contact as? CNContact
            if (c != null) {
                CNContactFormatter.stringFromContact(c, CNContactFormatterStyle.CNContactFormatterStyleFullName)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(out::add)
            }
            if (out.size >= max) stop?.pointed?.value = true
        }
    }
    return out.sorted()
}

@OptIn(ExperimentalForeignApi::class)
actual fun readContacts(max: Int): List<ContactEntry> {
    if (!hasPermission(IosAppContext, AppPermission.CONTACTS)) return emptyList()
    val out = LinkedHashMap<String, ContactEntry>()
    runCatching {
        val keys = listOf(
            CNContactFormatter.descriptorForRequiredKeysForStyle(
                CNContactFormatterStyle.CNContactFormatterStyleFullName,
            ),
            CNContactPhoneNumbersKey,
        )
        val request = CNContactFetchRequest(keysToFetch = keys)
        CNContactStore().enumerateContactsWithFetchRequest(request, null) { contact, stop ->
            val c = contact as? CNContact
            val name = c?.let {
                CNContactFormatter.stringFromContact(it, CNContactFormatterStyle.CNContactFormatterStyleFullName)
            }?.trim()
            if (c != null && !name.isNullOrBlank()) {
                val number = (c.phoneNumbers.firstOrNull() as? CNLabeledValue)
                    ?.let { (it.value as? CNPhoneNumber)?.stringValue }
                    .orEmpty()
                out.getOrPut(name) { ContactEntry(name, number) }
            }
            if (out.size >= max) stop?.pointed?.value = true
        }
    }
    return out.values.sortedBy { it.name }
}
