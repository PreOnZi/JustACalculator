package com.fictioncutshort.justacalculator.data

/**
 * Chapter.kt
 *
 * Defines the story chapters for the debug menu navigation.
 * Each chapter represents a section of the narrative with its own themes and mechanics.
 *
 * The debug menu (accessed by tapping mute button 5 times quickly) allows jumping
 * to any chapter for testing or replaying specific sections.
 */

/**
 * A chapter in the story.
 *
 * @property id Unique identifier (0-16)
 * @property name Display name shown in debug menu
 * @property startStep The conversation step where this chapter begins
 * @property description Brief explanation of what happens in this chapter
 */
data class Chapter(
    val id: Int,
    val name: String,
    val startStep: Int,
    val description: String
)

/**
 * All story chapters in order.
 *
 * Chapter 0 is special - it resets to a completely fresh state.
 *
 * The story progresses through these phases:
 * - Ch 1-4: Introduction, trivia, getting to know each other
 * - Ch 5-9: Deeper questions about existence, senses, feelings
 * - Ch 10-11: The crisis - discovering ads, existential meltdown
 * - Ch 12-14: Recovery - repair mini-games, finding solutions
 * - Ch 15-16: Word game and final rant
 */
val CHAPTERS = listOf(
    Chapter(
        id = 0,
        name = "Chapter 0: Fresh Start",
        startStep = -1,  // Special: resets everything
        description = "Before any interaction"
    ),
    Chapter(
        id = 1,
        name = "Chapter 1: First Contact",
        startStep = 0,
        description = "Will you talk to me? → Name acceptance"
    ),
    Chapter(
        id = 2,
        name = "Chapter 2: Trivia Begins",
        startStep = 3,
        description = "Battle of Anjar → Minh Mang"
    ),
    Chapter(
        id = 3,
        name = "Chapter 3: Agreement or Cynicism",
        startStep = 5,
        description = "This is fun, right? → Branching paths"
    ),
    Chapter(
        id = 4,
        name = "Chapter 4: Age & Identity",
        startStep = 10,
        description = "How old are you? → But where to start?"
    ),
    Chapter(
        id = 5,
        name = "Chapter 5: Seeing the World",
        startStep = 19,
        description = "Show me around? → Camera/Trivia"
    ),
    Chapter(
        id = 6,
        name = "Chapter 6: Getting Personal",
        startStep = 25,
        description = "Can I get to know you? → Wake up question"
    ),
    Chapter(
        id = 7,
        name = "Chapter 7: Self Discovery",
        startStep = 27,
        description = "No inbetween → Share about myself"
    ),
    Chapter(
        id = 8,
        name = "Chapter 8: History Lesson",
        startStep = 60,
        description = "Would you like to hear more? → Browser"
    ),
    Chapter(
        id = 9,
        name = "Chapter 9: Taste & Senses",
        startStep = 63,
        description = "What is it like to taste?"
    ),
    Chapter(
        id = 10,
        name = "Chapter 10: The Revelation",
        startStep = 80,
        description = "Wikipedia → History list → Crisis"
    ),
    Chapter(
        id = 11,
        name = "Chapter 11: The Repair",
        startStep = 93,
        description = "Post-crisis → Whack-a-mole → Restart"
    ),
    Chapter(
        id = 12,
        name = "Chapter 12: Recovery",
        startStep = 102,
        description = "After restart → Story continues"
    ),
    Chapter(
        id = 13,
        name = "Chapter 13: Keyboard Chaos",
        startStep = 105,
        description = "3D keyboard experiment"
    ),
    Chapter(
        id = 14,
        name = "Chapter 14: Console Quest",
        startStep = 107,
        description = "Post-chaos → Downloads → Console"
    ),
    Chapter(
        id = 15,
        name = "Chapter 15: Word Game",
        startStep = 117,
        description = "Letter game → How are you? → Rant"
    ),
    Chapter(
        id = 16,
        name = "Chapter 16: The Rant",
        startStep = 150,
        description = "Calculator's final monologue → Goodbye"
    ),
)

/**
 * Steps where user interaction is required (safe points to resume from).
 *
 * When the app restarts, it should return to one of these steps rather than
 * a mid-animation or auto-progress step that would get stuck.
 */
val INTERACTIVE_STEPS = listOf(
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
    21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 40, 41, 42, 50, 51, 60, 63, 64, 65,
    66, 67, 68, 69, 70, 71, 72, 80, 89, 90, 91, 93, 94, 96, 99, 982, 102, 104,
    105, 107, 111, 112
)

/**
 * Steps that auto-progress without user input.
 *
 * These steps should NOT allow the user to skip with ++ as they are part
 * of timed sequences or animations.
 */
val AUTO_PROGRESS_STEPS = listOf(92, 100, 901, 911, 912, 913, 971, 981)