package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.BezelBrown
import com.fictioncutshort.justacalculator.util.BezelInverted
import com.fictioncutshort.justacalculator.util.DarkText
import com.fictioncutshort.justacalculator.util.LetterGenerator
import com.fictioncutshort.justacalculator.util.RetroDisplayGreen
import com.fictioncutshort.justacalculator.util.WordCategories
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * LetterBlockGame
 *
 * Replacement for the falling-words game (steps 119+). The screen fills with
 * lettered cubes that fall in from the top, settle into a stack via simple
 * gravity + AABB collision, and stay put until the player taps them. Tapping
 * letters in order builds a word; once the formed string matches a recognized
 * answer (mood / colour / season / cuisine / etc — see [WordCategories]) the
 * selected blocks fly out of the screen and a fresh batch falls in for the
 * next question.
 *
 * Pseudo-3D rendering: each cube is drawn as three quads (top, right, front)
 * with the letter on the front face. Shading is hard-coded — top brightest,
 * right darkest — so the cubes read as solid blocks without an actual 3D
 * pipeline.
 *
 * The game owns its physics state internally (blocks list, selection). It
 * only writes back to the global CalculatorState by calling [onSubmitWord]
 * once a valid word is locked in; that hands off to the existing
 * `handleWordGameResponse` flow which routes the story branch.
 *
 * Question text is supplied by the caller via [questionText] and rendered in
 * the top band reserved above the play area.
 */

private const val GRAVITY = 1800f         // px/s²
private const val MAX_FALL_SPEED = 1400f  // px/s
private const val SPAWN_INTERVAL_MS = 80L
private const val SELECTION_SUBMIT_DELAY_MS = 250L
private const val FALL_OUT_DURATION_MS = 700L

