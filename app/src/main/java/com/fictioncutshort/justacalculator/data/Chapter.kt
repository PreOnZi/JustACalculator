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
 * @property id Unique identifier (0-17)
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
 * - Ch 12-13: Recovery - repair mini-games, keyboard chaos
 * - Ch 13.5: Phone Detour - attempting to build a phone interface
 * - Ch 14-16: Console quest, word game, and final rant
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
        name = "Chapter 13.5: Phone Detour",
        startStep = 1071,
        description = "Permissions → Talk overlay → Feedback squeal"
    ),
    Chapter(
        id = 15,
        name = "Chapter 14: Console Quest",
        startStep = 108,
        description = "Post-chaos → Downloads → Console"
    ),
    Chapter(
        id = 16,
        name = "Chapter 15: Word Game",
        startStep = 117,
        description = "Letter game → How are you? → Rant"
    ),
    Chapter(
        id = 17,
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
    // Chapter 1-4: First contact through Age & Identity (0-18)
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,

    // Chapter 5: Camera (19-24) + camera active
    19, 20, 21, 22, 23, 24, 191,

    // Chapter 6-7: Getting Personal & Self Discovery (25-59)
    25, 26, 27, 28, 29, 30, 40, 41, 42, 50, 51,

    // Chapter 8: History (60-62)
    60, 61, 62,

    // Chapter 9: Taste & Senses (63-79)
    63, 64, 65, 66, 67, 68, 69, 70, 71, 72,

    // Chapter 10: Revelation/Wikipedia (80-88)
    80, 81, 82, 83, 84, 85, 86, 87, 88,

    // Chapter 11: Crisis & Repair (89-101)
    89, 90, 91, 93, 94, 95, 96, 97, 98, 99,

    // Chapter 12: Recovery (102-104)
    102, 103, 104,

    // Chapter 13: Keyboard Chaos (105-107)
    105, 106, 107,

    // Chapter 14: Console Quest (108-116)
    108, 109, 110, 111, 112, 113, 114, 115, 116,

    // Chapter 15: Word Game (117-149)
    117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129,
    130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142,
    143, 144, 145, 146, 147, 148, 149,

    // Chapter 16: Rant (150-167)
    150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167,

    // Branch steps
    982,

    // Phone detour
    1071, 1074, 1083,

    // Downloads fallback
    1121
)

/**
 * Steps that auto-progress without user input.
 *
 * These steps should NOT allow the user to skip with ++ as they are part
 * of timed sequences or animations.
 */
val AUTO_PROGRESS_STEPS = listOf(92, 100, 901, 911, 912, 913, 971, 981, 1072, 1073, 1075, 1076, 1077, 1078, 1079, 1080, 1081, 1082, 1085, 1086)