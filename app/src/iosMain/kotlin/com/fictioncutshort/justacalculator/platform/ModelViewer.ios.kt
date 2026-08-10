package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fictioncutshort.justacalculator.ui.screens.ModelViewerGl

@Composable
actual fun PlatformModelViewer(modelFile: String, modifier: Modifier) {
    ModelViewerGl(modelFile, modifier)
}
