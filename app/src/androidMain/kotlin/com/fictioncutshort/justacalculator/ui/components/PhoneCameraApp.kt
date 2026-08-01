package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phone-detour Camera app. Reuses [CameraPreview] (already used during step 19)
 * and overlays a circular close button. Distinct from the story-mode camera
 * sequence — this screen is open-ended browsing, no timer, no double-press.
 */
@Composable
fun PhoneCameraApp(onClose: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            lifecycleOwner = lifecycleOwner,
            useFrontCamera = false
        )
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✕", color = Color.White, fontSize = 18.sp)
        }
    }
}
