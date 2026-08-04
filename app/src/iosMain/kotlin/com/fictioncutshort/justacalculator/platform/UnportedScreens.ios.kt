package com.fictioncutshort.justacalculator.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
actual fun PlatformModelViewer(modelFile: String, modifier: Modifier) =
    NotPortedYet("3D model viewer", "SceneView/Filament has no iOS counterpart.", {})

/** Silent until the AVAudioEngine mic echo is ported; the story still advances. */
@Composable
private fun NotPortedYet(title: String, detail: String, onSkip: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF14110E)).clickable { onSkip() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(title, color = Color(0xFFE88617), fontSize = 18.sp, textAlign = TextAlign.Center)
            Text(detail, color = Color(0xFF8A7F70), fontSize = 13.sp, textAlign = TextAlign.Center)
            Text("tap to continue", color = Color(0xFF5A5248), fontSize = 12.sp)
        }
    }
}
