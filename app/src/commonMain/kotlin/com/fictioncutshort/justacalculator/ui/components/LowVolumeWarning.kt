package com.fictioncutshort.justacalculator.ui.components

import com.fictioncutshort.justacalculator.platform.nowMillis
import com.fictioncutshort.justacalculator.platform.AudioOutput
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
// banner. It re-arms after RE_ARM_MS so it can nag again if the player turns the
// volume back down later — which they might do at any point (e.g. the tower-defense
// sound effects). Purely observational: it never changes the volume itself.
// ─────────────────────────────────────────────────────────────────────────────

private const val SPEAKER_MIN_FRAC   = 0.22f
private const val BLUETOOTH_MIN_FRAC  = 0.40f
private const val RE_ARM_MS           = 60_000L

@Composable
fun LowVolumeWarning() {

    var show by remember { mutableStateOf(false) }
    // Time we last dismissed; suppresses re-showing until RE_ARM_MS has passed.
    var mutedUntil by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val frac = AudioOutput.volumeFraction()
            // Bluetooth reads quieter at the same fraction, so it gets a higher bar.
            val low = frac < (if (AudioOutput.isWireless()) BLUETOOTH_MIN_FRAC else SPEAKER_MIN_FRAC)
            when {
                !low            -> show = false
                low && nowMillis() >= mutedUntil -> show = true
            }
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
                            mutedUntil = nowMillis() + RE_ARM_MS
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
