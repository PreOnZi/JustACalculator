package com.fictioncutshort.justacalculator.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.R
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.RetroCream

/**
 * BeepCheckScreen.kt
 *
 * The gate between the pexeso game and the city intro, styled to match the
 * calculator UI (RetroCream ground, AccentOrange, the LCD display font). Loops
 * the monster's beep (R.raw.beep) and asks the user to confirm they can hear it.
 *
 *  - NO  → show a "turn it up / check Bluetooth" hint and let them try again.
 *  - YES → stop the beep and hand off ([onConfirmed]) to the intro narration.
 */
@Composable
fun BeepCheckScreen(onConfirmed: () -> Unit) {
    val context = LocalContext.current
    var declined by remember { mutableStateOf(false) }

    // Loop the beep for as long as this screen is shown.
    DisposableEffect(Unit) {
        val beep = MediaPlayer.create(context, R.raw.beep)?.apply {
            isLooping = true
            runCatching { start() }
        }
        onDispose {
            beep?.let { runCatching { it.stop() }; runCatching { it.release() } }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCream),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = "Can you hear the beep?",
                color = AccentOrange,
                fontSize = 26.sp,
                fontFamily = CalculatorDisplayFont,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            Row(horizontalArrangement = Arrangement.Center) {
                Button(
                    onClick = onConfirmed,
                    modifier = Modifier.width(120.dp).height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        "YES",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalculatorDisplayFont
                    )
                }

                Spacer(Modifier.width(24.dp))

                Button(
                    onClick = { declined = true },
                    modifier = Modifier.width(120.dp).height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B6B6B),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        "NO",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CalculatorDisplayFont
                    )
                }
            }

            if (declined) {
                Spacer(Modifier.height(36.dp))
                Text(
                    text = "Please increase the volume and check your connected Bluetooth devices",
                    color = Color(0xFF2D2D2D),
                    fontSize = 15.sp,
                    fontFamily = CalculatorDisplayFont,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
