package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Live camera preview.
 *
 * Android keeps the existing CameraX implementation; it resolves its own
 * lifecycle owner internally, which is why no `LifecycleOwner` appears here —
 * that parameter used to be threaded through every layout purely to reach the
 * camera, and UIKit has no equivalent to thread.
 *
 * The iOS actual is a placeholder until the AVFoundation port lands.
 */
@Composable
expect fun PlatformCameraPreview(
    modifier: Modifier = Modifier,
    useFrontCamera: Boolean = false,
)
