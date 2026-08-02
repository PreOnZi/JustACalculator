package com.fictioncutshort.justacalculator.platform

import android.provider.ContactsContract

actual fun readContactNames(max: Int): List<String> {
    if (!hasPermission(AppInit.context, AppPermission.CONTACTS)) return emptyList()
    val out = LinkedHashSet<String>()
    runCatching {
        AppInit.context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
            null, null,
            ContactsContract.Contacts.DISPLAY_NAME + " ASC",
        )?.use { c ->
            val col = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            while (c.moveToNext() && out.size < max) {
                c.getString(col)?.takeIf { it.isNotBlank() }?.let(out::add)
            }
        }
    }
    return out.toList()
}
