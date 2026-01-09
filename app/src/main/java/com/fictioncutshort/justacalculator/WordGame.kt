package com.fictioncutshort.justacalculator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Word categories for response detection
object WordCategories {
    fun isValidWord(word: String): Boolean {
        val lower = word.lowercase()

        // Accept mood words
        if (lower in positiveWords) return true
        if (lower in negativeWords) return true
        if (lower in neutralWords) return true
        if (lower.length == 1 && lower[0] in singleLetterWords) return true

        // Accept all colors
        if (lower in validColors) return true

        // Accept all seasons
        if (lower in validSeasons) return true

        // Accept all cuisines
        if (lower in nonSpicyCuisines || lower in spicyCuisines) return true

        // Accept invalid cuisine responses too (so they can be rejected properly)
        if (lower in invalidCuisineResponses) return true

        // Accept death responses
        if (lower in deathResponses) return true

        // Accept walk frequencies
        if (lower in walkFrequencies) return true

        // Accept maths opinions
        if (lower in mathsOpinions) return true
        return false
    }

    // Positive mood words
    val positiveWords = setOf(
        "good", "great", "well", "fine", "happy", "ok", "okay", "yes",
        "nice", "cool", "awesome", "amazing", "wonderful", "fantastic",
        "excellent", "perfect", "lovely", "blessed", "grateful", "content",
        "joyful", "cheerful", "excited", "thrilled", "delighted", "pleased",
        "satisfied", "relaxed", "peaceful", "calm", "glad", "super",
        "brilliant", "terrific", "fabulous", "marvelous", "splendid"
    )

    // Negative mood words
    val negativeWords = setOf(
        "bad", "sad", "tired", "no", "not", "upset", "angry", "mad","dead", "dying", "death", "die",
        "awful", "terrible", "horrible", "depressed", "anxious", "stressed",
        "exhausted", "miserable", "unhappy", "down", "low", "rough",
        "struggling", "suffering", "hurt", "broken", "lost", "lonely",
        "scared", "worried", "nervous", "overwhelmed", "frustrated",
        "annoyed", "irritated", "drained", "hopeless", "empty"
    )

    // Neutral mood words
    val neutralWords = setOf(
        "meh", "so", "eh", "alright", "average", "normal", "usual",
        "same", "okay", "whatever", "dunno", "idk", "unsure", "mixed",
        "indifferent", "neutral", "mediocre", "moderate", "fair", "decent"
    )

    // Single letter responses
    val singleLetterWords = setOf('i', 'a', 'o')

    // === COLORS ===
    val validColors = setOf(
        // Primary & secondary
        "red", "blue", "green", "yellow", "orange", "purple", "pink",
        "violet", "indigo", "cyan", "magenta", "lime", "teal", "turquoise",
        // Earth tones
        "brown", "tan", "beige", "cream", "ivory", "khaki", "olive",
        "maroon", "burgundy", "rust", "copper", "bronze", "gold", "silver",
        // Blues & greens
        "navy", "aqua", "mint", "sage", "emerald", "jade", "forest",
        "seafoam", "cobalt", "sapphire", "azure", "cerulean", "periwinkle",
        // Reds & pinks
        "coral", "salmon", "rose", "blush", "crimson", "scarlet", "ruby",
        "cherry", "wine", "plum", "fuchsia", "peach", "apricot",
        // Purples
        "lavender", "lilac", "mauve", "orchid", "amethyst", "grape",
        // Neutrals (special handling - will be rejected)
        "black", "white", "grey", "gray",
        // Other
        "charcoal", "slate", "pewter", "taupe", "champagne", "mustard"
    )

    val nonColorResponses = setOf("black", "white", "grey", "gray")

    // === SEASONS ===
    val validSeasons = setOf(
        "summer", "autumn", "fall", "winter", "spring", "all", "none"
    )

    val invalidSeasonResponses = setOf("all", "none")

