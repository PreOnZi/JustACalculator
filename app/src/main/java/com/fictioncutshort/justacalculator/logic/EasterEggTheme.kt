package com.fictioncutshort.justacalculator.logic

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawWithContent

/**
 * EasterEggTheme.kt
 *
 * Shared, persisted visual-tweak store driven by the hidden calculator codes:
 *   58008  → number-button colour
 *   707    → background colour
 *   1134206 → global grayscale (toggle)
 *
 * Both the 2D Compose calculator (CalculatorButton, the screen background) and
 * the OpenGL Calculator City read from this singleton, so a change made on the
 * calculator is reflected the next time the city is drawn (and vice-versa).
 *
 * Values live in Compose snapshot state so the 2D UI recomposes the instant
 * they change; the GL renderer just reads the current value each frame. They
 * are persisted to a dedicated SharedPreferences file so the look survives an
 * app restart. `0` / `false` always means "original, untouched".
 */
object EasterEggTheme {

    /** A selectable colour: ARGB for Compose, plus 0..1 RGB for OpenGL. */
    data class Preset(
        val name: String,
        val argb: Long,
        val r: Float,
        val g: Float,
        val b: Float,
        /** True when this swatch is dark enough to need light text on top. */
        val lightText: Boolean = false
    ) {
        val color: Color get() = Color(argb)
    }

    // Index 0 is always "Original" — selecting it reverts that surface.
    val NUMBER_PRESETS: List<Preset> = listOf(
        Preset("Original", 0xFFE8E4DA, 0.91f, 0.89f, 0.85f, lightText = false),
        Preset("Red",      0xFFD24B3E, 0.82f, 0.29f, 0.24f, lightText = true),
        Preset("Orange",   0xFFE08A3C, 0.88f, 0.54f, 0.24f, lightText = false),
        Preset("Green",    0xFF4CAF6E, 0.30f, 0.69f, 0.43f, lightText = true),
        Preset("Blue",     0xFF4A78D2, 0.29f, 0.47f, 0.82f, lightText = true),
        Preset("Purple",   0xFF8A5CD0, 0.54f, 0.36f, 0.82f, lightText = true),
        Preset("Pink",     0xFFE08AB4, 0.88f, 0.54f, 0.71f, lightText = false),
        Preset("Black",    0xFF2B2B2B, 0.17f, 0.17f, 0.17f, lightText = true),
    )

    val BACKGROUND_PRESETS: List<Preset> = listOf(
        Preset("Original", 0xFFF5F0E1, 0.96f, 0.94f, 0.88f),
        Preset("White",    0xFFFFFFFF, 0.98f, 0.98f, 0.98f),
        Preset("Black",    0xFF111111, 0.07f, 0.07f, 0.07f),
        Preset("Navy",     0xFF1A2A50, 0.10f, 0.16f, 0.31f),
        Preset("Teal",     0xFF1E6F6B, 0.12f, 0.44f, 0.42f),
        Preset("Pink",     0xFFE8B8CC, 0.91f, 0.72f, 0.80f),
        Preset("Mint",     0xFFB8E8C8, 0.72f, 0.91f, 0.78f),
        Preset("Lavender", 0xFFC8B8E8, 0.78f, 0.72f, 0.91f),
    )

    private const val PREFS = "easter_eggs"
    private const val KEY_NUMBER = "ee_number_color"
    private const val KEY_BG = "ee_bg_color"
    private const val KEY_GRAY = "ee_grayscale"

    private var prefs: SharedPreferences? = null

    /** Index into [NUMBER_PRESETS]. 0 = original cream. */
    var numberColorIndex by mutableIntStateOf(0)
        private set

    /** Index into [BACKGROUND_PRESETS]. 0 = original. */
    var backgroundIndex by mutableIntStateOf(0)
        private set

    /** Whole-app grayscale toggle. */
    var grayscale by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        numberColorIndex = p.getInt(KEY_NUMBER, 0).coerceIn(0, NUMBER_PRESETS.lastIndex)
        backgroundIndex = p.getInt(KEY_BG, 0).coerceIn(0, BACKGROUND_PRESETS.lastIndex)
        grayscale = p.getBoolean(KEY_GRAY, false)
    }

    fun selectNumberColor(index: Int) {
        val i = index.coerceIn(0, NUMBER_PRESETS.lastIndex)
        numberColorIndex = i
        prefs?.edit()?.putInt(KEY_NUMBER, i)?.apply()
    }

    fun selectBackground(index: Int) {
        val i = index.coerceIn(0, BACKGROUND_PRESETS.lastIndex)
        backgroundIndex = i
        prefs?.edit()?.putInt(KEY_BG, i)?.apply()
    }

    /** Flips grayscale and returns the new value. */
    fun toggleGrayscale(): Boolean {
        grayscale = !grayscale
        prefs?.edit()?.putBoolean(KEY_GRAY, grayscale)?.apply()
        return grayscale
    }

    // ── Compose helpers ────────────────────────────────────────────────────

    /** Chosen number-button preset, or null when left on original. */
    fun numberPresetOrNull(): Preset? =
        if (numberColorIndex == 0) null else NUMBER_PRESETS.getOrNull(numberColorIndex)

    /** Chosen background colour for the 2D screen, or null when on original. */
    fun backgroundColorOrNull(): Color? =
        if (backgroundIndex == 0) null else BACKGROUND_PRESETS.getOrNull(backgroundIndex)?.color

    // ── OpenGL helpers (read by Cityglrenderer each frame) ──────────────────

    /** Digit-building body colour, or null to keep the model's cream. */
    fun cityNumberRgbOrNull(): FloatArray? =
        if (numberColorIndex == 0) null
        else NUMBER_PRESETS.getOrNull(numberColorIndex)?.let { maybeGray(floatArrayOf(it.r, it.g, it.b)) }

    /** City clear (sky/ground) colour — always defined; defaults to cream. */
    fun cityClearRgb(): FloatArray {
        val p = BACKGROUND_PRESETS.getOrNull(backgroundIndex) ?: BACKGROUND_PRESETS[0]
        return maybeGray(floatArrayOf(p.r, p.g, p.b))
    }

    /** Applies luminance grayscale when the toggle is on. */
    private fun maybeGray(rgb: FloatArray): FloatArray {
        if (!grayscale) return rgb
        val l = 0.299f * rgb[0] + 0.587f * rgb[1] + 0.114f * rgb[2]
        return floatArrayOf(l, l, l)
    }
}

/**
 * Desaturates everything drawn inside this modifier when [enabled]. Used to
 * grayscale the 2D calculator chrome; the GL city desaturates itself in-shader.
 */
fun Modifier.easterEggGrayscale(enabled: Boolean): Modifier =
    if (!enabled) this
    else this.drawWithContent {
        val paint = Paint().apply {
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
