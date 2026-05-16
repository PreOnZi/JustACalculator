package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.DarkText
import com.fictioncutshort.justacalculator.util.RetroCream
import kotlin.math.PI
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Scale the dial to fit landscape height (~360dp) while staying generous
    // in portrait. Numbers ring, hole positions, and the centre cover all
    // derive from this size so the geometry stays correct at any scale.
    val dialSize = if (isLandscape) 220.dp else 300.dp
    val numberRingRadius = dialSize * 0.333f         // was 100.dp at 300.dp
    val numberCellSize = dialSize * 0.107f           // was 32.dp at 300.dp
    val centerCoverSize = dialSize * 0.433f          // was 130.dp at 300.dp
    val talkButtonSize = dialSize * 0.333f           // was 100.dp at 300.dp

    // Continuous rotation animation when holding
    val infiniteTransition = rememberInfiniteTransition(label = "dial_spin")
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

            // ── Rotary dial ───────────────────────────────────────────────────
            // Numbers are STATIC on the bottom layer.
            // A rotating disc with punched holes sits on top — numbers show only
            // through the holes, which sweep over them as the dial spins.
            Box(
                modifier = Modifier.size(dialSize),
                contentAlignment = Alignment.Center
            ) {
                // ── Layer 1: Static numbers plate (bottom) ────────────────────
                Box(
                    modifier = Modifier
                        .size(dialSize)
                        .clip(CircleShape)
                        .background(Color(0xFFE8E4DA))
                ) {
                    val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                    val halfDial = dialSize / 2
                    val halfCell = numberCellSize / 2
                    numbers.forEachIndexed { index, number ->
                        val angle = (index * 36 - 90) * (PI / 180)
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = numberRingRadius * cos(angle).toFloat() + halfDial - halfCell,
                                    y = numberRingRadius * sin(angle).toFloat() + halfDial - halfCell
                                )
                                .size(numberCellSize),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = number,
                                color = DarkText,
                                fontSize = (numberCellSize.value * 0.5f).sp,
                                fontFamily = CalculatorDisplayFont
                            )
                        }
                    }
                }

                // ── Layer 2: Rotating disc with holes (BlendMode.Clear) ───────
                // The disc covers the numbers plate; holes punch through to show
                // whichever numbers are beneath them at the current rotation angle.
                Canvas(
                    modifier = Modifier
                        .size(dialSize)
                        .rotate(if (isHolding) rotation else 0f)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                ) {
                    // Solid circular disc
                    drawCircle(color = Color(0xFFD8D2C8))

                    // Punch 10 finger holes at the same radius as the numbers
                    val holeRadius = (numberCellSize * 0.6875f).toPx()
                    val ringRadius = numberRingRadius.toPx()
                    for (i in 0..9) {
                        val angle = (i * 36 - 90) * (PI / 180)
                        drawCircle(
                            color = Color.Black,   // colour irrelevant — Clear erases
                            radius = holeRadius,
                            center = Offset(
                                center.x + (ringRadius * cos(angle)).toFloat(),
                                center.y + (ringRadius * sin(angle)).toFloat()
                            ),
                            blendMode = BlendMode.Clear
                        )
                    }
                }

                // ── Layer 3: Static center cover ──────────────────────────────
                // Hides the middle of the rotating disc so only the ring is visible.
                Box(
                    modifier = Modifier
                        .size(centerCoverSize)
                        .clip(CircleShape)
                        .background(Color(0xFFE8E4DA))
                )

                // ── Layer 4: TALK button ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(talkButtonSize)
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
                        fontSize = 20.sp,
                        fontFamily = CalculatorDisplayFont
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
