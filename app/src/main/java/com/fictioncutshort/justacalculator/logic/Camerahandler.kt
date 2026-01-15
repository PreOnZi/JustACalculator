package com.fictioncutshort.justacalculator.logic

import androidx.compose.runtime.MutableState
import com.fictioncutshort.justacalculator.data.CalculatorState

/**
 * CameraHandler.kt
 *
 * Handles camera activation and timeout during the "show me around" sequence (step 19).
 *
 * Uses CalculatorState properties:
 * - cameraActive: Boolean - whether camera viewfinder is showing
 * - cameraTimerStart: Long - timestamp when camera was opened (for 8-second timeout)
 *
 * NOTE: This handler updates state directly but does NOT handle persistence.
 * The caller (CalculatorActions) should call persistConversationStep() after
 * any step changes.
 */
object CameraHandler {

    private const val CAMERA_TIMEOUT_MS = 8000L  // 8 seconds
    private const val DOUBLE_PRESS_WINDOW_MS = 600L

    private var lastAction: String? = null
    private var lastActionTime: Long = 0L

    /**
     * Start camera mode (called when user accepts at step 19)
     */
    fun startCamera(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(
            cameraActive = true,
            cameraTimerStart = System.currentTimeMillis()
        )
    }

    /**
     * Stop camera due to timeout (8 seconds elapsed).
     * Returns the new conversation step (20) for the caller to persist.
     */
    fun stopCameraTimeout(state: MutableState<CalculatorState>, closeCamera: Boolean = true): Int {
        val current = state.value

        state.value = current.copy(
            cameraActive = !closeCamera,
            cameraTimerStart = 0L,
            number1 = "0",
            number2 = "",
            operation = null,
            conversationStep = 20,
            message = "",
            fullMessage = "I've seen enough, struggling to process everything! Thank you.",
            isTyping = true,
            isLaggyTyping = true,
            pendingAutoMessage = "Wow, I don't know what any of this was. But the shapes, the colours. I am not even sure if I saw any numbers. I am jealous. Makes one want to feel everything! Touch things... More trivia?",
            pendingAutoStep = 21,
            waitingForAutoProgress = true
        )

        return 20  // Return step for caller to persist
    }

    /**
     * Stop camera because user declined (pressed --).
     * Returns the new conversation step (21) for the caller to persist.
     */
    fun stopCameraDeclined(state: MutableState<CalculatorState>): Int {
        val current = state.value

        state.value = current.copy(
            cameraActive = false,
            cameraTimerStart = 0L,
            conversationStep = 21,
            awaitingNumber = false,
            message = "",
            fullMessage = "That's fair. Perhaps you can describe things to me eventually. More trivia?",
            isTyping = true
        )

        return 21  // Return step for caller to persist
    }

    /**
     * Simply close the camera without changing conversation state
     */
    fun closeCamera(state: MutableState<CalculatorState>) {
        val current = state.value
        state.value = current.copy(
            cameraActive = false,
            cameraTimerStart = 0L
        )
    }

    /**
     * Close camera after timeout message is shown
     */
    fun closeCameraAfterMessage(state: MutableState<CalculatorState>) {
        val current = state.value
        if (current.cameraActive) {
            state.value = current.copy(cameraActive = false)
        }
    }

    /**
     * Check if camera has timed out (8 seconds elapsed)
     */
    fun hasCameraTimedOut(state: CalculatorState): Boolean {
        if (state.cameraActive && state.cameraTimerStart > 0) {
            val elapsed = System.currentTimeMillis() - state.cameraTimerStart
            return elapsed >= CAMERA_TIMEOUT_MS
        }
        return false
    }

    /**
     * Handle input while camera is active.
     * Returns: null if no action needed, or the new step number if caller should persist.
     */
    fun handleCameraInput(state: MutableState<CalculatorState>, action: String): Int? {
        val now = System.currentTimeMillis()

        when (action) {
            "+" -> {
                if (lastAction == "+" && (now - lastActionTime) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Take a picture (just visual feedback, doesn't actually save)
                    // Could add a flash effect here
                    lastAction = null
                    lastActionTime = 0L
                } else {
                    lastAction = "+"
                    lastActionTime = now
                }
            }
            "-" -> {
                if (lastAction == "-" && (now - lastActionTime) <= DOUBLE_PRESS_WINDOW_MS) {
                    // Close camera - user declined to show around
                    lastAction = null
                    lastActionTime = 0L
                    return stopCameraDeclined(state)  // Returns 21
                } else {
                    lastAction = "-"
                    lastActionTime = now
                }
            }
            // Zoom controls and exposure changes are handled in camera composable
            // Numbers 0-9, %, (), ., C, DEL - all handled by UI
        }

        return null  // No persistence needed
    }

    /**
     * Reset double-press tracking
     */
    fun resetTracking() {
        lastAction = null
        lastActionTime = 0L
    }
}