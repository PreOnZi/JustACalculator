package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Stripped-down Tetris launched from the phone-detour home screen.
 *
 * 10×20 board, 7 standard pieces, manual-fall (no soft-drop), four buttons:
 * left, rotate, right, and "drop one row". When the stack reaches the top,
 * shows Game Over with a Restart button.
 *
 * Deliberately minimal: no scoring beyond cleared-line count, no levels, no
 * hold/preview. The bit is "you can play tetris in this calculator," not
 * "this is a serious tetris implementation."
 */

private const val COLS = 10
private const val ROWS = 20
private const val FALL_INTERVAL_MS = 600L

@Composable
fun PhoneTetrisApp(onClose: () -> Unit) {
    var board by remember { mutableStateOf(Array(ROWS) { IntArray(COLS) }) }
    var piece by remember { mutableStateOf(spawnPiece()) }
    var lines by remember { mutableStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    // Auto-fall driver
    LaunchedEffect(gameOver) {
        if (gameOver) return@LaunchedEffect
        while (!gameOver) {
            delay(FALL_INTERVAL_MS)
            val (newBoard, newPiece, newLines, over) = stepDown(board, piece)
            board = newBoard
            piece = newPiece
            lines += newLines
            gameOver = over
            tick++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 18.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Tetris",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Lines: $lines",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(12.dp))

            // Board
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cellSize = minOf(size.width / COLS, size.height / ROWS)
                    val boardWidth = cellSize * COLS
                    val boardHeight = cellSize * ROWS
                    val ox = (size.width - boardWidth) / 2f
                    val oy = (size.height - boardHeight) / 2f

                    // Background
                    drawRect(
                        color = Color(0xFF14141C),
                        topLeft = Offset(ox, oy),
                        size = Size(boardWidth, boardHeight)
                    )

                    // Render board cells
                    for (r in 0 until ROWS) {
                        for (c in 0 until COLS) {
                            val v = board[r][c]
                            if (v != 0) {
                                drawCell(ox, oy, cellSize, c, r, pieceColors[v - 1])
                            }
                        }
                    }
                    // Render falling piece
                    piece.cells().forEach { (cx, cy) ->
                        if (cy in 0 until ROWS && cx in 0 until COLS) {
                            drawCell(ox, oy, cellSize, cx, cy, pieceColors[piece.kind])
                        }
                    }
                    // Outer frame
                    drawRect(
                        color = Color.White.copy(alpha = 0.18f),
                        topLeft = Offset(ox, oy),
                        size = Size(boardWidth, boardHeight),
                        style = Stroke(width = 2f)
                    )
                }

                if (gameOver) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xEE000000))
                            .padding(24.dp)
                    ) {
                        Text("Game Over", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Lines: $lines", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF3A8DFF))
                                .clickable {
                                    board = Array(ROWS) { IntArray(COLS) }
                                    piece = spawnPiece()
                                    lines = 0
                                    gameOver = false
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Restart", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CtrlButton("◀", Modifier.weight(1f)) {
                    if (!gameOver) {
                        val moved = piece.copy(x = piece.x - 1)
                        if (!collides(board, moved)) piece = moved
                    }
                }
                CtrlButton("⟳", Modifier.weight(1f)) {
                    if (!gameOver) {
                        val rotated = piece.rotated()
                        if (!collides(board, rotated)) piece = rotated
                    }
                }
                CtrlButton("▶", Modifier.weight(1f)) {
                    if (!gameOver) {
                        val moved = piece.copy(x = piece.x + 1)
                        if (!collides(board, moved)) piece = moved
                    }
                }
                CtrlButton("▼", Modifier.weight(1f)) {
                    if (!gameOver) {
                        val (newBoard, newPiece, newLines, over) = stepDown(board, piece)
                        board = newBoard
                        piece = newPiece
                        lines += newLines
                        gameOver = over
                    }
                }
            }
        }
    }
}

@Composable
private fun CtrlButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A2A38))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 22.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Game logic
// ─────────────────────────────────────────────────────────────────────────────

private val pieceColors = listOf(
    Color(0xFF00B7C7), // I — cyan
    Color(0xFFFFC400), // O — yellow
    Color(0xFFB14CFF), // T — purple
    Color(0xFF00C853), // S — green
    Color(0xFFFF3D3D), // Z — red
    Color(0xFF2962FF), // J — blue
    Color(0xFFFF7043)  // L — orange
)

