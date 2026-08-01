package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import com.fictioncutshort.justacalculator.data.Chapter
import com.fictioncutshort.justacalculator.logic.TalkAudioHandler
import com.fictioncutshort.justacalculator.ui.components.HomeScreenOverlay
import com.fictioncutshort.justacalculator.ui.screens.AdCardStack

@Composable
actual fun PlatformAdCardStack(
    onPexesoComplete: () -> Unit,
    startAtCity: Boolean,
    startAtPexeso: Boolean,
    onStageChanged: (String) -> Unit,
    onCityEntered: () -> Unit,
    onJumpToPhase1: (Chapter) -> Unit,
) {
    AdCardStack(
        onPexesoComplete = onPexesoComplete,
        startAtCity = startAtCity,
        startAtPexeso = startAtPexeso,
        onStageChanged = onStageChanged,
        onCityEntered = onCityEntered,
        onJumpToPhase1 = onJumpToPhase1,
    )
}

@Composable
actual fun PlatformHomeScreenOverlay(
    audioHandler: TypingClicker?,
    onIconClick: (String) -> Unit,
    onReturnToCalculator: () -> Unit,
) {
    HomeScreenOverlay(
        audioHandler = audioHandler as? TalkAudioHandler,
        onIconClick = onIconClick,
        onReturnToCalculator = onReturnToCalculator,
    )
}

actual fun createTalkAudioHandler(context: AppContext): TypingClicker =
    TalkAudioHandler(context)

@Composable
actual fun PlatformDebugPasswordGate(onUnlock: () -> Unit, onCancel: () -> Unit) {
    com.fictioncutshort.justacalculator.ui.screens.DebugPasswordGate(
        onUnlock = onUnlock,
        onCancel = onCancel,
    )
}
