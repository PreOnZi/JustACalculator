package com.fictioncutshort.justacalculator.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** A single contact (label + number). */
data class PhonebookContact(val name: String, val number: String)

private val FALLBACK_CONTACTS = listOf(
    PhonebookContact("Apple CEO",     "+1 408 555 0100"),
    PhonebookContact("Alphabet CEO",  "+1 650 555 0100"),
    PhonebookContact("Amazon CEO",    "+1 206 555 0100"),
    PhonebookContact("Brazil CEO",    "+55 61 555 0100"),
    PhonebookContact("Meta CEO",      "+1 650 555 0200"),
    PhonebookContact("Cleaning Lady", "+1 555 0199")
)

/**
 * Lists the user's real contacts (READ_CONTACTS permission is already granted
 * earlier in the story). If permission is missing or the contact list is
 * empty, falls back to a static list of well-known names — punchier as a gag
 * than an empty screen.
 *
 * Tapping a contact opens the keypad app pre-filled with their number via
 * [onContactCall].
 */
@Composable
fun PhonePhonebookApp(
    onClose: () -> Unit,
    onContactCall: (PhonebookContact) -> Unit
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<PhonebookContact>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contacts = loadContactsOrFallback(context)
        loaded = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 18.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("Contacts", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }

            Spacer(Modifier.height(12.dp))

            if (!loaded) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(contacts) { c ->
                        ContactRow(contact = c, onClick = { onContactCall(c) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: PhonebookContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A3A3A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                contact.number,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

private fun loadContactsOrFallback(context: Context): List<PhonebookContact> {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    if (!granted) return FALLBACK_CONTACTS

    val out = mutableListOf<PhonebookContact>()
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )
    cursor?.use {
        val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        if (nameCol >= 0 && numCol >= 0) {
            while (it.moveToNext()) {
                val name = it.getString(nameCol) ?: continue
                val number = it.getString(numCol) ?: continue
                out += PhonebookContact(name, number)
            }
        }
    }
    return if (out.isEmpty()) FALLBACK_CONTACTS else out
}
