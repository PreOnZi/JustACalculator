package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fictioncutshort.justacalculator.ui.screens.ModelViewerGl

/**
 * Both platforms now render the key inspector with the shared GL viewer.
 *
 * This used to call SceneViewModel (Filament). That pulled ~10.5 MB of native
 * libraries and IBL environment maps into the APK for this one overlay, and
 * stood up an entire PBR engine every time the inspector opened — the engine was
 * deliberately scoped per-viewer to dodge a disposal crash, so the cost was paid
 * on every open rather than once.
 *
 * ModelViewerGl was written for iOS against the same .glb files and deliberately
 * copies SceneView's framing: camera 3.5 units back, longest side scaled to 2
 * units, opening tilt x=15 y=-25, drag to rotate with no auto-spin, base colours
 * read from the same pbrMetallicRoughness.baseColorFactor. The shading differs —
 * a key/fill/specular shader rather than image-based lighting — which is the one
 * visible change.
 */
@Composable
actual fun PlatformModelViewer(modelFile: String, modifier: Modifier) {
    ModelViewerGl(modelFile, modifier)
}
