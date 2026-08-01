package com.fictioncutshort.justacalculator.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

/**
 * PLACEHOLDER — the AVFoundation capture session is not ported yet.
 *
 * Deliberately renders a visible, labelled surface rather than an empty Box: the
 * camera scenes are story beats, and a silently blank rectangle would look like
 * a layout bug rather than unfinished work.
 */
@Composable
actual fun PlatformCameraPreview(
    modifier: Modifier,
    useFrontCamera: Boolean,
) {
    Box(
        modifier = modifier.background(Color(0xFF14110E)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "[ camera not ported yet ]",
            color = Color(0xFF8A7F70),
            textAlign = TextAlign.Center,
        )
    }
}
