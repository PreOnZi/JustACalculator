package com.fictioncutshort.justacalculator.ui.screens

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.data.CHAPTERS
import com.fictioncutshort.justacalculator.data.Chapter
import com.fictioncutshort.justacalculator.logic.ComplicityStore
import com.fictioncutshort.justacalculator.logic.EndingStore
import com.fictioncutshort.justacalculator.logic.Currency
import com.fictioncutshort.justacalculator.logic.CurrencyStore

/**
 * CityDebugMenu.kt
 *
 * The developer menu, and the password gate in front of it.
 *
 * The gate exists because the debug menu is reachable during the FIRST PHASE —
 * the calculator itself, which is the shipped experience — via the mute button.
 * A player tapping around must not fall into it, so entry costs a passcode.
 * The same gate guards the city's joystick shortcut (5 taps).
 *
 * The menu reports the story's real persisted state: for each of the nine
 * buildings, whether it has been entered and whether it has been COMPLETED
 * (those are different — walking through a door marks a building "entered" and
 * darkens its windows, but only finishing its game completes it and unlocks the
 * next door in the entry order), plus every city-wide change that hangs off
 * those completions.
 *
 * All state lives in the "calc_city" SharedPreferences. Writing here and calling
 * back through onApply lets the live city re-read it without a restart.
 */

/** The passcode. Deliberately not a round number, so it can't be stumbled into. */
private const val DEBUG_PASSCODE = "17102020"

/** Entry order the city enforces — shown so the gating is legible while testing. */
private val DEBUG_ENTRY_ORDER = listOf(1, 7, 4, 3, 6, 2, 5, 8, 9)

/** What each building changes about the city when it is completed. */
private val BUILDING_EFFECTS = mapOf(
    1 to "tower defence - lays bridge piece, door turns green",
    2 to "maze - lays bridge piece, door turns green",
    3 to "phone sim - PERMANENT NIGHT + monster wakes, bridge piece",
    4 to "surveillance room - bridge piece",
    5 to "sound mosaic - bridge piece, lights B8 entrance, seeds lottery",
    6 to "3D crowd runner - bridge piece",
    7 to "vanity face filter - no bridge piece",
    8 to "casino - bridge piece, currencies drained to zero",
    9 to "flappy - final bridge piece",
)

@Composable
fun DebugPasswordGate(onUnlock: () -> Unit, onCancel: () -> Unit) {
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // Raise the system keyboard as soon as the gate appears.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* swallow taps on the scrim */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(Color(0xFF101010), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF33FF66), RoundedCornerShape(6.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                ">>> RESTRICTED <<<",
                color = Color(0xFF33FF66),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Passcode required",
                color = Color(0xFF999999),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = entry,
                onValueChange = { entry = it.filter { c -> c.isDigit() }; wrong = false },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF33FF66),
                    unfocusedTextColor = Color(0xFF33FF66),
                    focusedContainerColor = Color(0xFF0D1A0D),
                    unfocusedContainerColor = Color(0xFF0D1A0D),
                    focusedIndicatorColor = if (wrong) Color(0xFFFF4444) else Color(0xFF33FF66),
                    unfocusedIndicatorColor = if (wrong) Color(0xFFFF4444) else Color(0xFF226633),
                    cursorColor = Color(0xFF33FF66),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )

            if (wrong) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Incorrect.",
                    color = Color(0xFFFF4444),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DebugButton(
                    label = "CLOSE",
                    bg = Color(0xFF2A2A2A),
                    modifier = Modifier.weight(1f),
                    onClick = onCancel
                )
                DebugButton(
                    label = "ENTER",
                    bg = Color(0xFF1E5E33),
                    modifier = Modifier.weight(1f),
                    onClick = { if (entry == DEBUG_PASSCODE) onUnlock() else wrong = true }
                )
            }
        }
    }
}

/**
 * @param prefs        the "calc_city" store — the single source of truth for the city
 * @param onApply      called after any write, so the live city re-reads its state
 * @param onJumpToPhase1 hands a story chapter back up to the calculator so the menu
 *                     can leave the city and drop straight into phase 1. Null when
 *                     no phase-1 host is wired (the jump section is then hidden).
 */
