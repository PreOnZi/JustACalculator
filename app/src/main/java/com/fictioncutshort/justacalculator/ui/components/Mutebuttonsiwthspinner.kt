package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.vibrate
import kotlinx.coroutines.delay

/**
 * MuteButtonWithSpinner.kt
 *
 * The orange circular button in the top-right corner that toggles
 * conversation mode on/off.
 *
 * Features:
 * - Filled circle (●) when conversation is active
 * - Empty circle (○) when muted
 * - Spinning dashed border when auto-progressing (waiting for next message)
 *
 * Hidden features (via rapid clicking):
 * - 5 clicks in 2 seconds: Opens debug menu
 * - 10 clicks in 2 seconds: Resets game
 */

/**
 * Conversation toggle button with auto-progress spinner.
 *
 * @param isMuted True when conversation is turned off
 * @param isAutoProgressing True when calculator is typing or about to show next message
 * @param onClick Called when button is tapped (handles single tap and rapid-tap detection)
 * @param modifier Modifier for positioning
 */
@Composable
fun MuteButtonWithSpinner(
    isMuted: Boolean,
    isAutoProgressing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Debounced spinner state - prevents flickering during rapid state changes
    var shouldSpin by remember { mutableStateOf(isAutoProgressing) }
    val currentAutoProgressing by rememberUpdatedState(isAutoProgressing)

    LaunchedEffect(isAutoProgressing) {
        if (isAutoProgressing) {
            // Start spinning immediately when auto-progressing
            shouldSpin = true
        } else {
            // Grace period before stopping - prevents flicker between messages
            delay(500)
            if (!currentAutoProgressing) {
                shouldSpin = false
            }
        }
    }

    // Continuous rotation animation for the spinner
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        // Spinning dashed border (only visible when auto-progressing)
        if (shouldSpin) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = rotation }
            ) {
                val radius = size.minDimension / 2 - 2.dp.toPx()
                val dashCount = 12
                val dashAngle = 360f / dashCount
                val dashSweep = dashAngle * 0.6f

                // Draw 12 dashed segments around the circle
                for (i in 0 until dashCount) {
                    drawArc(
                        Color(0xFF8B0000),
                        startAngle = i * dashAngle,
                        sweepAngle = dashSweep,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        size = Size(radius * 2, radius * 2)
                    )
                }
            }
        }

        // Main circular button
        Button(
            onClick = {
                vibrate(context, 10, 30)
                onClick()
            },
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentOrange,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(0.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = if (isMuted) "○" else "●",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}