package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.RetroCream
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PhoneOverlay(
    message: String,
    onButtonHoldStart: () -> Unit,
    onButtonHoldEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHolding by remember { mutableStateOf(false) }

    // Continuous rotation animation when holding
    val infiniteTransition = rememberInfiniteTransition(label = "dial_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a1a))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Message display area at top (same as TalkOverlay)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color(0xFF2a2a2a), shape = RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = message,
                    color = RetroCream,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Rotary dial with talk button in center
            Box(
                modifier = Modifier.size(300.dp),
                contentAlignment = Alignment.Center
            ) {
                // Add space at top to push message down
                Spacer(modifier = Modifier.height(80.dp))
                // Outer dial ring (rotates when holding)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(if (isHolding) rotation else 0f)
                        .clip(CircleShape)
                        .background(Color(0xFF3a3a3a))
                ) {
                    // Numbers around the outer edge
                    val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                    numbers.forEachIndexed { index, number ->
                        val angle = (index * 36 - 90) * (Math.PI / 180)
                        val radius = 125.dp

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = radius * cos(angle).toFloat() + 150.dp - 15.dp,
                                    y = radius * sin(angle).toFloat() + 150.dp - 15.dp
                                )
                                .size(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = number,
                                color = RetroCream,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Finger holes ring (also rotates)
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .rotate(if (isHolding) rotation else 0f)
                        .clip(CircleShape)
                        .background(Color(0xFF2a2a2a))
                ) {
                    // Draw finger holes
                    for (i in 0..9) {
                        val angle = (i * 36 - 90) * (Math.PI / 180)
                        val radius = 75.dp

                        Box(
                            modifier = Modifier
                                .offset(
                                    x = radius * cos(angle).toFloat() + 110.dp - 18.dp,
                                    y = radius * sin(angle).toFloat() + 110.dp - 18.dp
                                )
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1a1a1a))
                        )
                    }
                }

                // Inner static circle (doesn't rotate)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2a2a2a))
                )

                // Center "Talk" button
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(if (isHolding) AccentOrange else Color(0xFF4a4a4a))
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
                        color = if (isHolding) Color.Black else RetroCream,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}