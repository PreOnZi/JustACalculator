package com.fictioncutshort.justacalculator.ui.components

import com.fictioncutshort.justacalculator.platform.AppContext
import com.fictioncutshort.justacalculator.platform.currentAppContext
import com.fictioncutshort.justacalculator.platform.readContacts
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single contact (label + number). [isAd] rows are the salted-in advertisers. */
data class PhonebookContact(val name: String, val number: String, val isAd: Boolean = false)

private val FALLBACK_CONTACTS = listOf(
    PhonebookContact("Apple CEO",     "+1 408 555 0100"),
    PhonebookContact("Alphabet CEO",  "+1 650 555 0100"),
    PhonebookContact("Amazon CEO",    "+1 206 555 0100"),
    PhonebookContact("Brazil CEO",    "+55 61 555 0100"),
    PhonebookContact("Meta CEO",      "+1 650 555 0200"),
    PhonebookContact("Cleaning Lady", "+1 555 0199")
)

/** Advertisers spread evenly through the phonebook — the gag only lands with the
 *  player's own people either side of them. */
private val AD_CONTACTS = listOf(
    PhonebookContact("The Insurance Lawyer",      "+44 20 7946 0100", isAd = true),
    PhonebookContact("The Teeth Doctor",          "+44 20 7946 0211", isAd = true),
    PhonebookContact("The Best Car Recovery",     "+44 20 7946 0322", isAd = true),
    PhonebookContact("Cheapest Locksmith In Town","+44 20 7946 0433", isAd = true),
    PhonebookContact("Cash-For-Phones (24h)",     "+44 20 7946 0544", isAd = true),
    PhonebookContact("Solar Panels — FREE quote", "+44 20 7946 0655", isAd = true),
)

private val AD_REPLIES = listOf(
    "Thank you for your interest, I will be in touch.",
    "Your number has been added to the database, thank you!",
    "We'll contact you shortly!",
    "We are happy to see you interested, we will contact you soon!"
)

/**
 * Lists the user's real contacts (READ_CONTACTS permission is already granted
 * at step 1077, before this overlay opens) with advertisers salted in between
 * them. If permission is missing or the device has no contacts, falls back to a
 * static list of well-known names — punchier as a gag than an empty screen.
 *
 * Tapping a real contact opens the keypad app pre-filled with their number via
 * [onContactCall]; tapping an advertiser just harvests your number.
 */
@Composable
fun PhonePhonebookApp(
    onClose: () -> Unit,
    onContactCall: (PhonebookContact) -> Unit
) {
    val context = currentAppContext()
    var contacts by remember { mutableStateOf<List<PhonebookContact>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var adReply by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // The content-resolver query is disk-backed — never on the frame thread.
        contacts = withContext(Dispatchers.Default) { withAdsInterleaved(loadContactsOrFallback(context)) }
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
                        ContactRow(
                            contact = c,
                            onClick = {
                                if (c.isAd) adReply = AD_REPLIES.random() else onContactCall(c)
                            }
                        )
                    }
                }
            }
        }

        adReply?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable { adReply = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1C))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(msg, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF3A3A3A))
                            .clickable { adReply = null }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text("OK", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
        if (contact.isAd) {
            Text(
                "AD",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Spread the advertisers evenly through [real] so they read as ordinary rows
 *  you scroll past, not as a block at the end. */
private fun withAdsInterleaved(real: List<PhonebookContact>): List<PhonebookContact> {
    val out = real.toMutableList()
    for ((i, ad) in AD_CONTACTS.withIndex()) {
        val pos = ((i + 1) * (out.size + 1) / (AD_CONTACTS.size + 1)).coerceIn(0, out.size)
        out.add(pos, ad)
    }
    return out
}

/**
 * Real contacts off the device, name-sorted and de-duplicated.
 *
 * Two queries, because one is not enough in practice: the Phone table only holds
 * people who have a number saved, and on a device whose contacts all come from an
 * account without numbers it comes back empty. When that happens we fall back to
 * the Contacts table (the same one Building 6 reads, which is why the runner's
 * helper names work where this screen didn't) and show the entry without a number.
 */
private fun loadContactsOrFallback(context: AppContext): List<PhonebookContact> {
    val entries = readContacts(80)
    // Falls back to invented contacts when access was refused, so the phonebook
    // still reads as a phonebook.
    return if (entries.isEmpty()) FALLBACK_CONTACTS
    else entries.map { PhonebookContact(it.name, it.number.ifBlank { "no number saved" }) }
}
