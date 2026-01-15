package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.data.ChaosKey
import com.fictioncutshort.justacalculator.util.Point3D
import com.fictioncutshort.justacalculator.util.project
import com.fictioncutshort.justacalculator.util.rotateX
import com.fictioncutshort.justacalculator.util.rotateY
import kotlin.math.roundToInt

/**
 * KeyboardChaos3DView.kt
 *
 * Renders the 3D floating letter cubes during the keyboard chaos mini-game.
 *
 * The calculator "accidentally" tries to add a full keyboard and creates
 * floating letter cubes that the player must tap to dismiss.
 *
 * Features:
 * - Perspective projection (letters further away appear smaller)
 * - Drag to rotate the view
 * - Tap letters to remove them
 * - Each letter has its own rotation and size
 */

/**
 * 3D view of floating letter cubes.
 *
 * @param chaosLetters List of letters to display
 * @param rotationX View tilt (up/down)
 * @param rotationY View spin (left/right)
 * @param scale Zoom level
 * @param onLetterTap Called when a letter is tapped
 * @param onRotationChange Called when user drags to rotate view
 * @param modifier Modifier for sizing
 */
@Composable
fun KeyboardChaos3DView(
    chaosLetters: List<ChaosKey>,
    rotationX: Float,
    rotationY: Float,
    scale: Float,
    onLetterTap: (String) -> Unit,
    onRotationChange: (deltaX: Float, deltaY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Calculate screen center - capture density values here in composable context
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val centerX = screenWidthPx / 2
    val centerY = screenHeightPx / 2

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onRotationChange(dragAmount.x * 0.5f, dragAmount.y * 0.5f)
                }
            }
    ) {
        // Sort letters by Z depth (render far letters first)
        val sortedLetters = remember(chaosLetters, rotationX, rotationY) {
            chaosLetters.sortedByDescending { letter ->
                // Transform the letter position and get final Z for sorting
                val point = Point3D(letter.x * 0.6f, letter.y * 0.6f, letter.z * 0.6f)
                val rotatedY = rotateY(point, rotationY)
                val rotatedXY = rotateX(rotatedY, rotationX)
                rotatedXY.z
            }
        }

        // Render each letter cube - pass density as parameter
        sortedLetters.forEach { letter ->
            ChaosLetterCube(
                letter = letter,
                rotationX = rotationX,
                rotationY = rotationY,
                scale = scale,
                centerX = centerX,
                centerY = centerY,
                density = density,  // Pass density here
                onTap = { onLetterTap(letter.letter) }
            )
        }

        // Instructions text
        Text(
            text = "Tap the letters to dismiss them!",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 50.dp)
        )

        // Letter count
        Text(
            text = "${chaosLetters.size} letters remaining",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-100).dp)
        )
    }
}

/**
 * A single floating letter cube.
 */
@Composable
private fun ChaosLetterCube(
    letter: ChaosKey,
    rotationX: Float,
    rotationY: Float,
    scale: Float,
    centerX: Float,
    centerY: Float,
    density: Density,  // Receive density as parameter
    onTap: () -> Unit
) {
    // Transform letter position through view rotation
    val lx = letter.x * 0.6f
    val ly = letter.y * 0.6f
    val lz = letter.z * 0.6f

    var point = Point3D(lx, ly, lz)
    point = rotateY(point, rotationY)
    point = rotateX(point, rotationX)

    // Project to screen coordinates
    val screenPos = project(point, centerX, centerY, scale)

    // Calculate size based on depth (perspective)
    val fov = 500f
    val perspectiveScale = fov / (fov + point.z)
    val baseSize = 45.dp
    val finalSize = baseSize * letter.size * perspectiveScale * scale

    // Pre-calculate the size in pixels using the passed density
    val finalSizePx = with(density) { finalSize.toPx() }

    // Calculate opacity based on depth
    val alpha = (0.4f + 0.6f * perspectiveScale).coerceIn(0.3f, 1f)

    // Random color based on letter
    val hue = (letter.letter[0].code * 37) % 360
    val cubeColor = Color.hsl(hue.toFloat(), 0.7f, 0.5f)

    Box(
        modifier = Modifier
            .offset {
                // Now we use the pre-calculated finalSizePx instead of calling LocalDensity
                IntOffset(
                    (screenPos.x - finalSizePx / 2).roundToInt(),
                    (screenPos.y - finalSizePx / 2).roundToInt()
                )
            }
            .size(finalSize)
            .graphicsLayer {
                // Apply the letter's own rotation
                this.rotationX = letter.rotationX
                this.rotationY = letter.rotationY
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(6.dp))
            .background(cubeColor)
            .pointerInput(letter.letter) {
                detectTapGestures {
                    onTap()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.letter,
            color = Color.White,
            fontSize = (finalSize.value * 0.5f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Simplified 3D keyboard preview (non-interactive).
 * Used during the intro animation before chaos starts.
 */
@Composable
fun KeyboardChaosPreview(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "QWERTYUIOP\nASDFGHJKL\nZXCVBNM",
            color = Color.Green,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp
        )
    }
}