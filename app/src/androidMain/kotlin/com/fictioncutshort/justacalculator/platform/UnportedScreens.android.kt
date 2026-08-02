package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
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

@Composable
actual fun rememberModelIcon(
    objPath: String,
    mtlPath: String?,
    sizePx: Int,
    tilt: Float,
    turn: Float,
    colorGamma: Float,
    fitSpan: Float,
): ImageBitmap? = com.fictioncutshort.justacalculator.ui.screens.rememberModelBitmap(
    objPath, mtlPath, sizePx, tilt, turn, colorGamma, fitSpan,
)

@Composable
actual fun PlatformModelViewer(modelFile: String, modifier: Modifier) {
    com.fictioncutshort.justacalculator.ui.screens.SceneViewModel(modelFile, modifier)
}

@Composable
actual fun PlatformDoor4Room(modifier: Modifier, onComplete: () -> Unit) =
    com.fictioncutshort.justacalculator.ui.screens.Door4Room(modifier, onComplete)

@Composable
actual fun PlatformBuilding5Map(onComplete: () -> Unit, onExit: () -> Unit) =
    com.fictioncutshort.justacalculator.ui.screens.Building5Map(onComplete, onExit)

@Composable
actual fun PlatformBuilding6Runner(onComplete: () -> Unit, onExit: () -> Unit) =
    com.fictioncutshort.justacalculator.ui.screens.Building6Runner(onComplete, onExit)

@Composable
actual fun PlatformBuilding7VanityRoom(modifier: Modifier, onComplete: () -> Unit) =
    com.fictioncutshort.justacalculator.ui.screens.Building7VanityRoom(modifier, onComplete)
