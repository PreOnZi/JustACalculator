package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.logic.EndingStore
import kotlinx.coroutines.delay

/**
 * EndingOverlay.kt
 *
 * The black screen between the city falling down and the calculator's last word.
 *
 * Two of the three endings pass through here. The compliant and explorer endings
 * ask the player for their real name — the calculator has called them "Rad" all
 * game, a name it chose for them, and this is the one time it asks for the truth.
 * The resistance ending skips this screen entirely: it never earned the name, and
 * it says so by not asking.
 */
@Composable
fun EndingBlackScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    // 0,1 = the two slides; 2 = the name wheels.
    var slide by remember { mutableIntStateOf(0) }
    var fade by remember { mutableStateOf(0f) }

    LaunchedEffect(slide) {
        fade = 0f
        repeat(20) { fade = (it + 1) / 20f; delay(28) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (slide) {
            0, 1 -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Tap anywhere to go on — there is no calculator to press yet.
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { slide++ },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = EndingStore.NAME_INTRO[slide],
                        color = Color(0xFFE8E4DA).copy(alpha = fade),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    Text(
                        text = "[ tap ]",
                        color = Color(0xFF555555).copy(alpha = fade),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            else -> EndingNameEntry(
                prompt = EndingStore.NAME_INTRO[1],
                onConfirm = { name ->
                    EndingStore.setName(context, name)
                    EndingStore.line = 0
                    EndingStore.phase = EndingStore.Phase.DIALOGUE
                    onDone()
                }
            )
        }
    }
}