@Composable
fun CityDebugMenu(
    prefs: SharedPreferences,
    onApply: () -> Unit,
    onClose: () -> Unit,
    onJumpToPhase1: ((Chapter) -> Unit)? = null,
) {
    val context = LocalContext.current
    // Bumped after every write to force this panel to re-read the prefs.
    var rev by remember { mutableIntStateOf(0) }

    fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        rev++
        onApply()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .background(Color(0xFF0C0C0C), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF33FF66), RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CITY DEBUG",
                    color = Color(0xFF33FF66),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    Modifier
                        .clickable { onClose() }
                        .padding(4.dp)
                ) {
                    Text(
                        "[X]",
                        color = Color(0xFF33FF66),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // rev is read here so every write re-runs this block.
                @Suppress("UNUSED_EXPRESSION") rev

                // ── Leave the city and jump into phase 1 (the calculator) ─────────
                // Only shown when a phase-1 host wired onJumpToPhase1. Each button
                // tears the city down and lands at that chapter's start step.
                if (onJumpToPhase1 != null) {
                    DebugSectionLabel("PHASE 1  (leave city → jump to chapter)")
                    for (chapter in CHAPTERS) {
                        Phase1ChapterRow(
                            chapter = chapter,
                            onClick = { onJumpToPhase1(chapter) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                DebugSectionLabel("BUILDINGS  (entry order: ${DEBUG_ENTRY_ORDER.joinToString(" > ")})")

                for ((i, digit) in DEBUG_ENTRY_ORDER.withIndex()) {
                    val entered = prefs.getBoolean("building_done_$digit", false)
                    val completed = prefs.getBoolean("completed_$digit", false)
                    // A door only opens when everything before it in the chain is done.
                    val locked = DEBUG_ENTRY_ORDER.take(i).any {
                        !prefs.getBoolean("completed_$it", false)
                    }
                    BuildingRow(
                        digit = digit,
                        step = i + 1,
                        entered = entered,
                        completed = completed,
                        locked = locked,
                        effect = BUILDING_EFFECTS[digit] ?: "",
                        onToggleComplete = {
                            val now = !completed
                            write {
                                putBoolean("completed_$digit", now)
                                // Keep the legacy per-building flags in step, since the
                                // city still reads them for night, glow and door colour.
                                when (digit) {
                                    1 -> putBoolean("td_b1_done", now)
                                    3 -> putBoolean("td_b3_done", now)
                                    4 -> putBoolean("td_b4_done", now)
                                    5 -> putBoolean("td_b5_done", now)
                                    6 -> putBoolean("td_b6_done", now)
                                }
                                // Completing implies having been inside.
                                if (now) putBoolean("building_done_$digit", true)
                            }
                        },
                        onToggleEntered = {
                            write { putBoolean("building_done_$digit", !entered) }
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))
                DebugSectionLabel("CITY STATE")

                val pieces = prefs.getInt("bridge_pieces", 0)
                DebugCounterRow(
                    label = "bridge pieces",
                    value = "$pieces / 9",
                    note = if (pieces >= 9) "bridge complete - crossable" else "one per completed building",
                    onMinus = { write { putInt("bridge_pieces", (pieces - 1).coerceAtLeast(0)) } },
                    onPlus = { write { putInt("bridge_pieces", (pieces + 1).coerceAtMost(9)) } },
                )

                DebugToggleRow(
                    label = "permanent night",
                    on = prefs.getBoolean("td_b3_done", false),
                    note = "set by Building 3; also wakes the monster",
                    onToggle = {
                        val now = !prefs.getBoolean("td_b3_done", false)
                        write {
                            putBoolean("td_b3_done", now)
                            putBoolean("completed_3", now)
                            putBoolean("b3_night_active", now)
                        }
                    }
                )
                DebugToggleRow(
                    label = "bridge crossed",
                    on = prefs.getBoolean("bridge_crossed", false),
                    note = "one-way latch: city sealed, monster guards the deck",
                    onToggle = {
                        val now = !prefs.getBoolean("bridge_crossed", false)
                        write { putBoolean("bridge_crossed", now) }
                    }
                )
                DebugToggleRow(
                    label = "building 10 door open",
                    on = prefs.getBoolean("b10_door_open", false),
                    note = "the mute button; unlocked by the rating slider",
                    onToggle = {
                        val now = !prefs.getBoolean("b10_door_open", false)
                        write { putBoolean("b10_door_open", now) }
                    }
                )
                DebugToggleRow(
                    label = "B8 entrance glow",
                    on = prefs.getBoolean("td_b5_done", false),
                    note = "RGB frame on Building 8's door (set by Building 5)",
                    onToggle = {
                        val now = !prefs.getBoolean("td_b5_done", false)
                        write {
                            putBoolean("td_b5_done", now)
                            putBoolean("completed_5", now)
                        }
                    }
                )
                DebugToggleRow(
                    label = "lottery popup shown",
                    on = CurrencyStore.lotteryShown(context),
                    note = "the coins draw seeded after Building 5",
                    onToggle = {
                        val now = !CurrencyStore.lotteryShown(context)
                        write { putBoolean("b8_lottery_shown", now) }
                    }
                )
                DebugToggleRow(
                    label = "intro seen",
                    on = prefs.getBoolean("intro_done", false),
                    note = "skips the aerial fly-in on entry",
                    onToggle = {
                        val now = !prefs.getBoolean("intro_done", false)
                        write { putBoolean("intro_done", now) }
                    }
                )

                Spacer(Modifier.height(12.dp))
                DebugSectionLabel("ENDING  (complicity)")
                Text(
                    ComplicityStore.summary(context),
                    color = if (ComplicityStore.isComplicitEnding(context)) Color(0xFFFF9944)
                            else Color(0xFF33FF66),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                // What will ACTUALLY play. Once an ending has been played it is
                // recorded, and the recording wins over anything the inputs above now
                // say — so show it, rather than leaving it to be discovered.
                val frozen = remember(rev) { EndingStore.chosen(context) }
                val plays = remember(rev) { EndingStore.predict(context) }
                Text(
                    if (frozen != null) "plays: $frozen  (FROZEN from a previous run)"
                    else "plays: $plays",
                    color = if (frozen != null) Color(0xFFFF5555) else Color(0xFF888888),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Both of these also UNFREEZE the ending. Without that they only
                    // move the complicity inputs, and an ending that has already been
                    // played once is recorded — so the story would go on delivering
                    // the ending from the previous run no matter what is forced here.
                    DebugButton(
                        label = "FORCE REFUSED",
                        bg = Color(0xFF1E5E33),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ComplicityStore.forceEnding(context, complicit = false)
                            EndingStore.unfreeze(context)
                            rev++; onApply()
                        }
                    )
                    DebugButton(
                        label = "FORCE COMPLICIT",
                        bg = Color(0xFF8A5A1E),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ComplicityStore.forceEnding(context, complicit = true)
                            EndingStore.unfreeze(context)
                            rev++; onApply()
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))
                DebugSectionLabel("CURRENCIES")
                for (c in Currency.entries.sortedBy { it.order }) {
                    val bal = CurrencyStore.balance(context, c)
                    DebugCounterRow(
                        label = c.name.lowercase(),
                        value = "$bal",
                        note = "",
                        onMinus = {
                            CurrencyStore.setBalance(context, c, (bal - 10).coerceAtLeast(0))
                            rev++; onApply()
                        },
                        onPlus = {
                            CurrencyStore.setBalance(context, c, bal + 10)
                            rev++; onApply()
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))
                DebugSectionLabel("BULK")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DebugButton(
                        label = "COMPLETE ALL",
                        bg = Color(0xFF1E5E33),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            write {
                                for (d in 1..9) {
                                    putBoolean("completed_$d", true)
                                    putBoolean("building_done_$d", true)
                                }
                                putBoolean("td_b1_done", true); putBoolean("td_b3_done", true)
                                putBoolean("td_b4_done", true); putBoolean("td_b5_done", true)
                                putBoolean("td_b6_done", true); putBoolean("b3_night_active", true)
                                putInt("bridge_pieces", 9)
                                putInt("entry_progress", 9)
                            }
                        }
                    )
                    DebugButton(
                        label = "RESET CITY",
                        bg = Color(0xFF7A2222),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // Wipes the whole city store: buildings, bridge, night,
                            // currencies, lottery, Building 10. The calculator's own
                            // story progress lives in a different store and survives.
                            write { clear() }
                            // The ending lives in this store too, but it also keeps
                            // state in memory, which clear() cannot reach.
                            EndingStore.unfreeze(context)
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            DebugButton(
                label = "CLOSE",
                bg = Color(0xFF2A2A2A),
                modifier = Modifier.fillMaxWidth(),
                onClick = onClose
            )
        }
    }
}

/** One tappable chapter in the PHASE 1 jump list — name on top, step+blurb under. */
@Composable
private fun Phase1ChapterRow(
    chapter: Chapter,
    onClick: () -> Unit,
) {
    // The D1/D2/D3 specials use negative sentinel steps; show them as "—".
    val stepLabel = if (chapter.startStep >= 0) "step ${chapter.startStep}" else "special"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color(0xFF141414), RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF2E4633), RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Text(
            chapter.name,
            color = Color(0xFF9FE8B0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "$stepLabel — ${chapter.description}",
            color = Color(0xFF6C8C74),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun DebugSectionLabel(text: String) {
    Text(
        text,
        color = Color(0xFF55AA55),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun BuildingRow(
    digit: Int,
    step: Int,
    entered: Boolean,
    completed: Boolean,
    locked: Boolean,
    effect: String,
    onToggleComplete: () -> Unit,
    onToggleEntered: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color(0xFF141414), RoundedCornerShape(3.dp))
            .border(
                1.dp,
                when {
                    completed -> Color(0xFF33FF66)
                    locked -> Color(0xFF553333)
                    else -> Color(0xFF333333)
                },
                RoundedCornerShape(3.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$step. BLDG $digit",
                color = if (locked) Color(0xFF886666) else Color(0xFFCCCCCC),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(92.dp)
            )
            Text(
                when {
                    completed -> "DONE"
                    locked -> "LOCKED"
                    entered -> "ENTERED"
                    else -> "OPEN"
                },
                color = when {
                    completed -> Color(0xFF33FF66)
                    locked -> Color(0xFFFF6666)
                    entered -> Color(0xFFFFCC44)
                    else -> Color(0xFF888888)
                },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            DebugButton(
                label = if (completed) "UNDO" else "DONE",
                bg = if (completed) Color(0xFF3A3A3A) else Color(0xFF1E5E33),
                modifier = Modifier.width(74.dp),
                onClick = onToggleComplete
            )
            Spacer(Modifier.width(6.dp))
            DebugButton(
                label = if (entered) "-IN" else "+IN",
                bg = Color(0xFF2A2A2A),
                modifier = Modifier.width(56.dp),
                onClick = onToggleEntered
            )
        }
        if (effect.isNotEmpty()) {
            Text(
                effect,
                color = Color(0xFF777777),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DebugToggleRow(
    label: String,
    on: Boolean,
    note: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = if (on) Color(0xFF33FF66) else Color(0xFFAAAAAA),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            if (note.isNotEmpty()) {
                Text(
                    note,
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        DebugButton(
            label = if (on) "ON" else "OFF",
            bg = if (on) Color(0xFF1E5E33) else Color(0xFF2A2A2A),
            modifier = Modifier.width(64.dp),
            onClick = onToggle
        )
    }
}

@Composable
private fun DebugCounterRow(
    label: String,
    value: String,
    note: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            if (note.isNotEmpty()) {
                Text(
                    note,
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        DebugButton("-", Color(0xFF2A2A2A), Modifier.width(42.dp), onMinus)
        Text(
            value,
            color = Color(0xFF33FF66),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .width(58.dp)
                .padding(horizontal = 6.dp)
        )
        DebugButton("+", Color(0xFF2A2A2A), Modifier.width(42.dp), onPlus)
    }
}

@Composable
private fun DebugButton(
    label: String,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
