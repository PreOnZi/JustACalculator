package com.fictioncutshort.justacalculator.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.BezelBrown
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.LcdBackground
import com.fictioncutshort.justacalculator.util.RetroCream
import com.fictioncutshort.justacalculator.util.rememberResponsiveDimensions
import com.fictioncutshort.justacalculator.util.calculateDisplayFontSize

@Composable
fun PausedCalculatorOverlay(
    display: String,
    expression: String,
    justCalculated: Boolean,
    darkButtons: List<String>,
    minusButtonDamaged: Boolean,
    isTablet: Boolean,
    maxContentWidth: Dp,
    buttonLayout: List<List<String>>,
    onMuteClick: () -> Unit,
    onButtonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = rememberResponsiveDimensions()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RetroCream),
        contentAlignment = if (isTablet) Alignment.TopCenter else Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .then(if (isTablet && !isLandscape) Modifier.widthIn(max = maxContentWidth) else Modifier.fillMaxWidth())
                .fillMaxHeight()
        ) {
            // Top bezel - exactly status bar height only
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.statusBarHeight)
                    .background(BezelBrown)
            )

            if (isLandscape) {
                // Landscape layout: side-by-side
                LandscapePausedLayout(
                    display = display,
                    expression = expression,
                    justCalculated = justCalculated,
                    darkButtons = darkButtons,
                    minusButtonDamaged = minusButtonDamaged,
                    buttonLayout = buttonLayout,
                    dimensions = dimensions,
                    onMuteClick = onMuteClick,
                    onButtonClick = onButtonClick
                )
            } else {
                // Portrait layout: stacked
                PortraitPausedLayout(
                    display = display,
                    expression = expression,
                    justCalculated = justCalculated,
                    darkButtons = darkButtons,
                    minusButtonDamaged = minusButtonDamaged,
                    buttonLayout = buttonLayout,
                    dimensions = dimensions,
                    onMuteClick = onMuteClick,
                    onButtonClick = onButtonClick
                )
            }
        }
    }
}

@Composable
private fun PortraitPausedLayout(
    display: String,
    expression: String,
    justCalculated: Boolean,
    darkButtons: List<String>,
    minusButtonDamaged: Boolean,
    buttonLayout: List<List<String>>,
    dimensions: com.fictioncutshort.justacalculator.util.ResponsiveDimensions,
    onMuteClick: () -> Unit,
    onButtonClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimensions.contentPadding)
            .padding(top = 8.dp)
    ) {
        // Mute button (to resume)
        MuteButtonWithSpinner(
            isMuted = true,
            isAutoProgressing = false,
            onClick = onMuteClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp)
        )

        // "Story paused" indicator
        Text(
            text = "Story paused",
            fontSize = 14.sp,
            color = Color.Gray,
            fontFamily = CalculatorDisplayFont,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Calculator display
            CalculatorLcdDisplay(
                display = display,
                expression = expression,
                justCalculated = justCalculated,
                dimensions = dimensions,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 16.dp)
            )

            // Calculator buttons - fully functional
            CalculatorButtonGrid(
                buttonLayout = buttonLayout,
                darkButtons = darkButtons,
                minusButtonDamaged = minusButtonDamaged,
                dimensions = dimensions,
                onButtonClick = onButtonClick
            )
        }
    }
}

@Composable
private fun LandscapePausedLayout(
    display: String,
    expression: String,
    justCalculated: Boolean,
    darkButtons: List<String>,
    minusButtonDamaged: Boolean,
    buttonLayout: List<List<String>>,
    dimensions: com.fictioncutshort.justacalculator.util.ResponsiveDimensions,
    onMuteClick: () -> Unit,
    onButtonClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimensions.contentPadding)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // Left panel: Status + Display
        Box(
            modifier = Modifier
                .weight(dimensions.leftPanelWeight)
                .fillMaxHeight()
                .padding(end = dimensions.contentPadding / 2)
        ) {
            // Mute button (to resume)
            MuteButtonWithSpinner(
                isMuted = true,
                isAutoProgressing = false,
                onClick = onMuteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp)
            )

            // "Story paused" indicator
            Text(
                text = "Story paused",
                fontSize = 14.sp,
                color = Color.Gray,
                fontFamily = CalculatorDisplayFont,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp)
            )

            // LCD Display at bottom of left panel
            CalculatorLcdDisplay(
                display = display,
                expression = expression,
                justCalculated = justCalculated,
                dimensions = dimensions,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // Right panel: Calculator buttons
        Box(
            modifier = Modifier
                .weight(dimensions.rightPanelWeight)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            CalculatorButtonGrid(
                buttonLayout = buttonLayout,
                darkButtons = darkButtons,
                minusButtonDamaged = minusButtonDamaged,
                dimensions = dimensions,
                onButtonClick = onButtonClick,
                modifier = Modifier.width(dimensions.keyboardWidth)
            )
        }
    }
}

@Composable
private fun CalculatorLcdDisplay(
    display: String,
    expression: String,
    justCalculated: Boolean,
    dimensions: com.fictioncutshort.justacalculator.util.ResponsiveDimensions,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensions.lcdDisplayHeight)
                .background(LcdBackground, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Show expression history after calculation
            if (justCalculated && expression.isNotEmpty() && expression != display) {
                Text(
                    text = "$expression=",
                    fontSize = 16.sp,
                    color = Color(0xFF2D2D2D).copy(alpha = 0.5f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    fontFamily = CalculatorDisplayFont,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp)
                )
            }

            // Shadow digits (ghost effect)
            Text(
                text = "8888888888888",
                fontSize = dimensions.displayFontSizeBase.sp,
                color = Color(0xFF000000).copy(alpha = 0.06f),
                textAlign = TextAlign.End,
                maxLines = 1,
                fontFamily = CalculatorDisplayFont,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomEnd)
            )

            // Actual display
            val displayFontSize = calculateDisplayFontSize(display.length, dimensions.displayFontSizeBase)
            Text(
                text = display,
                fontSize = displayFontSize.sp,
                color = Color(0xFF2D2D2D),
                textAlign = TextAlign.End,
                maxLines = 1,
                fontFamily = CalculatorDisplayFont,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun CalculatorButtonGrid(
    buttonLayout: List<List<String>>,
    darkButtons: List<String>,
    minusButtonDamaged: Boolean,
    dimensions: com.fictioncutshort.justacalculator.util.ResponsiveDimensions,
    onButtonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(bottom = if (dimensions.isLandscape) 0.dp else dimensions.contentPadding),
        verticalArrangement = Arrangement.spacedBy(dimensions.buttonSpacing)
    ) {
        buttonLayout.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensions.buttonRowHeight),
                horizontalArrangement = Arrangement.spacedBy(dimensions.buttonSpacing)
            ) {
                row.forEach { symbol ->
                    CalculatorButton(
                        symbol = symbol,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shakeIntensity = 0f,
                        invertedColors = false,
                        isDamaged = minusButtonDamaged && symbol == "-",
                        isBroken = false,
                        isFlickering = false,
                        isDark = symbol in darkButtons,
                        showAsRad = false,
                        onClick = { onButtonClick(symbol) }
                    )
                }
            }
        }
    }
}