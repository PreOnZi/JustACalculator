package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.logic.CalculatorEngine
import com.fictioncutshort.justacalculator.platform.formatFixed
import com.fictioncutshort.justacalculator.platform.formatScientific
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs on both the JVM and the iOS simulator. The point is less the arithmetic
 * than the platform seams underneath it: number formatting is backed by
 * `String.format` on Android and `NSString.stringWithFormat` on iOS, and the
 * calculator display depends on the two agreeing exactly.
 */
class CalculatorEngineTest {

    private fun press(symbols: List<String>): CalculatorState {
        var s = CalculatorState()
        for (sym in symbols) {
            s = when (sym) {
                in setOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9") ->
                    CalculatorEngine.appendDigit(sym, s)
                "." -> CalculatorEngine.appendDecimal(s)
                "=" -> CalculatorEngine.calculate(s)
                else -> CalculatorEngine.setOperation(sym, s)
            }
        }
        return s
    }

    @Test
    fun multipliesTwoNumbers() {
        assertEquals("56", press(listOf("7", "*", "8", "=")).number1)
    }

    @Test
    fun addsTwoNumbers() {
        assertEquals("19", press(listOf("1", "2", "+", "7", "=")).number1)
    }

    @Test
    fun dividesToDecimal() {
        assertEquals("3.5", press(listOf("7", "/", "2", "=")).number1)
    }

    @Test
    fun formatsWholeNumbersWithoutDecimalPoint() {
        assertEquals("42", CalculatorEngine.formatResult(42.0))
    }

    @Test
    fun trimsTrailingZeros() {
        assertEquals("0.5", CalculatorEngine.formatResult(0.5))
    }

    @Test
    fun reportsErrorForNonFiniteResults() {
        assertEquals("Error", CalculatorEngine.formatResult(Double.NaN))
        assertEquals("Error", CalculatorEngine.formatResult(Double.POSITIVE_INFINITY))
    }

    @Test
    fun clearResetsToZero() {
        val cleared = CalculatorEngine.clearAll(press(listOf("1", "2", "3")))
        assertEquals("0", cleared.number1)
    }

    /** The seam most likely to diverge between platforms. */
    @Test
    fun fixedPointFormattingMatchesAcrossPlatforms() {
        assertEquals("0.5000000000", formatFixed(0.5, 10))
        assertEquals("3.14", formatFixed(3.14159, 2))
        assertEquals("-2.50", formatFixed(-2.5, 2))
    }

    @Test
    fun scientificFormattingUsesExponentNotation() {
        val huge = formatScientific(1.5e13, 4)
        assertTrue(huge.contains("e"), "expected exponent notation, got '$huge'")
        assertTrue(huge.startsWith("1.5000"), "expected 1.5000…, got '$huge'")
    }

    @Test
    fun veryLargeResultsUseScientificNotation() {
        assertTrue(CalculatorEngine.formatResult(1e13).contains("e"))
    }
}
