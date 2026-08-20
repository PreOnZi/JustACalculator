package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The maze's interactive key inspector.
 *
 * Both platforms now render it with the shared GL seam via
 * [com.fictioncutshort.justacalculator.ui.screens.ModelViewerGl], so a key reads
 * identically on Android and iOS by construction rather than by tuning.
 *
 * Android used to render it with SceneView (Filament). That became a burden
 * exactly as this comment once predicted it might: ~10.5 MB of native libraries
 * and environment maps for one overlay, plus a full PBR engine stood up and torn
 * down on every open. The seam is kept because the two platforms still differ in
 * how a GL surface is hosted, not because the viewers differ.
 */
@Composable
expect fun PlatformModelViewer(modelFile: String, modifier: Modifier = Modifier)
