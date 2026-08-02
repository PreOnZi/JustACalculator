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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.data.Chapter

@Composable
actual fun PlatformAdCardStack(
    onPexesoComplete: () -> Unit,
    startAtCity: Boolean,
    startAtPexeso: Boolean,
    onStageChanged: (String) -> Unit,
    onCityEntered: () -> Unit,
    onJumpToPhase1: (Chapter) -> Unit,
) {
    NotPortedYet(
        title = "Ad cards, pexeso and the city",
        detail = "Blocked on the OpenGL city port.",
        // Tapping through keeps the rest of the story reachable on iOS rather
        // than dead-ending here.
        onSkip = onPexesoComplete,
    )
}

@Composable
actual fun PlatformHomeScreenOverlay(
    audioHandler: TypingClicker?,
    onIconClick: (String) -> Unit,
    onReturnToCalculator: () -> Unit,
) {
    NotPortedYet(
        title = "Phone home screen",
        detail = "Needs the contacts and mic-echo ports.",
        onSkip = onReturnToCalculator,
    )
}

/** No-op until the AVAudioEngine mic echo is ported; the story still advances. */
actual fun createTalkAudioHandler(context: AppContext): TypingClicker =
    object : TypingClicker {
        override fun playTypingClick() = Unit
    }

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

@Composable
actual fun PlatformDebugPasswordGate(onUnlock: () -> Unit, onCancel: () -> Unit) {
    NotPortedYet(
        title = "Debug menu",
        detail = "Lives in the city debug menu, which is not ported.",
        onSkip = onCancel,
    )
}

@Composable
actual fun rememberModelIcon(
    objPath: String,
    mtlPath: String?,
    sizePx: Int,
    tilt: Float,
    turn: Float,
    colorGamma: Float,
    fitSpan: Float,
): ImageBitmap? = null

@Composable
actual fun PlatformModelViewer(modelFile: String, modifier: Modifier) {
    NotPortedYet(
        title = "3D model viewer",
        detail = "SceneView/Filament has no iOS counterpart yet.",
        onSkip = {},
    )
}

@Composable
actual fun PlatformDoor4Room(modifier: Modifier, onComplete: () -> Unit) =
    NotPortedYet("Building 4", "GL door room with a camera-fed texture.", onComplete)

@Composable
actual fun PlatformBuilding5Map(onComplete: () -> Unit, onExit: () -> Unit) =
    NotPortedYet("Building 5", "Needs MapKit in place of osmdroid.", onComplete)

@Composable
actual fun PlatformBuilding6Runner(onComplete: () -> Unit, onExit: () -> Unit) =
    NotPortedYet("Building 6", "GLES 3.0 runner — next in the GL queue.", onComplete)

@Composable
actual fun PlatformBuilding7VanityRoom(modifier: Modifier, onComplete: () -> Unit) =
    NotPortedYet("Building 7", "Needs AVFoundation + Vision face landmarks.", onComplete)