    // === CUISINES ===
    // Non-spicy cuisines
    val nonSpicyCuisines = setOf(
        "italian", "french", "american", "british", "english", "german",
        "greek", "japanese", "sushi", "ramen", "swedish", "danish",
        "norwegian", "finnish", "dutch", "belgian", "swiss", "austrian",
        "polish", "russian", "ukrainian", "portuguese", "spanish", "tapas",
        "brazilian", "argentinian", "peruvian", "canadian", "australian",
        "irish", "scottish", "welsh", "czech", "hungarian", "romanian",
        "filipino", "hawaiian", "caribbean", "cuban", "jamaican",
        "mediterranean", "persian", "lebanese", "turkish", "israeli",
        "moroccan", "egyptian", "southern", "comfort",
        "mcdonalds", "burger", "pizza", "pasta", "seafood",
        "steak", "bbq", "barbecue", "diner", "cafe", "bakery", "dessert",
        "vegan", "vegetarian", "organic", "fusion", "modern"
    )

    // Spicy cuisines
    val spicyCuisines = setOf(
        "indian", "thai", "mexican", "chinese", "korean", "vietnamese",
        "szechuan", "sichuan", "hunan", "malaysian", "indonesian",
        "singaporean", "lankan", "bangladeshi", "pakistani", "nepali",
        "burmese", "laotian", "cambodian", "african", "ethiopian",
        "nigerian", "ghanaian", "senegalese", "cajun", "creole",
        "texmex", "southwestern", "wings", "buffalo",
        "jerk", "curry", "tandoori", "vindaloo", "kimchi",
        "salsa", "habanero", "jalapeno", "wasabi", "sriracha"
    )

    val invalidCuisineResponses = setOf(
        "none", "nothing", "idk", "dunno", "all", "any", "everything"
    )

    // === DEATH QUESTION RESPONSES ===
    val deathResponses = setOf(
        "yes", "no", "sometimes", "seldom", "often", "always", "daily",
        "rarely", "never", "occasionally", "frequently", "constantly",
        "weekly", "monthly", "yearly", "idk", "dunno", "maybe", "perhaps",
        "lot", "much", "hardly", "barely"
    )

    // === WALK FREQUENCY ===
    val walkFrequencies = setOf(
        "daily", "often", "rarely", "never", "weekly", "sometimes",
        "seldom", "frequently", "always", "occasionally", "monthly",
        "yearly", "hardly", "barely", "lot", "everyday", "once", "twice"
    )

    // === MATHS OPINIONS (triggers rant) ===
    val mathsOpinions = setOf(
        // Positive
        "love", "like", "enjoy", "fun", "great", "good", "cool", "awesome",
        "amazing", "interesting", "fascinating", "useful", "important",
        "necessary", "helpful", "beautiful", "elegant", "perfect",
        // Neutral
        "ok", "okay", "fine", "alright", "meh", "whatever", "neutral",
        "indifferent", "average", "normal", "tolerable",
        // Negative
        "hate", "dislike", "boring", "stupid", "dumb", "pointless",
        "useless", "terrible", "awful", "horrible", "worst", "annoying",
        "frustrating", "confusing", "hard", "difficult", "impossible",
        "despise", "loathe", "detest", "abhor", "sucks", "bad"
    )

    fun categorizeResponse(words: List<String>): String {
        val lowerWords = words.map { it.lowercase() }

        // Check for negative words first (they override positive)
        if (lowerWords.any { it in negativeWords }) return "negative"

        // Then check for positive
        if (lowerWords.any { it in positiveWords }) return "positive"

        // Check for neutral
        if (lowerWords.any { it in neutralWords }) return "neutral"

        // Default to neutral if we can't determine
        return "neutral"
    }

    fun isValidColor(word: String): Boolean = word.lowercase() in validColors
    fun isNonColor(word: String): Boolean = word.lowercase() in nonColorResponses
    fun isValidSeason(word: String): Boolean = word.lowercase() in validSeasons
    fun isInvalidSeason(word: String): Boolean = word.lowercase() in invalidSeasonResponses
    fun isSpicyCuisine(word: String): Boolean = word.lowercase() in spicyCuisines
    fun isNonSpicyCuisine(word: String): Boolean = word.lowercase() in nonSpicyCuisines
    fun isValidCuisine(word: String): Boolean = isSpicyCuisine(word) || isNonSpicyCuisine(word)
    fun isInvalidCuisine(word: String): Boolean = word.lowercase() in invalidCuisineResponses
    fun isDeathResponse(word: String): Boolean = word.lowercase() in deathResponses
    fun isWalkFrequency(word: String): Boolean = word.lowercase() in walkFrequencies
    fun isMathsOpinion(word: String): Boolean = word.lowercase() in mathsOpinions
}

