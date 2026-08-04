package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Seams for the parts of the game that are still Android-only.
 *
 * The iOS actuals render a labelled "not ported yet" panel rather than failing
 * silently — an unfinished beat should look unfinished, not look like a bug.
 *
 * **Delete each one as its real implementation ports.** A stale seam is worse
 * than no seam: it keeps showing a placeholder for something that already works,
 * which is exactly what happened to the debug-menu gate.
 */

/** Interactive 3D model viewer — SceneView/Filament, no iOS counterpart. */
@Composable
expect fun PlatformModelViewer(modelFile: String, modifier: Modifier = Modifier)

/** Building 4's door room — GL plus a camera-fed external texture. */
@Composable
expect fun PlatformDoor4Room(modifier: Modifier = Modifier, onComplete: () -> Unit = {})

