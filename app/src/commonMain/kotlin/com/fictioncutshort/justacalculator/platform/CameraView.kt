package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The live camera surface underneath the scan overlay.
 *
 * Deliberately narrower than "a camera preview": the story's viewfinder is
 * mostly Compose drawing, so this seam carries only what genuinely needs a
 * capture session — pixels on screen, a coarse colour readout, and one saved
 * photo. Nothing about the camera escapes it.
 *
 * Android resolves its own lifecycle owner internally, which is why no
 * `LifecycleOwner` appears here; that parameter used to be threaded through
 * every layout purely to reach the camera, and UIKit has no equivalent.
 *
 * @param onScanSamples receives one packed ARGB value per grid cell, row-major,
 *   `scanRows * scanCols` long. Called on a camera thread, once per analysed
 *   frame, with a fresh array each time.
 * @param autoCaptureLabel when non-null, one photo is taken a couple of seconds
 *   after the session binds and saved to the gallery, named with this label.
 */
@Composable
expect fun PlatformCameraSurface(
    modifier: Modifier = Modifier,
    useFrontCamera: Boolean = false,
    scanRows: Int = 0,
    scanCols: Int = 0,
    onScanSamples: ((IntArray) -> Unit)? = null,
    autoCaptureLabel: String? = null,
)
