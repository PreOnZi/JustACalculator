package com.fictioncutshort.justacalculator.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.data.ChaosKey
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.Point3D
import com.fictioncutshort.justacalculator.util.project
import com.fictioncutshort.justacalculator.util.rotateX
import com.fictioncutshort.justacalculator.util.rotateY
import com.fictioncutshort.justacalculator.util.vibrate
import kotlinx.coroutines.delay
import androidx.compose.ui.text.drawText




/**
 * KeyboardChaos3DView.kt
 *
 * Full 3D keyboard chaos mini-game with two phases:
 *
 * PHASE 1 (Word Building - first 60 seconds):
 * - Calculator keyboard rendered as 3D cubes
 * - Floating letter cubes around the keyboard
 * - Player can drag to connect letters and form words
 * - Constellation-style lines show connections
 *
 * PHASE 2 (Cleanup - after 60 seconds):
 * - Player taps letters to dismiss them
 * - Must clear all letters to proceed
 *
 * Features:
 * - Full 3D perspective projection
 * - Drag to rotate the view
 * - Zoom slider
 * - Haptic feedback on interactions
 */

/**
 * Helper function to calculate screen position of a letter after 3D transformation.
 * Used for hit testing and constellation line drawing.
 */
private fun getLetterScreenPosition(
    chaosKey: ChaosKey,
    rotationX: Float,
    rotationY: Float,
    scale: Float,
    centerX: Float,
    centerY: Float
): Offset {
    val lx = chaosKey.x * 0.6f
    val ly = chaosKey.y * 0.6f
    val lz = chaosKey.z * 0.6f

    val point = Point3D(lx, ly, lz)
    val rotatedY = rotateY(point, rotationY)
    val rotatedXY = rotateX(rotatedY, rotationX)

    return project(rotatedXY, centerX, centerY, scale)
}

