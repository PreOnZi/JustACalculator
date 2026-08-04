package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

@Composable
actual fun PlatformModelViewer(modelFile: String, modifier: Modifier) {
    com.fictioncutshort.justacalculator.ui.screens.SceneViewModel(modelFile, modifier)
}

@Composable
actual fun PlatformDoor4Room(modifier: Modifier, onComplete: () -> Unit) =
    com.fictioncutshort.justacalculator.ui.screens.Door4Room(modifier, onComplete)

