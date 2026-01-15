package com.fictioncutshort.justacalculator.logic

import com.fictioncutshort.justacalculator.data.CalculatorState
import com.fictioncutshort.justacalculator.util.MAX_DIGITS
import kotlin.math.abs

/**
 * CalculatorEngine.kt
 *
 * Pure arithmetic functionality - no story logic here.
 *
 * Handles:
 * - Number input and formatting
 * - Basic operations (+, -, *, /)
 * - Percentage calculations
 * - Result formatting (scientific notation for large numbers)
 */

object CalculatorEngine {

    /**
     * Appends a digit to the current number being entered.
     */
    fun appendDigit(digit: String, state: CalculatorState): CalculatorState {
        return if (state.operation == null) {
            // Working on first number
            val newNumber = when {
                state.isReadyForNewOperation -> digit
                state.number1 == "0" -> digit
                state.number1.replace("-", "").replace(".", "").length >= MAX_DIGITS -> state.number1
                else -> state.number1 + digit
            }
            state.copy(
                number1 = newNumber,
                isReadyForNewOperation = false,
                lastExpression = if (state.isReadyForNewOperation) "" else state.lastExpression
            )
        } else {
            // Working on second number (after operator)
            val newNumber = when {
                state.number2.isEmpty() -> digit
                state.number2 == "0" -> digit
                state.number2.replace("-", "").replace(".", "").length >= MAX_DIGITS -> state.number2
                else -> state.number2 + digit
            }
            state.copy(number2 = newNumber)
        }
    }

    /**
     * Appends a decimal point to the current number.
     */
    fun appendDecimal(state: CalculatorState): CalculatorState {
        return if (state.operation == null) {
            if (!state.number1.contains(".")) {
                val newNumber = if (state.isReadyForNewOperation) "0." else state.number1 + "."
                state.copy(number1 = newNumber, isReadyForNewOperation = false)
            } else state
        } else {
            if (!state.number2.contains(".")) {
                val newNumber = if (state.number2.isEmpty()) "0." else state.number2 + "."
                state.copy(number2 = newNumber)
            } else state
        }
    }

    /**
     * Sets the current operation (+, -, *, /).
     * If an operation is pending, calculates intermediate result first.
     */
    fun setOperation(op: String, state: CalculatorState): CalculatorState {
        if (state.operation != null && state.number2.isNotEmpty()) {
            val result = calculateResult(state)
            return state.copy(
                number1 = result,
                number2 = "",
                operation = op,
                operationHistory = "$result $op",
                isReadyForNewOperation = false
            )
        }
        return state.copy(
            operation = op,
            operationHistory = "${state.number1} $op",
            isReadyForNewOperation = false
        )
    }

    /**
     * Deletes the last character from current input.
     */
    fun deleteLastChar(state: CalculatorState): CalculatorState {
        return if (state.operation == null) {
            val newNumber = if (state.number1.length <= 1) "0" else state.number1.dropLast(1)
            state.copy(number1 = newNumber)
        } else if (state.number2.isNotEmpty()) {
            val newNumber = if (state.number2.length <= 1) "" else state.number2.dropLast(1)
            state.copy(number2 = newNumber)
        } else {
            state.copy(operation = null, operationHistory = "")
        }
    }

    /**
     * Clears all input.
     */
    fun clearAll(state: CalculatorState): CalculatorState {
        return state.copy(
            number1 = "0",
            number2 = "",
            operation = null,
            expression = "",
            operationHistory = "",
            isReadyForNewOperation = true
        )
    }

    /**
     * Calculates percentage.
     * With operator: percentage of first number (100 + 10% = 110)
     * Without: divides by 100
     */
    fun calculatePercentage(state: CalculatorState): CalculatorState {
        return try {
            if (state.operation != null && state.number2.isNotEmpty()) {
                val num1 = state.number1.toDouble()
                val num2 = state.number2.toDouble()
                val percentValue = num1 * (num2 / 100)
                state.copy(number2 = formatResult(percentValue))
            } else {
                val num = state.number1.toDouble()
                state.copy(
                    number1 = formatResult(num / 100),
                    isReadyForNewOperation = true
                )
            }
        } catch (e: Exception) {
            state
        }
    }

    /**
     * Performs the pending calculation and returns result.
     */
    fun calculate(state: CalculatorState): CalculatorState {
        if (state.operation == null || state.number2.isEmpty()) {
            return state.copy(isReadyForNewOperation = true)
        }

        val expression = "${state.number1} ${state.operation} ${state.number2}"
        val result = calculateResult(state)

        return state.copy(
            lastExpression = expression,
            number1 = result,
            number2 = "",
            operation = null,
            operationHistory = "",
            isReadyForNewOperation = true,
            totalCalculations = state.totalCalculations + 1
        )
    }

    /**
     * Calculates the result of the current operation.
     */
    fun calculateResult(state: CalculatorState): String {
        return try {
            val num1 = state.number1.toDouble()
            val num2 = state.number2.toDouble()

            val result = when (state.operation) {
                "+" -> num1 + num2
                "-" -> num1 - num2
                "*" -> num1 * num2
                "/" -> if (num2 != 0.0) num1 / num2 else Double.NaN
                else -> num1
            }

            formatResult(result)
        } catch (e: Exception) {
            "Error"
        }
    }

    /**
     * Formats a number for display.
     * - Removes unnecessary decimal places
     * - Uses scientific notation for very large/small numbers
     * - Shows "Error" for invalid results
     */
    fun formatResult(value: Double): String {
        return when {
            value.isNaN() || value.isInfinite() -> "Error"
            abs(value) >= 1e12 -> String.format("%.4e", value)
            abs(value) < 0.0001 && value != 0.0 -> String.format("%.4e", value)
            value == value.toLong().toDouble() -> value.toLong().toString()
            else -> {
                // Remove trailing zeros after decimal
                val formatted = String.format("%.10f", value)
                formatted.trimEnd('0').trimEnd('.')
            }
        }
    }

    /**
     * Toggles the sign of the current number (positive/negative).
     */
    fun toggleSign(state: CalculatorState): CalculatorState {
        return if (state.operation == null) {
            val newNumber = if (state.number1.startsWith("-")) {
                state.number1.drop(1)
            } else {
                "-${state.number1}"
            }
            state.copy(number1 = newNumber)
        } else {
            val newNumber = if (state.number2.startsWith("-")) {
                state.number2.drop(1)
            } else {
                "-${state.number2}"
            }
            state.copy(number2 = newNumber)
        }
    }

    /**
     * Checks if the current input represents an absurdly large number.
     * Used for the "testing me" easter egg.
     */
    fun isAbsurdlyLarge(state: CalculatorState): Boolean {
        return try {
            val num = state.number1.toDouble()
            abs(num) >= 1_000_000_000_000.0
        } catch (e: Exception) {
            false
        }
    }
}