@Composable
fun KeyboardChaos3DView(
    chaosLetters: List<ChaosKey>,
    rotationX: Float,
    rotationY: Float,
    scale: Float,
    message: String,
    onRotationChange: (Float, Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onLetterTap: (ChaosKey) -> Unit
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()

    // Keep updated references to avoid stale closures in pointerInput
    val currentChaosLetters by rememberUpdatedState(chaosLetters)
    val currentRotationX by rememberUpdatedState(rotationX)
    val currentRotationY by rememberUpdatedState(rotationY)
    val currentScale by rememberUpdatedState(scale)
    val currentOnLetterTap by rememberUpdatedState(onLetterTap)

    // Phase tracking: false = word building, true = cleanup
    var isCleanupPhase by remember { mutableStateOf(false) }

    // Word building state - store LETTER REFERENCES, not screen positions
    var currentWord by remember { mutableStateOf("") }
    var connectedLetters by remember { mutableStateOf<List<ChaosKey>>(emptyList()) }
    var isDragging by remember { mutableStateOf(false) }
    var currentFingerPos by remember { mutableStateOf<Offset?>(null) }
    var dragStartedOnLetter by remember { mutableStateOf(false) }

    // Phase messages
    val phase1Message =
        "Well, that's not exactly a QWERTY keyboard, is it? Maybe you can try using it anyway - connect keys."
    val phase2Message =
        "Nevermind. This is way too uncomfortable - as pretty as it looks. I'll have to try something else. Can you get rid of the rogue keys? I have to focus to even keep it together."

    // Timer to switch to cleanup phase after 60 seconds
    LaunchedEffect(Unit) {
        delay(30000)
        isCleanupPhase = true
        connectedLetters = emptyList()
        currentWord = ""
        isDragging = false
        currentFingerPos = null
        dragStartedOnLetter = false
    }

    // Display message based on phase
    val displayMessage = if (isCleanupPhase) {
        if (chaosLetters.isEmpty()) message else phase2Message
    } else {
        if (currentWord.isNotEmpty()) "I'm reading: $currentWord" else phase1Message
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
    ) {
        // Message at top
        Text(
            text = displayMessage,
            color = Color(0xFF00FF00),
            fontSize = 16.sp,
            fontFamily = CalculatorDisplayFont,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 40.dp)
                .align(Alignment.TopCenter)
        )

        // Letters remaining counter (cleanup phase only)
        if (isCleanupPhase) {
            Text(
                text = "Letters: ${chaosLetters.size}",
                color = Color(0xFF00FF00),
                fontSize = 16.sp,
                fontFamily = CalculatorDisplayFont,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
            )
        }

        // =============== ZOOM SLIDER (horizontal at bottom) ===============
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "−",
                color = Color(0xFF00FF00),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Slider(
                value = scale,
                onValueChange = { onScaleChange(it) },
                valueRange = 0.3f..4.5f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00FF00),
                    activeTrackColor = Color(0xFF00FF00),
                    inactiveTrackColor = Color(0xFF333333)
                )
            )

            Text(
                text = "+",
                color = Color(0xFF00FF00),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${(scale * 100).toInt()}%",
                color = Color(0xFF00FF00),
                fontSize = 12.sp,
                fontFamily = CalculatorDisplayFont,
                modifier = Modifier.width(45.dp)
            )
        }

        // ===================================================================
        // 3D CANVAS
        // ===================================================================
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 110.dp, bottom = 85.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Calculate current letter positions for hit testing
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f

                            if (!isCleanupPhase) {
                                var foundLetter: ChaosKey? = null

                                for (chaosKey in currentChaosLetters) {
                                    val screenPos = getLetterScreenPosition(
                                        chaosKey,
                                        currentRotationX,
                                        currentRotationY,
                                        currentScale,
                                        centerX,
                                        centerY
                                    )
                                    // Larger hit radius that works from any angle
                                    val baseRadius = 55f * currentScale * chaosKey.size
                                    val hitRadius =
                                        baseRadius.coerceAtLeast(35f) // Minimum 35px hit area

                                    val dx = offset.x - screenPos.x
                                    val dy = offset.y - screenPos.y
                                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                                    if (distance < hitRadius) {
                                        foundLetter = chaosKey
                                        break
                                    }
                                }

                                if (foundLetter != null) {
                                    dragStartedOnLetter = true
                                    isDragging = true
                                    currentFingerPos = offset
                                    connectedLetters = listOf(foundLetter)
                                    currentWord = foundLetter.letter
                                    vibrate(context, 15, 80)
                                } else {
                                    dragStartedOnLetter = false
                                    isDragging = false
                                }
                            } else {
                                dragStartedOnLetter = false
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            if (!isCleanupPhase && dragStartedOnLetter && isDragging) {
                                val pos = change.position
                                currentFingerPos = pos

                                val centerX = size.width / 2f
                                val centerY = size.height / 2f

                                for (chaosKey in currentChaosLetters) {
                                    if (!connectedLetters.contains(chaosKey)) {
                                        val screenPos = getLetterScreenPosition(
                                            chaosKey,
                                            currentRotationX,
                                            currentRotationY,
                                            currentScale,
                                            centerX,
                                            centerY
                                        )
                                        // Larger hit radius that works from any angle
                                        val baseRadius = 55f * currentScale * chaosKey.size
                                        val hitRadius = baseRadius.coerceAtLeast(35f)

                                        val dx = pos.x - screenPos.x
                                        val dy = pos.y - screenPos.y
                                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                                        if (distance < hitRadius) {
                                            connectedLetters = connectedLetters + chaosKey
                                            currentWord = currentWord + chaosKey.letter
                                            vibrate(context, 15, 80)
                                        }
                                    }
                                }
                            } else {
                                // Rotation mode
                                onRotationChange(dragAmount.x * 0.3f, -dragAmount.y * 0.3f)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            currentFingerPos = null
                            dragStartedOnLetter = false
                        },
                        onDragCancel = {
                            isDragging = false
                            currentFingerPos = null
                            dragStartedOnLetter = false
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        if (isCleanupPhase) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f

                            // Find the CLOSEST letter to the tap, not just any letter within radius
                            var closestLetter: ChaosKey? = null
                            var closestDistance = Float.MAX_VALUE

                            for (chaosKey in currentChaosLetters) {
                                val screenPos = getLetterScreenPosition(
                                    chaosKey,
                                    currentRotationX,
                                    currentRotationY,
                                    currentScale,
                                    centerX,
                                    centerY
                                )

                                val dx = tapOffset.x - screenPos.x
                                val dy = tapOffset.y - screenPos.y
                                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                                // Larger hit radius that works from any angle
                                val baseRadius = 55f * currentScale * chaosKey.size
                                val hitRadius = baseRadius.coerceAtLeast(35f)

                                if (distance < hitRadius && distance < closestDistance) {
                                    closestDistance = distance
                                    closestLetter = chaosKey
                                }
                            }

                            // Only remove the single closest letter
                            if (closestLetter != null) {
                                vibrate(context, 20, 100)
                                currentOnLetterTap(closestLetter)
                            }
                        }
                    }
                }
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2

            data class FaceToDraw(
                val vertices: List<Point3D>,
                val color: Color,
                val avgZ: Float,
                val label: String,
                val isFront: Boolean,
                val textColor: Color
            )

            val allFaces = mutableListOf<FaceToDraw>()

            // =============== CALCULATOR KEYBOARD CUBES ===============
            val keyboardLayout = listOf(
                listOf("C", "DEL", "%", "/"),
                listOf("7", "8", "9", "*"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "=", "")
            )

            fun getKeyColor(key: String): Color = when {
                key in "0".."9" -> Color(0xFFE8E4DA)
                key in listOf("+", "-", "*", "/", "=", "%") -> Color(0xFF6B6B6B)
                key == "C" -> Color(0xFFC9463D)
                key == "DEL" -> Color(0xFFD4783C)
                key == "." -> Color(0xFFE8E4DA)
                else -> Color(0xFF333333)
            }

            fun getTextColor(key: String): Color = when {
                key in "0".."9" -> Color(0xFF2D2D2D)
                key == "." -> Color(0xFF2D2D2D)
                else -> Color.White
            }

            val cubeSize = 32f
            val spacing = 42f
            val half = cubeSize / 2
            val totalWidth = 4 * spacing
            val totalHeight = 5 * spacing

            val faceIndices = listOf(
                listOf(4, 5, 6, 7), listOf(1, 0, 3, 2), listOf(0, 4, 7, 3),
                listOf(5, 1, 2, 6), listOf(0, 1, 5, 4), listOf(7, 6, 2, 3)
            )

            // Build calculator cubes
            keyboardLayout.forEachIndexed { row, keys ->
                keys.forEachIndexed { col, key ->
                    if (key.isNotEmpty()) {
                        val kx = (col * spacing) - totalWidth / 2 + spacing / 2
                        val ky = (row * spacing) - totalHeight / 2 + spacing / 2
                        val keyColor = getKeyColor(key)
                        val txtColor = getTextColor(key)

                        val verts = listOf(
                            Point3D(-half + kx, -half + ky, -half),
                            Point3D(half + kx, -half + ky, -half),
                            Point3D(half + kx, half + ky, -half),
                            Point3D(-half + kx, half + ky, -half),
                            Point3D(-half + kx, -half + ky, half),
                            Point3D(half + kx, -half + ky, half),
                            Point3D(half + kx, half + ky, half),
                            Point3D(-half + kx, half + ky, half)
                        ).map { rotateX(rotateY(it, rotationY), rotationX) }

                        faceIndices.forEachIndexed { fi, face ->
                            val fv = face.map { verts[it] }
                            val faceColor = when (fi) {
                                0 -> keyColor
                                1 -> keyColor.copy(alpha = 0.85f)
                                else -> Color(
                                    keyColor.red * 0.55f,
                                    keyColor.green * 0.55f,
                                    keyColor.blue * 0.55f
                                )
                            }
                            allFaces.add(
                                FaceToDraw(
                                    fv,
                                    faceColor,
                                    fv.map { it.z }.average().toFloat(),
                                    if (fi == 0) key else "",
                                    fi == 0,
                                    txtColor
                                )
                            )
                        }
                    }
                }
            }

            // =============== CHAOS LETTER CUBES ===============
            val letterHalf = 12f
            chaosLetters.forEach { ck ->
                val lx = ck.x * 0.6f
                val ly = ck.y * 0.6f
                val lz = ck.z * 0.6f
                val sh = letterHalf * ck.size
                val isConnected = connectedLetters.contains(ck)

                val verts = listOf(
                    Point3D(-sh + lx, -sh + ly, -sh + lz),
                    Point3D(sh + lx, -sh + ly, -sh + lz),
                    Point3D(sh + lx, sh + ly, -sh + lz),
                    Point3D(-sh + lx, sh + ly, -sh + lz),
                    Point3D(-sh + lx, -sh + ly, sh + lz),
                    Point3D(sh + lx, -sh + ly, sh + lz),
                    Point3D(sh + lx, sh + ly, sh + lz),
                    Point3D(-sh + lx, sh + ly, sh + lz)
                ).map { rotateX(rotateY(it, rotationY), rotationX) }

                val frontColor = if (isConnected) Color(0xFF7A7A7A) else Color(0xFF5A5A5A)
                val backColor = if (isConnected) Color(0xFF4A4A4A) else Color(0xFF2A2A2A)
                val sideColor = if (isConnected) Color(0xFF5A5A5A) else Color(0xFF3A3A3A)
                val txtColor = if (isConnected) Color.White else Color(0xFFDDDDDD)

                faceIndices.forEachIndexed { fi, face ->
                    val fv = face.map { verts[it] }
                    val faceColor = when (fi) {
                        0 -> frontColor; 1 -> backColor; else -> sideColor
                    }
                    allFaces.add(
                        FaceToDraw(
                            fv,
                            faceColor,
                            fv.map { it.z }.average().toFloat(),
                            if (fi == 0) ck.letter else "",
                            fi == 0,
                            txtColor
                        )
                    )
                }
            }

            // =============== RENDER SORTED FACES ===============
            allFaces.sortedBy { it.avgZ }.forEach { face ->
                val v0 = face.vertices[0]
                val v1 = face.vertices[1]
                val v2 = face.vertices[2]
                val crossZ = (v1.x - v0.x) * (v2.y - v1.y) - (v1.y - v0.y) * (v2.x - v1.x)

                if (crossZ > 0) {
                    val proj = face.vertices.map { project(it, centerX, centerY, scale) }
                    val path = Path().apply {
                        moveTo(proj[0].x, proj[0].y)
                        proj.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    drawPath(path, face.color)
                    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(1.2f))

                    if (face.isFront && face.label.isNotEmpty()) {
                        val cx = proj.map { it.x }.average().toFloat()
                        val cy = proj.map { it.y }.average().toFloat()
                        val fw = kotlin.math.abs(proj[1].x - proj[0].x)
                        val fs = (fw * 0.5f).coerceIn(7f, 18f)
                        val tr = textMeasurer.measure(
                            face.label,
                            TextStyle(
                                fontSize = fs.sp,
                                fontWeight = FontWeight.Bold,

                            )
                        )
                        drawText(
                            textLayoutResult = tr,
                            color = face.textColor,
                            topLeft = Offset(
                                cx - tr.size.width / 2,
                                cy - tr.size.height / 2
                            )
                        )
                    }
                }
            }

            // =============== CONSTELLATION LINES (recalculated each frame) ===============
            if (!isCleanupPhase && connectedLetters.size > 1) {
                // Calculate current screen positions for connected letters
                val letterPositions = connectedLetters.mapNotNull { ck ->
                    if (chaosLetters.contains(ck)) {
                        getLetterScreenPosition(
                            ck,
                            rotationX,
                            rotationY,
                            scale,
                            centerX,
                            centerY
                        )
                    } else null
                }

                if (letterPositions.size > 1) {
                    for (i in 0 until letterPositions.size - 1) {
                        val start = letterPositions[i]
                        val end = letterPositions[i + 1]

                        // Glow layers
                        drawLine(
                            Color(0xFF87CEEB).copy(alpha = 0.15f),
                            start,
                            end,
                            16f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            Color(0xFFADD8E6).copy(alpha = 0.25f),
                            start,
                            end,
                            10f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            Color(0xFFE0FFFF).copy(alpha = 0.4f),
                            start,
                            end,
                            6f,
                            cap = StrokeCap.Round
                        )
                        drawLine(Color.White, start, end, 2f, cap = StrokeCap.Round)
                    }

                    // Star points
                    letterPositions.forEach { pt ->
                        drawCircle(Color(0xFF87CEEB).copy(alpha = 0.2f), 20f, pt)
                        drawCircle(Color(0xFFADD8E6).copy(alpha = 0.35f), 14f, pt)
                        drawCircle(Color(0xFFE0FFFF).copy(alpha = 0.5f), 9f, pt)
                        drawCircle(Color.White, 5f, pt)
                    }
                }
            }

            // Trailing line to finger
            if (!isCleanupPhase && isDragging && connectedLetters.isNotEmpty() && currentFingerPos != null) {
                val lastLetter = connectedLetters.last()
                if (chaosLetters.contains(lastLetter)) {
                    val start = getLetterScreenPosition(
                        lastLetter,
                        rotationX,
                        rotationY,
                        scale,
                        centerX,
                        centerY
                    )
                    val end = currentFingerPos!!
                    drawLine(
                        Color(0xFF87CEEB).copy(alpha = 0.1f),
                        start,
                        end,
                        12f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        Color(0xFFE0FFFF).copy(alpha = 0.25f),
                        start,
                        end,
                        6f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.5f),
                        start,
                        end,
                        2f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        // Helper function to get letter's current screen position based on rotation
        fun getLetterScreenPosition(
            chaosKey: ChaosKey,
            rotationX: Float,
            rotationY: Float,
            scale: Float,
            centerX: Float,
            centerY: Float
        ): Offset {
            val lx = chaosKey.x * 0.6f
            val ly = chaosKey.y * 0.6f
            val lz = chaosKey.z * 0.6f

            var p = Point3D(lx, ly, lz)
            p = rotateY(p, rotationY)
            p = rotateX(p, rotationX)

            return project(p, centerX, centerY, scale)
        }
    }
}