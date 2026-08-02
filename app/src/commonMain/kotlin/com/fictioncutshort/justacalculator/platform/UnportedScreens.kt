package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.fictioncutshort.justacalculator.data.Chapter

/**
 * Seams for the parts of the story that are still Android-only.
 *
 * These exist so `CalculatorScreen` can live in commonMain now rather than
 * waiting on the 3D city and the audio-capture work. The iOS actuals render a
 * labelled placeholder instead of failing silently — an unfinished beat should
 * look unfinished, not look like a bug.
 *
 * Each one disappears when its real implementation ports; nothing else has to
 * change at the call site.
 */

/** The ad-card stack and everything downstream of it — pexeso, then the 3D city. */
@Composable
expect fun PlatformAdCardStack(
    onPexesoComplete: () -> Unit,
    startAtCity: Boolean,
    startAtPexeso: Boolean,
    onStageChanged: (String) -> Unit,
    onCityEntered: () -> Unit,
    onJumpToPhase1: (Chapter) -> Unit,
)

/** The fake phone home screen (step 1086). */
@Composable
expect fun PlatformHomeScreenOverlay(
    audioHandler: TypingClicker?,
    onIconClick: (String) -> Unit,
    onReturnToCalculator: () -> Unit,
)

/**
 * The realtime mic-echo handler. Android returns the real TalkAudioHandler;
 * iOS returns a no-op until the AVAudioEngine port lands, so the typing click
 * is silent but the story still advances.
 */
expect fun createTalkAudioHandler(context: AppContext): TypingClicker

/** The debug-menu password gate; lives in the city debug menu on Android. */
@Composable
expect fun PlatformDebugPasswordGate(onUnlock: () -> Unit, onCancel: () -> Unit)

/**
 * Renders an OBJ model to a bitmap for use as a 2D icon.
 *
 * Android does this with an offscreen EGL pbuffer context. iOS returns null
 * until that is ported — callers already treat null as "model not ready" and
 * fall back to drawing without it, so the screens stay usable.
 */
@Composable
expect fun rememberModelIcon(
    objPath: String,
    mtlPath: String? = null,
    sizePx: Int = 160,
    tilt: Float = -22f,
    turn: Float = 32f,
    colorGamma: Float = 1f,
    fitSpan: Float = 1.7f,
): ImageBitmap?