// Letter generator with weighted distribution for word formation
object LetterGenerator {
    // Common letters weighted for word formation
    private val letterWeights = mapOf(
        'E' to 12, 'T' to 9, 'A' to 8, 'O' to 8, 'I' to 7, 'N' to 7,
        'S' to 6, 'H' to 6, 'R' to 6, 'D' to 4, 'L' to 4, 'C' to 3,
        'U' to 3, 'M' to 3, 'W' to 2, 'F' to 2, 'G' to 2, 'Y' to 2,
        'P' to 2, 'B' to 1, 'V' to 1, 'K' to 1
        // Excluding rare letters like J, X, Q, Z for easier word formation
    )

    private val weightedLetters: List<Char> by lazy {
        letterWeights.flatMap { (letter, weight) -> List(weight) { letter } }
    }

    // Generate letters that help form common response words
    fun generateHelpfulLetters(): List<Char> {
        val helpfulSequence = listOf(
            'I', 'A', 'M', 'W', 'E', 'L', 'L',
            'I', 'A', 'M', 'G', 'O', 'O', 'D',
            'I', 'A', 'M', 'F', 'I', 'N', 'E',
            'O', 'K', 'A', 'Y',
            'G', 'O', 'O', 'D', 'G', 'R', 'E', 'A', 'T',
            'B', 'A', 'D', 'S', 'A', 'D', 'N', 'O', 'T',
            'T', 'I', 'R', 'E', 'D', 'H', 'A', 'P', 'P', 'Y',
            'Y', 'E', 'S', 'N', 'O',
            'A', 'E', 'I', 'O', 'U', 'L', 'N', 'S', 'T', 'R'
        )
        return helpfulSequence.shuffled()
    }

    fun getRandomLetter(): Char = weightedLetters.random()

    fun getInitialLetterQueue(): List<Char> {
        return listOf(
            'G', 'O', 'O', 'D',
            'W', 'E', 'L', 'L',
            'F', 'I', 'N', 'E',
            'O', 'K',
            'B', 'A', 'D',
            'S', 'A', 'D',
            'Y', 'E', 'S',
            'N', 'O',
            'I', 'A', 'M',
            'T', 'I', 'R', 'E', 'D',
            'H', 'A', 'P', 'P', 'Y',
            'A', 'E', 'I', 'O', 'U', 'L', 'N', 'S', 'T', 'R'
        )
    }
}

// Game grid cell
data class GridCell(
    val letter: Char? = null,
    val isSelected: Boolean = false
)

