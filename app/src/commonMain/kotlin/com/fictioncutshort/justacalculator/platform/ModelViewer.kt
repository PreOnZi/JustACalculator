package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The maze's interactive key inspector.
 *
 * Android renders it with SceneView (Filament); iOS renders it with the shared
 * GL seam via [com.fictioncutshort.justacalculator.ui.screens.ModelViewerGl].
 * The two are kept deliberately alike — same camera distance, same framing,
 * same opening tilt — so a key reads the same on both.
 *
 * This is a seam rather than one shared implementation only because Android's
 * SceneView path already worked and was already tuned; there is nothing about
 * the GL version that could not serve both if SceneView ever becomes a burden.
 */
@Composable
expect fun PlatformModelViewer(modelFile: String, modifier: Modifier = Modifier)
