package com.fictioncutshort.justacalculator.data

data class ScrambleLetter(
    val id: Int,
    val letter: Char,
    val originalX: Float,
    val originalY: Float,
    val currentX: Float,
    val currentY: Float,
    val isPlaced: Boolean = false,
    val placedInSlot: Int = -1
)

data class ScrambleSlot(
    val id: Int,
    val targetLetter: Char,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val filledBy: Int = -1  // ID of letter placed here, -1 if empty
)