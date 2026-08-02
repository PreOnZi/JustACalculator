package com.fictioncutshort.justacalculator.platform

import platform.Contacts.CNContactFormatter
import platform.Contacts.CNContactFormatterStyle
import platform.Contacts.CNContactStore
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContact
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
