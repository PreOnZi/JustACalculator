package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader

/**
 * Android-only SceneView (Filament) model viewer, extracted from MazeGame so
 * that file could move to commonMain.
 *
 * The engine is created per viewer here rather than shared across the game
 * session as it was before. That was done to avoid a native crash when
 * rememberEngine() disposed the engine while a frame was still rendering —
 * scoping the engine to the composable that uses it achieves the same thing,
 * since it is now created and disposed with a single Scene.
 */
@Composable
fun SceneViewModel(modelFile: String, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) { position = Position(z = 3.5f) }
    val mainLightNode = rememberMainLightNode(engine)

    val modelNode = remember(modelFile) {
        ModelNode(
            modelInstance = modelLoader.createModelInstance(modelFile),
            scaleToUnits = 2.0f,
        ).apply {
            isEditable = true
            rotation = Rotation(x = 15f, y = -25f)
        }
    }

    Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        mainLightNode = mainLightNode,
        childNodes = remember(modelFile) { listOf(modelNode) },
    )
}