// Each piece: list of rotation states; each state = list of (dx, dy) offsets.
private val pieceShapes: List<List<List<Pair<Int, Int>>>> = listOf(
    // I
    listOf(
        listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
        listOf(2 to 0, 2 to 1, 2 to 2, 2 to 3)
    ),
    // O
    listOf(
        listOf(1 to 0, 2 to 0, 1 to 1, 2 to 1)
    ),
    // T
    listOf(
        listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
        listOf(1 to 0, 1 to 1, 2 to 1, 1 to 2),
        listOf(0 to 1, 1 to 1, 2 to 1, 1 to 2),
        listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2)
    ),
    // S
    listOf(
        listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
        listOf(1 to 0, 1 to 1, 2 to 1, 2 to 2)
    ),
    // Z
    listOf(
        listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
        listOf(2 to 0, 1 to 1, 2 to 1, 1 to 2)
    ),
    // J
    listOf(
        listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
        listOf(1 to 0, 2 to 0, 1 to 1, 1 to 2),
        listOf(0 to 1, 1 to 1, 2 to 1, 2 to 2),
        listOf(1 to 0, 1 to 1, 0 to 2, 1 to 2)
    ),
    // L
    listOf(
        listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
        listOf(1 to 0, 1 to 1, 1 to 2, 2 to 2),
        listOf(0 to 1, 1 to 1, 2 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2)
    )
)

private data class Piece(val kind: Int, val rot: Int, val x: Int, val y: Int) {
    fun cells(): List<Pair<Int, Int>> {
        val rotations = pieceShapes[kind]
        return rotations[rot % rotations.size].map { (dx, dy) -> (x + dx) to (y + dy) }
    }
    fun rotated(): Piece {
        val rotations = pieceShapes[kind]
        return copy(rot = (rot + 1) % rotations.size)
    }
}

private fun spawnPiece(): Piece {
    val kind = Random.nextInt(pieceShapes.size)
    return Piece(kind = kind, rot = 0, x = COLS / 2 - 2, y = 0)
}

private fun collides(board: Array<IntArray>, piece: Piece): Boolean {
    piece.cells().forEach { (cx, cy) ->
        if (cx < 0 || cx >= COLS || cy >= ROWS) return true
        if (cy >= 0 && board[cy][cx] != 0) return true
    }
    return false
}

private fun lockAndClear(board: Array<IntArray>, piece: Piece): Pair<Array<IntArray>, Int> {
    val next = Array(ROWS) { r -> board[r].copyOf() }
    piece.cells().forEach { (cx, cy) ->
        if (cy in 0 until ROWS && cx in 0 until COLS) {
            next[cy][cx] = piece.kind + 1
        }
    }
    // Clear full rows
    val kept = next.filter { row -> row.any { it == 0 } }
    val cleared = ROWS - kept.size
    val padded = Array(ROWS) { r ->
        if (r < cleared) IntArray(COLS) else kept[r - cleared]
    }
    return padded to cleared
}

private data class StepResult(
    val board: Array<IntArray>,
    val piece: Piece,
    val cleared: Int,
    val gameOver: Boolean
)

private fun stepDown(board: Array<IntArray>, piece: Piece): StepResult {
    val advanced = piece.copy(y = piece.y + 1)
    return if (!collides(board, advanced)) {
        StepResult(board, advanced, 0, false)
    } else {
        val (locked, cleared) = lockAndClear(board, piece)
        val next = spawnPiece()
        val over = collides(locked, next)
        StepResult(locked, next, cleared, over)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCell(
    ox: Float, oy: Float, cell: Float, col: Int, row: Int, color: Color
) {
    val x = ox + col * cell
    val y = oy + row * cell
    drawRect(
        color = color,
        topLeft = Offset(x + 1f, y + 1f),
        size = Size(cell - 2f, cell - 2f)
    )
    drawRect(
        color = Color.White.copy(alpha = 0.25f),
        topLeft = Offset(x + 1f, y + 1f),
        size = Size(cell - 2f, cell - 2f),
        style = Stroke(width = 1.5f)
    )
}
