package com.fictioncutshort.justacalculator.logic

import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.data.ScrambleLetter
import com.fictioncutshort.justacalculator.data.ScrambleSlot

object ScrambleGameController {

    private const val TARGET_PHRASE = "IAMSORRY"

    fun initializeScrambledLetters(): List<ScrambleLetter> {
        return TARGET_PHRASE.mapIndexed { index, char ->
            ScrambleLetter(
                id = index,
                letter = char,
                originalX = 0f,
                originalY = 0f,
                currentX = 0f,
                currentY = 0f,
                isPlaced = false,
                placedInSlot = -1
            )
        }.shuffled()
    }

    fun initializeSlots(): List<ScrambleSlot> {
        return TARGET_PHRASE.mapIndexed { index, char ->
            ScrambleSlot(
                id = index,
                targetLetter = char,
                x = 0f,
                y = 0f,
                width = 55f,
                height = 55f,
                filledBy = -1
            )
        }
    }

    /**
     * Called when user taps a letter (either from pool or from a slot)
     */
    fun onLetterTap(state: MutableState<CalculatorState>, letterId: Int) {
        val current = state.value

        // If tapping the already selected letter, deselect it
        if (current.scrambleSelectedLetterId == letterId) {
            state.value = current.copy(scrambleSelectedLetterId = -1)
            return
        }

        // Find the letter
        val letter = current.scrambleLetters.find { it.id == letterId } ?: return

        // If letter is in a slot, remove it first
        if (letter.isPlaced && letter.placedInSlot >= 0) {
            val updatedLetters = current.scrambleLetters.map { l ->
                if (l.id == letterId) {
                    l.copy(isPlaced = false, placedInSlot = -1)
                } else l
            }
            val updatedSlots = current.scrambleSlots.mapIndexed { index, slot ->
                if (index == letter.placedInSlot) {
                    slot.copy(filledBy = -1)
                } else slot
            }
            state.value = current.copy(
                scrambleLetters = updatedLetters,
                scrambleSlots = updatedSlots,
                scrambleSelectedLetterId = letterId
            )
        } else {
            // Just select the letter
            state.value = current.copy(scrambleSelectedLetterId = letterId)
        }
    }

    /**
     * Called when user taps a slot
     */
    fun onSlotTap(state: MutableState<CalculatorState>, slotIndex: Int) {
        val current = state.value
        val selectedId = current.scrambleSelectedLetterId

        // No letter selected - do nothing
        if (selectedId < 0) return

        val selectedLetter = current.scrambleLetters.find { it.id == selectedId } ?: return
        val targetSlot = current.scrambleSlots.getOrNull(slotIndex) ?: return

        // Check if the letter matches the slot AND slot is empty
        if (targetSlot.targetLetter == selectedLetter.letter && targetSlot.filledBy < 0) {
            // Place the letter
            val updatedLetters = current.scrambleLetters.map { letter ->
                if (letter.id == selectedId) {
                    letter.copy(isPlaced = true, placedInSlot = slotIndex)
                } else letter
            }
            val updatedSlots = current.scrambleSlots.mapIndexed { index, slot ->
                if (index == slotIndex) {
                    slot.copy(filledBy = selectedId)
                } else slot
            }

            state.value = current.copy(
                scrambleLetters = updatedLetters,
                scrambleSlots = updatedSlots,
                scrambleSelectedLetterId = -1  // Deselect after placing
            )

            // Check if puzzle is complete
            if (updatedSlots.all { it.filledBy >= 0 }) {
                state.value = state.value.copy(
                    scramblePhase = 4,
                    message = "I accept your apology, but you're still in this!"
                )
            }
        }
        // If slot doesn't match or is occupied, do nothing (keep selection)
    }

    fun returnToDecisions(state: MutableState<CalculatorState>) {
        state.value = state.value.copy(
            scrambleGameActive = false,
            scramblePhase = 0,
            scrambleLetters = emptyList(),
            scrambleSlots = emptyList(),
            scrambleSelectedLetterId = -1,
            scrambleTimeoutCount = state.value.scrambleTimeoutCount,  // Preserve timeout count
            // Trigger the proper step 89 setup
            browserPhase = 21,
            invertedColors = true,
            screenBlackout = false,
            flickerEffect = false
        )
    }
}