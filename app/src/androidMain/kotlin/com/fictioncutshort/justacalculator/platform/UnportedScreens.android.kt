package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

@Composable
actual fun PlatformPhoneCameraApp(onClose: () -> Unit) =
    com.fictioncutshort.justacalculator.ui.components.PhoneCameraApp(onClose)

@Composable
actual fun PlatformPhonePicturesApp(onClose: () -> Unit) =
    com.fictioncutshort.justacalculator.ui.components.PhonePicturesApp(onClose)

@Composable
actual fun PlatformModelViewer(modelFile: String, modifier: Modifier) {
    com.fictioncutshort.justacalculator.ui.screens.SceneViewModel(modelFile, modifier)
}

@Composable
actual fun PlatformDoor4Room(modifier: Modifier, onComplete: () -> Unit) =
    com.fictioncutshort.justacalculator.ui.screens.Door4Room(modifier, onComplete)

@Composable
actual fun PlatformBuilding7VanityRoom(modifier: Modifier, onComplete: () -> Unit) =
    com.fictioncutshort.justacalculator.ui.screens.Building7VanityRoom(modifier, onComplete)

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

