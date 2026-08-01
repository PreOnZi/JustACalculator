package com.fictioncutshort.justacalculator

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.ComposeUIViewController
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.platform.AppInit
import com.fictioncutshort.justacalculator.platform.IosAppContext
import com.fictioncutshort.justacalculator.ui.PortHarnessCalculator
import platform.UIKit.UIViewController

/**
 * The iOS entry point, mirroring what MainActivity.onCreate does on Android:
 * initialise the platform singletons, then hand Compose a root composable.
 *
 * Swift calls this from `ComposeView` in iosApp.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    // Same ordering contract as MainActivity: platform services before any UI.
    if (!AppInit.isInitialized) {
        AppInit.initialize(IosAppContext)
        CalculatorActions.init(IosAppContext)
    }

    MaterialTheme {
        // TODO: swap for the real CalculatorScreen once MainActivity is ported.
        PortHarnessCalculator()
    }
}