@Composable
fun LetterBlockGame(
    questionText: String,
    onSubmitWord: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // questionText kept for API compatibility; MainActivity renders the
        // message in its own overlay layer behind the cubes.
        val playAreaWidthPx = widthPx
        val playAreaHeightPx = heightPx

        // Orientation-aware grid:
        //   Portrait  → ~6 cols × 11 rows → tall stack, lots of letters.
        //   Landscape → 10 cols × 4 rows → wider but shorter, so blocks stay
        //               on-screen instead of overflowing the bottom and the
        //               grid still fits between the message area and the
        //               bottom edge.
        val isLandscape = playAreaWidthPx > playAreaHeightPx
        val targetCols = if (isLandscape) 10 else 6
        val maxBlocksPerColumn = if (isLandscape) 6 else 11
        // Prefill matches the per-column cap so the grid opens full
        // (no half-empty columns at the start). Capped at maxBlocksPerColumn
        // so the runtime invariant in spawnIfNeeded still holds.
        val prefillRows = if (isLandscape) 6 else 11
        // Pick the largest block size that still fits both targets — width
        // bound (cols × size ≤ width) and height bound (maxStack × size ≤
        // height). Landscape reserves 1.5 cells of vertical headroom for the
        // spelled-word chip so it never gets crammed against the screen edge;
        // portrait only needs the standard half-cell of breathing room.
        val verticalHeadroom = if (isLandscape) 1.5f else 0.5f
        val widthBoundedSize = playAreaWidthPx / (targetCols + 0.5f)
        val heightBoundedSize = playAreaHeightPx / (maxBlocksPerColumn + verticalHeadroom)
        val blockSize = kotlin.math.min(widthBoundedSize, heightBoundedSize)
        val cols = max(4, (playAreaWidthPx / blockSize).toInt())
        // Refill to full grid capacity (cols × maxBlocksPerColumn). Earlier
        // 0.75× cap left the grid permanently below the prefill height after
        // a few words were removed — each successful answer should put new
        // letters back from the top until every column is full again.
        val targetBlockCount = cols * maxBlocksPerColumn

        // Internal mutable state. Keyed on isLandscape so flipping orientation
        // resets the grid — block positions are stored in pixel coords for the
        // *previous* play area and would otherwise float off-screen or stack
        // at the wrong height after a rotation.
        var blocks by remember(isLandscape) { mutableStateOf<List<Block>>(emptyList()) }
        var selectedIds by remember(isLandscape) { mutableStateOf<List<Int>>(emptyList()) }
        var nextId by remember(isLandscape) { mutableStateOf(0) }
        // No external shuffle: getInitialLetterQueue() returns a queue whose
        // front is already a balanced, shuffled set of guaranteed answer-word
        // letters and whose tail is the shuffled bulk pool. Re-shuffling the
        // whole thing would scatter the guaranteed letters across the entire
        // queue and re-introduce the variety problem the layout was built to
        // solve (some branches would land with no spellable answer at start).
        var letterQueue by remember(isLandscape) {
            mutableStateOf(LetterGenerator.getInitialLetterQueue())
        }
        var fallingOutDeadline by remember(isLandscape) { mutableStateOf(0L) }
        var lockedSubmission by remember(isLandscape) { mutableStateOf(false) }

        fun nextLetter(): Char {
            if (letterQueue.isEmpty()) {
                letterQueue = LetterGenerator.getInitialLetterQueue()
            }
            val letter = letterQueue.first()
            letterQueue = letterQueue.drop(1)
            return letter
        }

        // Compute per-column occupancy from current block positions. A block
        // is "in" column c if its x falls within that column's slot.
        fun columnCounts(): IntArray {
            val counts = IntArray(cols)
            val colWidth = playAreaWidthPx / cols
            blocks.forEach { b ->
                if (!b.fallingOut) {
                    val c = (b.x / colWidth).toInt().coerceIn(0, cols - 1)
                    counts[c]++
                }
            }
            return counts
        }

        fun spawnIfNeeded(now: Long, lastSpawnAt: Long): Long {
            if (lockedSubmission) return lastSpawnAt
            val active = blocks.count { !it.fallingOut }
            if (active >= targetBlockCount) return lastSpawnAt
            if (now - lastSpawnAt < SPAWN_INTERVAL_MS) return lastSpawnAt
            // Pick from columns that haven't reached the cap yet, so no
            // single column ever stacks past `maxBlocksPerColumn` and the
            // top of the screen stays clear for the message text.
            val counts = columnCounts()
            val available = (0 until cols).filter { counts[it] < maxBlocksPerColumn }
            if (available.isEmpty()) return lastSpawnAt
            val col = available[Random.nextInt(available.size)]
            val xCenter = (col + 0.5f) * (playAreaWidthPx / cols) +
                Random.nextFloat() * (blockSize * 0.10f) - (blockSize * 0.05f)
            val newBlock = Block(
                id = nextId,
                letter = nextLetter(),
                x = xCenter.coerceIn(blockSize / 2f, playAreaWidthPx - blockSize / 2f),
                // Spawn a couple of block-heights above the play area so the
                // user sees the cube enter the screen by free-falling, not
                // pop in at the top edge.
                y = -blockSize * 1.5f - Random.nextFloat() * blockSize * 0.8f,
                vy = 0f,
                size = blockSize
            )
            nextId += 1
            blocks = blocks + newBlock
            return now
        }

        // Physics + spawn driver. One coroutine, ticks every frame.
        LaunchedEffect(playAreaHeightPx, playAreaWidthPx, blockSize) {
            if (playAreaHeightPx <= 0f) return@LaunchedEffect

            // Pre-fill the bottom of the play area with settled blocks so the
            // user sees a populated grid the instant the game opens. Without
            // this they'd watch the queue trickle in for ~5 seconds before
            // anything was tappable.
            if (blocks.isEmpty()) {
                val prefilled = buildList {
                    for (row in 0 until prefillRows) {
                        for (col in 0 until cols) {
                            val xCenter = (col + 0.5f) * (playAreaWidthPx / cols)
                            // Row 0 sits on the floor, row 1 on top of row 0, etc.
                            val yCenter = playAreaHeightPx - (row + 0.5f) * blockSize
                            add(
                                Block(
                                    id = nextId,
                                    letter = nextLetter(),
                                    x = xCenter,
                                    y = yCenter,
                                    vy = 0f,
                                    size = blockSize
                                )
                            )
                            nextId += 1
                        }
                    }
                }
                blocks = prefilled
            }

            var prevNs = 0L
            var lastSpawnAt = System.currentTimeMillis()
            while (true) {
                val nowNs = withFrameNanos { it }
                val dt = if (prevNs == 0L) 0f else (nowNs - prevNs) / 1_000_000_000f
                prevNs = nowNs
                if (dt > 0f) {
                    val nowMs = System.currentTimeMillis()
                    lastSpawnAt = spawnIfNeeded(nowMs, lastSpawnAt)
                    blocks = stepPhysics(
                        blocks,
                        dt = min(dt, 0.033f),
                        floorY = playAreaHeightPx,
                        playWidth = playAreaWidthPx
                    )

                    // Sweep blocks that fell out of the screen.
                    if (blocks.any { it.fallingOut && it.y - it.size / 2 > playAreaHeightPx + 100f }) {
                        blocks = blocks.filterNot {
                            it.fallingOut && it.y - it.size / 2 > playAreaHeightPx + 100f
                        }
                    }

                    // Lift the submission lock once the falling-out crew has cleared.
                    if (lockedSubmission && fallingOutDeadline > 0L && nowMs > fallingOutDeadline) {
                        lockedSubmission = false
                        fallingOutDeadline = 0L
                        selectedIds = emptyList()
                    }
                }
            }
        }

        // Word currently spelled by the selection.
        val spelled = remember(selectedIds, blocks) {
            buildString {
                selectedIds.forEach { id ->
                    blocks.firstOrNull { it.id == id }?.let { append(it.letter) }
                }
            }
        }

        // Auto-submit when the spelled word becomes a recognized answer.
        LaunchedEffect(spelled) {
            if (lockedSubmission) return@LaunchedEffect
            if (spelled.length < 1) return@LaunchedEffect
            if (!WordCategories.isValidWord(spelled)) return@LaunchedEffect
            // Brief pause so the user sees what they spelled before it animates out.
            delay(SELECTION_SUBMIT_DELAY_MS)
            // Re-check in case state changed during the delay
            if (lockedSubmission) return@LaunchedEffect
            val accepted = onSubmitWord(spelled)
            if (!accepted) return@LaunchedEffect
            lockedSubmission = true
            // Fly the selected blocks out and queue a refill.
            val ids = selectedIds.toSet()
            blocks = blocks.map { b ->
                if (b.id in ids) b.copy(fallingOut = true, vy = -350f) else b
            }
            fallingOutDeadline = System.currentTimeMillis() + FALL_OUT_DURATION_MS
        }

        // ── Play area ────────────────────────────────────────────────────
        // Fills the entire LetterBlockGame surface so cubes can fall from
        // the very top of the screen. Background is transparent so the
        // calculator's RetroCream body and message text show through —
        // cubes render in front; the per-column cap of 11 keeps the
        // settled stack from ever reaching the message area.
        // clipToBounds keeps the falling cubes inside this Box (they
        // spawn at negative Y) so they can't bleed into the bezel above.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(blocks, lockedSubmission) {
                    detectTapGestures { tap ->
                        if (lockedSubmission) return@detectTapGestures
                        // Hit-test in reverse so the visually-front block wins.
                        val hit = blocks
                            .asReversed()
                            .firstOrNull { b ->
                                !b.fallingOut &&
                                    tap.x in (b.x - b.size / 2)..(b.x + b.size / 2) &&
                                    tap.y in (b.y - b.size / 2)..(b.y + b.size / 2)
                            }
                            ?: return@detectTapGestures

                        val existing = selectedIds.indexOf(hit.id)
                        selectedIds = if (existing >= 0) {
                            // Tapping a selected block trims the selection back to (and
                            // including) that letter — same UX as Wordle/word-search.
                            selectedIds.subList(0, existing)
                        } else {
                            selectedIds + hit.id
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                blocks.forEach { b ->
                    drawCube(
                        block = b,
                        isSelected = b.id in selectedIds,
                        selectionOrder = selectedIds.indexOf(b.id) + 1
                    )
                }
            }

            // ── Spelled-so-far chip ────────────────────────────────────
            // Sits just above the top of the (capped) stack so it reads as
            // part of the play area but never overlaps a settled block.
            // Bottom padding = stack height (maxBlocksPerColumn × blockSize)
            // plus a small gap. Only renders while a selection exists.
            if (spelled.isNotEmpty()) {
                val stackTopFromBottomDp = with(density) {
                    (maxBlocksPerColumn * blockSize).toDp()
                }
                // Lift the chip noticeably above the top of the stack so it
                // reads as a separate UI element rather than sitting on the
                // top row of blocks. Landscape has extra headroom reserved
                // above the stack (via verticalHeadroom = 1.5), so this gap
                // never collides with a settled block.
                val chipGap = if (isLandscape) 24.dp else 20.dp
                Text(
                    text = spelled,
                    color = AccentOrange,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = stackTopFromBottomDp + chipGap)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Physics
// ─────────────────────────────────────────────────────────────────────────────

/** Single lettered cube. [fallingOut]=true skips collision and uses a fixed downward velocity. */
private data class Block(
    val id: Int,
    val letter: Char,
    val x: Float,
    val y: Float,
    val vy: Float,
    val size: Float,
    val fallingOut: Boolean = false
)

private fun stepPhysics(
    blocks: List<Block>,
    dt: Float,
    floorY: Float,
    playWidth: Float
): List<Block> {
    if (blocks.isEmpty()) return blocks

    // Pre-sort by y descending so a block's potential floor is whatever is
    // already "below" it in this snapshot (lower y == higher up).
    // Settled blocks (vy == 0 and resting) act as floor for blocks above.
    val sorted = blocks.sortedByDescending { it.y }
    // Map of column-index → floor-y for cheap stack collision. The grid is
    // approximate — blocks within ~half-cell in x are considered same column.
    val cellWidth = blocks.first().size

    return sorted.map { b ->
        if (b.fallingOut) {
            val newVy = (b.vy + GRAVITY * dt * 1.4f).coerceAtMost(MAX_FALL_SPEED * 1.5f)
            return@map b.copy(y = b.y + newVy * dt, vy = newVy)
        }

        // Apply gravity
        var newVy = b.vy + GRAVITY * dt
        if (newVy > MAX_FALL_SPEED) newVy = MAX_FALL_SPEED
        var newY = b.y + newVy * dt

        // Find resting surface: floor or top of any nearby settled block.
        val candidates = blocks.filter { other ->
            other.id != b.id &&
                !other.fallingOut &&
                kotlin.math.abs(other.x - b.x) < cellWidth * 0.85f &&
                other.y > b.y
        }
        val supportTop = candidates.minOfOrNull { it.y - it.size / 2f } ?: floorY
        val ceiling = supportTop - b.size / 2f - 0.5f

        if (newY >= ceiling) {
            newY = ceiling
            newVy = 0f
        }

        // Wall clamp (shouldn't happen since spawn x is constrained, but be safe).
        val newX = b.x.coerceIn(b.size / 2f, playWidth - b.size / 2f)

        b.copy(x = newX, y = newY, vy = newVy)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rendering — pseudo-3D cube
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawCube(
    block: Block,
    isSelected: Boolean,
    selectionOrder: Int
) {
    val s = block.size
    val cx = block.x
    val cy = block.y
    val depth = s * 0.18f

    // Base palette — unselected blocks pick up the calculator's AccentOrange
    // (same as the operator buttons), selected blocks swap to RetroDisplayGreen
    // (the LCD readout colour) so the contrast pops without introducing a
    // foreign hue.
    val baseFront = if (isSelected) RetroDisplayGreen else AccentOrange
    val baseTop = baseFront.lighten(0.18f)
    val baseSide = baseFront.darken(0.30f)
    val borderColor = if (isSelected) RetroDisplayGreen.darken(0.45f) else BezelInverted

    // Front face rectangle
    val frontTL = Offset(cx - s / 2, cy - s / 2)
    val frontSize = Size(s, s)

    // Top face: trapezoid above front
    val topPath = Path().apply {
        moveTo(cx - s / 2, cy - s / 2)               // front-top-left
        lineTo(cx - s / 2 + depth, cy - s / 2 - depth) // back-top-left
        lineTo(cx + s / 2 + depth, cy - s / 2 - depth) // back-top-right
        lineTo(cx + s / 2, cy - s / 2)               // front-top-right
        close()
    }
    drawPath(path = topPath, color = baseTop)

    // Right face: trapezoid right of front
    val sidePath = Path().apply {
        moveTo(cx + s / 2, cy - s / 2)               // front-top-right
        lineTo(cx + s / 2 + depth, cy - s / 2 - depth) // back-top-right
        lineTo(cx + s / 2 + depth, cy + s / 2 - depth) // back-bottom-right
        lineTo(cx + s / 2, cy + s / 2)               // front-bottom-right
        close()
    }
    drawPath(path = sidePath, color = baseSide)

    // Front face
    drawRect(color = baseFront, topLeft = frontTL, size = frontSize)

    // Border around the front face for definition
    drawRect(
        color = borderColor,
        topLeft = frontTL,
        size = frontSize,
        style = Stroke(width = 1.5f)
    )

    // Letter — drawn via native canvas because Compose's text APIs from inside
    // DrawScope are ergonomically painful for this use case. Dark text on
    // the bright LCD-green selected block reads better than white-on-green.
    val nativePaint = android.graphics.Paint().apply {
        color = if (isSelected) android.graphics.Color.rgb(0x2D, 0x2D, 0x2D) else android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = s * 0.55f
        isAntiAlias = true
        isFakeBoldText = true
    }
    val textY = cy + (nativePaint.textSize / 3f)
    drawContext.canvas.nativeCanvas.drawText(
        block.letter.toString(),
        cx,
        textY,
        nativePaint
    )

    // Selection-order badge (small circle bottom-right with the order number).
    if (isSelected && selectionOrder > 0) {
        val badgeR = s * 0.18f
        val badgeCx = cx + s / 2 - badgeR - 2f
        val badgeCy = cy + s / 2 - badgeR - 2f
        drawCircle(color = BezelInverted, radius = badgeR, center = Offset(badgeCx, badgeCy))
        drawCircle(
            color = AccentOrange,
            radius = badgeR,
            center = Offset(badgeCx, badgeCy),
            style = Stroke(width = 2f)
        )
        val badgePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = badgeR * 1.2f
            isAntiAlias = true
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            selectionOrder.toString(),
            badgeCx,
            badgeCy + badgeR * 0.4f,
            badgePaint
        )
    }
}

private fun Color.lighten(amount: Float): Color {
    return Color(
        red = (red + (1f - red) * amount).coerceIn(0f, 1f),
        green = (green + (1f - green) * amount).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private fun Color.darken(amount: Float): Color {
    return Color(
        red = (red * (1f - amount)).coerceIn(0f, 1f),
        green = (green * (1f - amount)).coerceIn(0f, 1f),
        blue = (blue * (1f - amount)).coerceIn(0f, 1f),
        alpha = alpha
    )
}
