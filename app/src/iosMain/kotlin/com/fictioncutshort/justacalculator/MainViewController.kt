package com.fictioncutshort.justacalculator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.logic.EasterEggTheme
import com.fictioncutshort.justacalculator.logic.easterEggGrayscale
import com.fictioncutshort.justacalculator.platform.AppInit
import com.fictioncutshort.justacalculator.platform.IosAppContext
import com.fictioncutshort.justacalculator.platform.installImageLoader
import com.fictioncutshort.justacalculator.ui.CalculatorScreen
import platform.UIKit.UIViewController

/**
 * The iOS entry point, mirroring MainActivity.onCreate: initialise the platform
 * singletons in the same order, then hand Compose the same root composable the
 * Android app uses.
 *
 * Swift calls this from `ComposeView` in iosApp.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    if (!AppInit.isInitialized) {
        AppInit.initialize(IosAppContext)
        installImageLoader()
        CalculatorActions.init(IosAppContext)
        EasterEggTheme.init(IosAppContext)
    }

    MaterialTheme {
        // Inset for the Dynamic Island / notch and the home indicator.
        //
        // This is not iOS-specific styling — it reproduces the Android window
        // the whole UI was written against. MainActivity never calls
        // enableEdgeToEdge(), so on Android the system insets the window and no
        // screen has to think about it. Compose on iOS is always edge-to-edge,
        // so without this the top of every screen sits under the island.
        //
        // safeDrawing rather than statusBars: it covers the cutout and the home
        // indicator too, which is what Android's systemBars + displayCutout
        // amounts to.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .easterEggGrayscale(EasterEggTheme.grayscale)
        ) {
            CalculatorScreen()
        }
    }
}
