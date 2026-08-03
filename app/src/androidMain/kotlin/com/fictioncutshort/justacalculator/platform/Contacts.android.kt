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

actual fun readContacts(max: Int): List<ContactEntry> {
    if (!hasPermission(AppInit.context, AppPermission.CONTACTS)) return emptyList()
    // A contact can hold several numbers; the first per name is enough here.
    val out = LinkedHashMap<String, ContactEntry>()
    runCatching {
        AppInit.context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
        )?.use { c ->
            val nameCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameCol >= 0 && numCol >= 0) {
                while (c.moveToNext() && out.size < max) {
                    val name = c.getString(nameCol)?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    out.getOrPut(name) { ContactEntry(name, c.getString(numCol)?.trim().orEmpty()) }
                }
            }
        }
    }
    return out.values.toList()
}
