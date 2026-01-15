package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.RetroCream

/**
 * TermsScreen.kt
 *
 * Initial splash screen shown on first app launch.
 * User must accept privacy policy before using the app.
 *
 * The privacy policy humorously emphasizes that no data is collected
 * ("we are not interested in your data").
 */

/**
 * Terms and privacy policy acceptance screen.
 *
 * @param onAccept Called when user accepts and continues
 */
@Composable
fun TermsScreen(
    onAccept: () -> Unit
) {
    var showTermsPopup by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCream),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // App title
            Text(
                text = "Just A Calculator",
                fontSize = 40.sp,
                fontFamily = CalculatorDisplayFont,
                color = AccentOrange,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(80.dp))

            // Privacy Policy button (smaller, secondary)
            Button(
                onClick = { showTermsPopup = true },
                modifier = Modifier
                    .width(130.dp)
                    .height(45.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B6B6B),
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Privacy Policy",
                    fontSize = 10.sp,
                    fontFamily = CalculatorDisplayFont
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Accept & Continue button (larger, primary)
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .width(200.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Accept & Continue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalculatorDisplayFont
                )
            }
        }

        // Privacy Policy popup overlay
        if (showTermsPopup) {
            PrivacyPolicyPopup(onDismiss = { showTermsPopup = false })
        }
    }
}

/**
 * Privacy policy popup dialog.
 */
@Composable
private fun PrivacyPolicyPopup(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(RetroCream)
                .clickable(enabled = false) {}  // Prevent click-through
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Privacy Policy",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalculatorDisplayFont,
                    color = AccentOrange,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = """This is Just A Calculator. So you know what you're getting yourself into.

But just in case, we would like you to know, that we do not collect (and are not interested) in any of your data, be it from your math calculations or the depths of your device.

We don't want it, we do not look at it, we are certainly not collecting it and we could not be further from selling it.

This app does not collect, store, or transmit any personal data. 

That is our promise.

Because to really take advantage of the calculator... Do what it tells you!""",
                    fontSize = 14.sp,
                    color = Color(0xFF2D2D2D),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(120.dp)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B6B6B),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Close",
                        fontSize = 14.sp,
                        fontFamily = CalculatorDisplayFont
                    )
                }
            }
        }
    }
}