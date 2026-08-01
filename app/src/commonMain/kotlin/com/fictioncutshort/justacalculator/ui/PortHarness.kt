package com.fictioncutshort.justacalculator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.ui.components.PortraitCalculatorContent
import com.fictioncutshort.justacalculator.ui.components.TopBezelBar
import com.fictioncutshort.justacalculator.util.RetroCream
import com.fictioncutshort.justacalculator.util.RetroDisplayGreen
import com.fictioncutshort.justacalculator.util.rememberResponsiveDimensions

/**
 * TEMPORARY — drives the *real* [PortraitCalculatorContent] with the *real*
 * [CalculatorActions] state, so the story's message field, button handling and
 * persistence are genuinely live on iOS.
 *
 * What `MainActivity.CalculatorScreen` still owns and this does not: the terms
 * screen, orientation switching to the landscape layout, the effects/dormancy/
 * auto-progress controllers that animate the story forward on timers, and every
 * overlay (phone, console, ad cards, city, minigames).
 *
 * Delete this file once `CalculatorScreen` is ported.
 */
@Composable
fun PortHarnessCalculator() {
    // The same singleton MainActivity uses, so progress persists across launches.
    val state = remember {
        CalculatorActions.liveState
            ?: mutableStateOf(CalculatorActions.loadInitialState()).also {
                CalculatorActions.liveState = it
            }
    }
    LaunchedEffect(Unit) { CalculatorActions.liveState = state }

    val current = state.value
    val dimensions = rememberResponsiveDimensions()

    val displayExpression = if (current.expression.isNotEmpty()) {
        current.expression
    } else {
        buildString {
            append(current.number1)
            if (current.operation != null) append(current.operation)
            if (current.number2.isNotEmpty()) append(current.number2)
        }
    }
    val displayText = displayExpression.ifEmpty { "0" }

    // After the story ends the clear key wears the name the calculator gave the
    // player: "C" becomes "RAD" (still clears — normalised back in the grid).
    val clearKey = if (current.storyComplete) "RAD" else "C"
    val buttonLayout = listOf(
        listOf(clearKey, "( )", "%", "/"),
        listOf("7", "8", "9", "*"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("DEL", "0", ".", "="),
    )

    val textColor = if (current.invertedColors) RetroDisplayGreen else Color(0xFF2D2D2D)
    val backgroundColor = if (current.invertedColors) Color(0xFF1A1A1A) else RetroCream

    Column(
        modifier = Modifier.fillMaxSize().background(backgroundColor),
    ) {
        TopBezelBar(invertedColors = current.invertedColors)
        PortraitCalculatorContent(
            state = state,
            current = current,
            displayText = displayText,
            buttonLayout = buttonLayout,
            dimensions = dimensions,
            textColor = textColor,
            // Shake is driven by EffectsController, which is not ported yet.
            currentShakeIntensity = 0f,
        )
    }
}
