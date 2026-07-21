package com.fictioncutshort.justacalculator.util


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
import kotlin.math.abs

// Word categories for response detection
object WordCategories {
    fun isValidWord(word: String): Boolean {
        val lower = word.lowercase()

        // Accept mood words
        if (lower in positiveWords) return true
        if (lower in negativeWords) return true
        if (lower in neutralWords) return true
        if (lower.length == 1 && lower[0] in singleLetterWords) return true

        // Accept binary yes/no synonyms (used by the new branching gates)
        if (lower in binaryYes || lower in binaryNo) return true

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

        // Accept activity words (neutral branch, step 142)
        if (lower in activities) return true

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

    // Single letter responses — intentionally empty. Lone letters (e.g. tapping
    // 'O' on the way to spelling "OK", or 'I' meaning the pronoun) were
    // submitting prematurely and accepting the question as answered. Force
    // the user to spell at least two letters before isValidWord can return true.
    val singleLetterWords = emptySet<Char>()

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
        "moroccan", "egyptian", "southern", "comfort", "asian", "oriental",
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

    // === BINARY YES/NO ===
    // Used for gate questions in the rewritten word game (steps 121, 122, 131,
    // 132, 141). Anything outside these sets triggers a "this is a binary
    // question" retry.
    val binaryYes = setOf("yes", "yeah", "yep", "yup", "sure", "ok", "okay")
    val binaryNo = setOf("no", "nope", "nah", "never", "not")

    // === ACTIVITIES (neutral branch, step 142) ===
    // The user must spell something the calculator recognises; any other word
    // triggers the "I've never heard of that" retry. Includes both base verb
    // and -ing form so either drops in.
    val activities = setOf(
        // Active / outdoor
        "run", "running", "walk", "walking", "hike", "hiking", "swim", "swimming",
        "bike", "biking", "cycle", "cycling", "ski", "skiing", "surf", "surfing",
        "skate", "skating", "climb", "climbing", "jog", "jogging",
        // Indoor / fitness
        "yoga", "gym", "exercise", "stretch", "stretching", "pilates", "lift",
        "lifting", "train", "training", "dance", "dancing",
        // Creative
        "read", "reading", "write", "writing", "draw", "drawing", "paint",
        "painting", "sing", "singing", "play", "playing", "music",
        "knit", "knitting", "sew", "sewing", "bake", "baking",
        "cook", "cooking", "garden", "gardening", "craft", "crafting",
        // Sedentary / comfort
        "eat", "eating", "sleep", "sleeping", "nap", "rest", "resting",
        "watch", "watching", "listen", "listening", "scroll", "scrolling",
        "browse", "browsing", "drink", "drinking", "smoke", "smoking",
        "shop", "shopping",
        // Social
        "talk", "talking", "call", "calling", "text", "texting",
        "party", "chat", "chatting",
        // Mental
        "study", "studying", "work", "working", "meditate", "meditating",
        "think", "thinking", "pray", "praying",
        // Misc
        "drive", "driving", "travel", "travelling", "traveling",
        "cry", "crying", "breathe", "breathing",
        "game", "gaming", "tv", "podcast", "movies", "puzzle", "chess",
        "fish", "fishing", "golf"
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
    fun isBinaryYes(word: String): Boolean = word.lowercase() in binaryYes
    fun isBinaryNo(word: String): Boolean = word.lowercase() in binaryNo
    fun isBinary(word: String): Boolean = isBinaryYes(word) || isBinaryNo(word)
    fun isActivity(word: String): Boolean = word.lowercase() in activities
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
        // Two-section queue. The FRONT is the prefill guarantee: one full copy
        // of every branch-critical answer word, lightly shuffled so letters
        // aren't laid out in word-order on the grid. The BACK is the bulk
        // pool with extra copies for variety and runtime refills after a
        // word is removed. Because the prefill consumes letters strictly
        // from the front of the queue, every answer the calculator might
        // demand is reliably present on the grid the moment the game opens.
        val guaranteed = mutableListOf<Char>()
        fun ensure(word: String) { guaranteed.addAll(word.toList()) }

        // ── Mood greeting (step 119) — all three branches must be reachable.
        ensure("GOOD")      // positive
        ensure("HAPPY")     // positive
        ensure("BAD")       // negative
        ensure("TIRED")     // negative
        ensure("MEH")       // neutral
        ensure("OKAY")      // neutral

        // ── Binary YES/NO — every branch hits a binary gate.
        ensure("YES")
        ensure("NO")

        // ── Positive branch followups: colour (123), season (125), cuisine (127).
        ensure("RED")
        ensure("BLUE")
        ensure("SPRING")
        ensure("THAI")
        ensure("ASIAN")

        // ── Negative branch followups: walk frequency / death freq.
        ensure("OFTEN")
        ensure("NEVER")

        // ── Neutral branch followups: activity (step 142).
        ensure("RUN")
        ensure("EAT")

        // Light shuffle so the guaranteed letters aren't laid out spelling
        // each word verbatim, but still all land in the prefill (which draws
        // from the front of the queue).
        guaranteed.shuffle()

        // Bulk pool: extras for variety + runtime refill after blocks remove.
        val bulk = mutableListOf<Char>()
        fun seed(word: String, copies: Int = 2) {
            repeat(copies) { bulk.addAll(word.toList()) }
        }

        // More mood options (variety on the opener).
        seed("WELL"); seed("FINE"); seed("GREAT"); seed("SAD"); seed("OK")

        // Extra colours / seasons / cuisines / activities so the user can
        // build multiple valid answers per branch instead of just one.
        seed("PINK"); seed("BROWN"); seed("GREEN")
        seed("SUMMER"); seed("AUTUMN"); seed("WINTER")
        seed("SUSHI"); seed("INDIAN"); seed("BURGER")
        seed("WALK"); seed("READ"); seed("SLEEP"); seed("SING")
        seed("COOK"); seed("SWIM"); seed("YOGA"); seed("BAKE")
        seed("DAILY")

        // Filler vowels + common consonants for general flexibility.
        bulk.addAll(listOf('A', 'E', 'I', 'O', 'U'))
        bulk.addAll(listOf('L', 'N', 'S', 'T', 'R'))
        bulk.shuffle()

        return guaranteed + bulk
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
        val rowDiff = abs(c1.first - c2.first)
        val colDiff = abs(c1.second - c2.second)
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