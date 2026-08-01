package com.fictioncutshort.justacalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.fictioncutshort.justacalculator.logic.CalculatorActions
import com.fictioncutshort.justacalculator.logic.EasterEggTheme
import com.fictioncutshort.justacalculator.logic.easterEggGrayscale
import com.fictioncutshort.justacalculator.platform.AppInit
import com.fictioncutshort.justacalculator.platform.logDebug
import com.fictioncutshort.justacalculator.ui.CalculatorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d("JustACalc", "🟢 MainActivity.onCreate START — savedInstanceState=${savedInstanceState != null}")
        super.onCreate(savedInstanceState)
        // Must precede anything that touches Assets or the other platform
        // singletons, which resolve their context through AppInit.
        com.fictioncutshort.justacalculator.platform.AppInit.initialize(applicationContext)
        CalculatorActions.init(applicationContext)
        // TEMPORARY: seed a "just finished Building 5" test state (see DebugSeed).
        com.fictioncutshort.justacalculator.logic.DebugSeed.seed(applicationContext)
        android.util.Log.d("JustACalc", "🟢 MainActivity.onCreate AFTER init")
        setContent {
            MaterialTheme {
                // Easter-egg grayscale (code 1134206) desaturates the whole 2D
                // UI. The GL Calculator City desaturates itself in-shader.
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .easterEggGrayscale(com.fictioncutshort.justacalculator.logic.EasterEggTheme.grayscale)
                ) {
                    CalculatorScreen()
                    // NOTE: the low-volume warning is NOT global — it lives inside
                    // CalculatorCityView so it only nags once the narrated city begins,
                    // never during the phase-1 calculator story.
                }
            }
        }
        android.util.Log.d("JustACalc", "🟢 MainActivity.onCreate END")
    }

    override fun onDestroy() {
        android.util.Log.d("JustACalc", "🔴 MainActivity.onDestroy")
        super.onDestroy()
        // Clear singleton state so a fresh activity always reloads from SharedPreferences
        CalculatorActions.liveState = null
    }
}