@Composable
fun WordGameScreen(
    gameGrid: List<List<Char?>>,
    fallingLetter: Char?,
    fallingX: Int,
    fallingY: Int,
    selectedCells: List<Pair<Int, Int>>,
    isSelecting: Boolean,
    formedWords: List<String>,
    isPaused: Boolean,
    draggingCell: Pair<Int, Int>? = null,
    dragOffsetX: Float = 0f,
    dragOffsetY: Float = 0f,
    previewGrid: List<List<Char?>>? = null,  // Grid showing where letters will be after drop
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onMoveDown: () -> Unit,
    onDrop: () -> Unit,
    onCellTap: (Int, Int) -> Unit,
    onConfirmSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onClearGrid: () -> Unit,
    onStartDrag: (Int, Int) -> Unit = { _, _ -> },
    onUpdateDrag: (Float, Float) -> Unit = { _, _ -> },
    onEndDrag: () -> Unit = { },
    onCancelDrag: () -> Unit = { },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var cellSizeDp by remember { mutableStateOf(40.dp) }
    var cellSizePxState by remember { mutableFloatStateOf(40f) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Game grid with drag support
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                cellSizeDp = minOf(this.maxWidth / 8, this.maxHeight / 12)
                val cellSizePx = with(density) { cellSizeDp.toPx() }
                cellSizePxState = cellSizePx

                // Use preview grid when dragging, otherwise use actual grid
                val displayGrid = previewGrid ?: gameGrid

                // Grid background and gesture handling
                Box(
                    modifier = Modifier
                        .fillMaxSize()

                        .pointerInput(isPaused, isSelecting, draggingCell, gameGrid) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // Don't start drag if paused or selecting
                                    if (isPaused || isSelecting) return@detectDragGestures

                                    // Add tolerance for better touch detection on different devices
                                    val col = (offset.x / cellSizePx).toInt().coerceIn(0, 7)
                                    val row = (offset.y / cellSizePx).toInt().coerceIn(0, 11)

                                    // Check the cell and adjacent cells for better hit detection
                                    val letter = gameGrid.getOrNull(row)?.getOrNull(col)
                                    if (letter != null) {
                                        onStartDrag(row, col)
                                        vibrate(context, 20, 100)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onUpdateDrag(dragAmount.x, dragAmount.y)
                                },
                                onDragEnd = {
                                    onEndDrag()
                                    vibrate(context, 15, 80)
                                },
                                onDragCancel = {
                                    onCancelDrag()
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val col = (offset.x / cellSizePx).toInt().coerceIn(0, 7)
                                val row = (offset.y / cellSizePx).toInt().coerceIn(0, 11)
                                onCellTap(row, col)
                            }
                        }
                ) {
                    // Draw grid lines
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (row in 0..12) {
                            drawLine(
                                Color.Gray.copy(alpha = 0.3f),
                                Offset(0f, row * cellSizePx),
                                Offset(8 * cellSizePx, row * cellSizePx),
                                strokeWidth = 1f
                            )
                        }
                        for (col in 0..8) {
                            drawLine(
                                Color.Gray.copy(alpha = 0.3f),
                                Offset(col * cellSizePx, 0f),
                                Offset(col * cellSizePx, 12 * cellSizePx),
                                strokeWidth = 1f
                            )
                        }
                    }

                    // Render letters from display grid (with animations for other letters)
                    displayGrid.forEachIndexed { row, rowList ->
                        rowList.forEachIndexed { col, letter ->
                            if (letter != null) {
                                val isBeingDragged = draggingCell?.first == row && draggingCell.second == col
                                val isSelected = selectedCells.contains(Pair(row, col))

                                if (!isBeingDragged) {
                                    // Check if this letter was somewhere else in original grid (for animation)
                                    val isShifted = previewGrid != null &&
                                            gameGrid.getOrNull(row)?.getOrNull(col) != letter

                                    Box(
                                        modifier = Modifier
                                            .offset(
                                                x = cellSizeDp * col,
                                                y = cellSizeDp * row
                                            )
                                            .size(cellSizeDp)
                                            .padding(1.dp)
                                            .graphicsLayer {
                                                // Slight wobble for shifted letters
                                                if (isShifted) {
                                                    scaleX = 0.95f
                                                    scaleY = 0.95f
                                                }
                                            }
                                            .background(
                                                when {
                                                    isSelected -> Color(0xFF4CAF50)
                                                    isShifted -> Color(0xFF5A5A5A)  // Slightly lighter when shifting
                                                    else -> Color(0xFF424242)
                                                },
                                                RoundedCornerShape(4.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = letter.toString(),
                                            color = Color.White,
                                            fontSize = (cellSizeDp.value * 0.5f).sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Dragged letter (rendered on top, follows finger exactly)
                    draggingCell?.let { (row, col) ->
                        val letter = gameGrid.getOrNull(row)?.getOrNull(col)
                        if (letter != null) {
                            val offsetXDp = with(density) { dragOffsetX.toDp() }
                            val offsetYDp = with(density) { dragOffsetY.toDp() }

                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = cellSizeDp * col + offsetXDp,
                                        y = cellSizeDp * row + offsetYDp
                                    )
                                    .size(cellSizeDp)
                                    .padding(1.dp)
                                    .graphicsLayer {
                                        scaleX = 1.15f
                                        scaleY = 1.15f
                                    }
                                    .shadow(12.dp, RoundedCornerShape(4.dp))
                                    .background(
                                        Color(0xFF2196F3),
                                        RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter.toString(),
                                    color = Color.White,
                                    fontSize = (cellSizeDp.value * 0.5f).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Falling letter (only when not dragging)
                    if (fallingLetter != null && draggingCell == null) {
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = cellSizeDp * fallingX,
                                    y = cellSizeDp * fallingY
                                )
                                .size(cellSizeDp)
                                .padding(1.dp)
                                .background(Color(0xFFE88617), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fallingLetter.toString(),
                                color = Color.White,
                                fontSize = (cellSizeDp.value * 0.5f).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onMoveLeft,
                    modifier = Modifier.size(50.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                ) {
                    Text("←", fontSize = 20.sp)
                }
                Button(
                    onClick = onMoveDown,
                    modifier = Modifier.size(50.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                ) {
                    Text("↓", fontSize = 20.sp)
                }
                Button(
                    onClick = onMoveRight,
                    modifier = Modifier.size(50.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                ) {
                    Text("→", fontSize = 20.sp)
                }
                Button(
                    onClick = onDrop,
                    modifier = Modifier.size(50.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE88617))
                ) {
                    Text("⬇", fontSize = 20.sp)
                }
            }

            if (isSelecting && selectedCells.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onConfirmSelection,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("✓", fontSize = 18.sp)
                    }
                    Button(
                        onClick = onCancelSelection,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("✗", fontSize = 18.sp)
                    }
                }
            }

            Button(
                onClick = onClearGrid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E9E9E))
            ) {
                Text("Clear", fontSize = 12.sp)
            }
        }

        Text(
            text = if (draggingCell != null) {
                "Drag to move • Release to place"
            } else if (isSelecting) {
                "Tap letters to select • ✓ to confirm"
            } else {
                "Tap to select • Hold & drag to move"
            },
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

// Helper function to check if selected cells form a valid connected word
fun validateWordSelection(
    grid: List<List<Char?>>,
    selectedCells: List<Pair<Int, Int>>
): Pair<Boolean, String> {
    if (selectedCells.isEmpty()) return Pair(false, "")

    val letters = selectedCells.mapNotNull { (row, col) ->
        grid.getOrNull(row)?.getOrNull(col)
    }

    if (letters.size != selectedCells.size) return Pair(false, "")

    if (selectedCells.size == 1) {
        val letter = letters[0]
        return if (letter.lowercaseChar() in WordCategories.singleLetterWords) {
            Pair(true, letter.toString().uppercase())
        } else {
            Pair(false, "")
        }
    }

    fun areAdjacent(c1: Pair<Int, Int>, c2: Pair<Int, Int>): Boolean {
        val rowDiff = kotlin.math.abs(c1.first - c2.first)
        val colDiff = kotlin.math.abs(c1.second - c2.second)
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
    }

    for (i in 1 until selectedCells.size) {
        val currentCell = selectedCells[i]
        val previousCells = selectedCells.subList(0, i)
        val isConnected = previousCells.any { areAdjacent(it, currentCell) }
        if (!isConnected) {
            return Pair(false, "")
        }
    }

    val word = letters.joinToString("")
    return Pair(true, word.uppercase())
}

fun isWordValid(word: String): Boolean {
    return WordCategories.isValidWord(word)
}

fun removeLettersAndShift(
    grid: List<List<Char?>>,
    cellsToRemove: List<Pair<Int, Int>>
): List<List<Char?>> {
    val newGrid = grid.map { it.toMutableList() }.toMutableList()

    for ((row, col) in cellsToRemove) {
        newGrid[row][col] = null
    }

    for (col in 0 until 8) {
        val lettersInColumn = mutableListOf<Char>()
        for (row in 11 downTo 0) {
            newGrid[row][col]?.let { lettersInColumn.add(it) }
        }

        for (row in 11 downTo 0) {
            val index = 11 - row
            newGrid[row][col] = if (index < lettersInColumn.size) lettersInColumn[index] else null
        }
    }

    return newGrid.map { it.toList() }
}

fun isGameOver(grid: List<List<Char?>>): Boolean {
    return grid[0].any { it != null }
}

fun findLandingRow(grid: List<List<Char?>>, col: Int, currentRow: Int): Int {
    var landingRow = currentRow
    for (row in currentRow + 1 until 12) {
        if (grid[row][col] != null) {
            break
        }
        landingRow = row
    }
    return landingRow
}

fun placeLetter(grid: List<List<Char?>>, row: Int, col: Int, letter: Char): List<List<Char?>> {
    val newGrid = grid.map { it.toMutableList() }.toMutableList()
    newGrid[row][col] = letter
    return newGrid.map { it.toList() }
}

fun applyGravityToGrid(grid: List<List<Char?>>): List<List<Char?>> {
    val newGrid = MutableList(12) { MutableList<Char?>(8) { null } }

    for (col in 0..7) {
        val letters = mutableListOf<Char>()
        for (row in 11 downTo 0) {
            grid.getOrNull(row)?.getOrNull(col)?.let { letters.add(it) }
        }
        var placeRow = 11
        for (letter in letters) {
            newGrid[placeRow][col] = letter
            placeRow--
        }
    }

    return newGrid.map { it.toList() }
}