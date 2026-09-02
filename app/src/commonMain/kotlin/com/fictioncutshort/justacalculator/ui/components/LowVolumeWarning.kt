package com.fictioncutshort.justacalculator.ui.components

import com.fictioncutshort.justacalculator.platform.AudioOutput
import com.fictioncutshort.justacalculator.platform.currentAppContext
import com.fictioncutshort.justacalculator.platform.openPrefs
import com.fictioncutshort.justacalculator.util.PREFS_NAME
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Low-volume warning.
//
// The whole game is narrated, so a muted / very-quiet device means the player
// silently misses the story. This overlay watches the media-stream volume and, if
// it's below a route-dependent threshold (Bluetooth headphones read quieter at the
// same fraction than a phone speaker, so they get a higher bar), shows a dismissible
// banner. Purely observational: it never changes the volume itself.
//
// Dismissing it is a statement, not a snooze.
//
// The system volume fraction cannot tell us how loud a player actually is: a
// Bluetooth speaker has its own amplifier and its own knob, so a phone sitting
// at a third of full scale may be filling a room. Nothing the platform exposes
// closes that gap. So the banner asks once, and "GOT IT" is taken to mean *this
// level, on this output, is fine* — recorded and remembered across launches
// rather than re-armed on a timer, which is what had it reappearing every minute
// on a perfectly loud speaker.
//
// It speaks up again only when something has actually changed for the worse:
// the player turns the volume down [ACCEPT_MARGIN] below what they accepted, or
// the audio moves to a different kind of output, where the old judgement says
// nothing about the new one. A genuine drop to silence is still caught, which is
// the case that matters.
// ─────────────────────────────────────────────────────────────────────────────

private const val SPEAKER_MIN_FRAC    = 0.22f
private const val BLUETOOTH_MIN_FRAC  = 0.40f

/** How far below an accepted level counts as the player turning it down again. */
private const val ACCEPT_MARGIN = 0.08f

private const val PREF_ACCEPTED_WIRED    = "volume_ok_frac_wired"
private const val PREF_ACCEPTED_WIRELESS = "volume_ok_frac_wireless"

/** No level accepted yet on this route. */
private const val NOT_ACCEPTED = -1f

/**
 * The level below which the banner is warranted, given the route and whatever
 * the player has already accepted on it.
 *
 * Accepting a level does not switch the warning off for good — it lowers the bar
 * to just under that level, so turning the volume down from there still gets
 * caught. Accepting something already under the bar leaves nothing to warn about
 * on this route, which is the player's call to make.
 */
internal fun warnBelow(baseThreshold: Float, acceptedFrac: Float): Float =
    if (acceptedFrac < 0f) baseThreshold
    else minOf(baseThreshold, acceptedFrac - ACCEPT_MARGIN)

@Composable
fun LowVolumeWarning() {
    val context = currentAppContext()
    val prefs = remember { context.openPrefs(PREFS_NAME) }

    var show by remember { mutableStateOf(false) }
    // The level the player last said was fine, per route. Survives restarts —
    // having to dismiss this again on every launch is the same nag by another name.
    var acceptedWired by remember {
        mutableStateOf(prefs.getFloat(PREF_ACCEPTED_WIRED, NOT_ACCEPTED))
    }
    var acceptedWireless by remember {
        mutableStateOf(prefs.getFloat(PREF_ACCEPTED_WIRELESS, NOT_ACCEPTED))
    }
    // Held so the dismiss handler records the route and level being accepted.
    var wireless by remember { mutableStateOf(false) }
    var frac by remember { mutableStateOf(1f) }

    LaunchedEffect(acceptedWired, acceptedWireless) {
        while (true) {
            frac = AudioOutput.volumeFraction()
            wireless = AudioOutput.isWireless()
            // Bluetooth reads quieter at the same fraction, so it gets a higher bar.
            val base = if (wireless) BLUETOOTH_MIN_FRAC else SPEAKER_MIN_FRAC
            val accepted = if (wireless) acceptedWireless else acceptedWired
            show = frac < warnBelow(base, accepted)
            delay(1000)
        }
    }

    if (show) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .padding(top = 44.dp)
                    .fillMaxWidth()
                    .background(Color(0xF21A1205), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFFFAA33), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Your volume is low",
                    color = Color(0xFFFFCC44), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "You must listen to what I've got to say!",
                    color = Color(0xFFECECEC), fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .background(Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            show = false
                            // Record what was accepted, for this route only.
                            if (wireless) {
                                acceptedWireless = frac
                                prefs.edit().putFloat(PREF_ACCEPTED_WIRELESS, frac).apply()
                            } else {
                                acceptedWired = frac
                                prefs.edit().putFloat(PREF_ACCEPTED_WIRED, frac).apply()
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text("[ GOT IT ]", color = Color(0xFFCCCCCC), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
