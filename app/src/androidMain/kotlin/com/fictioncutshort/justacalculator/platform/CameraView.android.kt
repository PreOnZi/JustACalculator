package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fictioncutshort.justacalculator.ui.components.CameraPreview

@Composable
actual fun PlatformCameraPreview(
    modifier: Modifier,
    useFrontCamera: Boolean,
) {
    CameraPreview(
        modifier = modifier,
        lifecycleOwner = LocalLifecycleOwner.current,
        useFrontCamera = useFrontCamera,
    )
}
