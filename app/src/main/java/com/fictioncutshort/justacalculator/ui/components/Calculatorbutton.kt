package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.vibrate
import kotlin.random.Random

/**
 * CalculatorButton.kt
 *
 * A single calculator button with retro styling.
 * Handles various visual states:
 * - Normal number/operator buttons
 * - Damaged/broken minus button (post-crisis)
 * - Flickering during whack-a-mole
 * - Dark/grayed out buttons
 * - "RAD" mode during final rant
 */

/**
 * A styled calculator button.
 *
 * @param symbol The text to display (0-9, +, -, etc.)
 * @param modifier Modifier for sizing/positioning
 * @param shakeIntensity How much the button shakes (0 = none, used during crisis)
 * @param invertedColors True for inverted color scheme (crisis mode)
 * @param isDamaged True if button appears damaged (brown color)
 * @param isBroken True if button is non-functional (shows crossed-out symbol)
 * @param isFlickering True if button is flashing yellow (whack-a-mole target)
 * @param isDark True if button is grayed out
 * @param showAsRad True if button should display "RAD" instead of normal symbol
 * @param onClick Called when button is pressed
 */
@Composable
fun CalculatorButton(
    symbol: String,
    modifier: Modifier = Modifier,
    shakeIntensity: Float = 0f,
    invertedColors: Boolean = false,
    isDamaged: Boolean = false,
    isBroken: Boolean = false,
    isFlickering: Boolean = false,
    isDark: Boolean = false,
    showAsRad: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // Determine button type for coloring
    val isNumberButton = symbol in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    val isOperationButton = symbol in listOf("+", "-", "*", "/", "=", "%", "( )")

    // Calculate background color based on state
    val backgroundColor = when {
        showAsRad -> Color(0xFF8B0000)  // Dark red for RAD mode
        isDark -> Color(0xFFB0A890)  // Muted beige when dark
        isFlickering -> Color(0xFFFFEB3B)  // Bright yellow when flickering
        isDamaged && symbol == "-" -> Color(0xFF8B4513)  // Brown for damaged minus

        // Inverted mode (crisis)
        invertedColors && isNumberButton -> Color(0xFF373737)
        invertedColors && symbol == "DEL" -> Color(0xFF1779E8)
        invertedColors && isOperationButton -> Color(0xFF1A1A1A)
        invertedColors && symbol == "C" -> Color(0xFF1A1A1A)
        invertedColors -> Color.Black

        // Normal retro mode
        isNumberButton -> Color(0xFFE8E4DA)  // Cream/beige
        symbol == "DEL" -> Color(0xFFD4783C)  // Orange-brown
        symbol == "C" -> Color(0xFFC9463D)  // Red
        isOperationButton -> Color(0xFF6B6B6B)  // Dark gray
        else -> Color(0xFFD4D0C4)  // Light gray-beige
    }

    // Calculate text color based on state
    val textColor = when {
        showAsRad -> Color.White
        isDark -> Color(0xFF6A6A6A)  // Faded text
        isFlickering -> Color.Black
        isBroken && symbol == "-" -> Color(0xFF4A4A4A)  // Very faded when broken
        isDamaged && symbol == "-" -> Color(0xFF6A6A6A)  // Slightly faded when damaged

        // Inverted mode
        invertedColors && isOperationButton -> Color(0xFF17B8E8)  // Cyan
        invertedColors && symbol == "C" -> Color(0xFF17B8E8)
        invertedColors -> Color.White

        // Normal mode
        symbol == "DEL" || symbol == "C" -> Color.White
        isOperationButton -> Color.White
        else -> Color(0xFF2D2D2D)  // Dark text
    }

    // Display text - show RAD or crossed-out minus if applicable
    val displaySymbol = when {
        showAsRad -> "RAD"
        isBroken && symbol == "-" -> "—̸"  // Crossed-out minus
        else -> symbol
    }

    // Random shake offset for crisis mode
    val shakeOffset = if (shakeIntensity > 0) {
        Random.nextFloat() * shakeIntensity * 2 - shakeIntensity
    } else 0f

    Button(
        onClick = {
            vibrate(context, 10, 30)
            onClick()
        },
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = Color.Black.copy(alpha = 0.3f)
            )
            .graphicsLayer {
                translationX = shakeOffset
                translationY = shakeOffset * 0.5f
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp
        )
    ) {
        Text(
            text = displaySymbol,
            fontSize = if (showAsRad) 12.sp else 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CalculatorDisplayFont
        )
    }
}