package com.fictioncutshort.justacalculator

import androidx.compose.foundation.layout.Box
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
        // Deliberately NOT inset here. Padding the root letterboxes the app and
        // leaves bare window either side of it, which reads as white bars above
        // and below the game. The screens own their own insets instead — see
        // the statusBarsPadding/navigationBarsPadding pair in the calculator
        // layouts — so the background still runs edge to edge.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .easterEggGrayscale(EasterEggTheme.grayscale)
        ) {
            CalculatorScreen()
        }
    }
}
