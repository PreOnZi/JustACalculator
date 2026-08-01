package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.DarkText
import com.fictioncutshort.justacalculator.util.RetroCream

@Composable
fun TalkOverlay(
    message: String,
    onButtonHoldStart: () -> Unit,
    onButtonHoldEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHolding by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RetroCream)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Message — no background box, calculator digital font
            Text(
                text = message,
                color = DarkText,
                fontSize = 18.sp,
                fontFamily = CalculatorDisplayFont,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Round "Talk" button — orange like mute button
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(if (isHolding) Color(0xFFBF6010) else AccentOrange)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                onButtonHoldStart()
                                tryAwaitRelease()
                                isHolding = false
                                onButtonHoldEnd()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TALK",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = CalculatorDisplayFont
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
