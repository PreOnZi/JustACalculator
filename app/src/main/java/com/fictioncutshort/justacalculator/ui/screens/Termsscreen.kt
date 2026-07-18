package com.fictioncutshort.justacalculator.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
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
    var policyViewed by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }

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

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "While effort was put into adapting the calculator for horizontal screens, it has been developed with vertical view-first in mind.",
                fontSize = 12.sp,
                fontFamily = CalculatorDisplayFont,
                color = Color(0xFF2D2D2D),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(46.dp))

            // Privacy Policy button (smaller, secondary)
            Button(
                onClick = {
                    policyViewed = true
                    showTermsPopup = true
                },
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
                onClick = {
                    if (policyViewed) {
                        onAccept()
                    } else {
                        showWarningDialog = true
                    }
                },
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

        // Warning dialog when user tries to accept without reading policy
        if (showWarningDialog) {
            PolicyWarningDialog(
                onDismiss = { showWarningDialog = false },
                onConfirm = {
                    showWarningDialog = false
                    onAccept()
                },
                onViewPolicy = {
                    showWarningDialog = false
                    policyViewed = true
                    showTermsPopup = true
                }
            )
        }
    }
}

@Composable
private fun PolicyWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onViewPolicy: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(RetroCream)
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Just a moment",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CalculatorDisplayFont,
                    color = AccentOrange,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "We strongly encourage you to review the privacy policy before continuing.",
                    fontSize = 14.sp,
                    color = Color(0xFF2D2D2D),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onViewPolicy,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B6B6B),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "View Policy",
                            fontSize = 12.sp,
                            fontFamily = CalculatorDisplayFont
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Continue",
                            fontSize = 12.sp,
                            fontFamily = CalculatorDisplayFont
                        )
                    }
                }
            }
        }
    }
}

/**
 * Privacy policy popup dialog.
 */
@Composable
private fun PrivacyPolicyPopup(onDismiss: () -> Unit) {
    val context = LocalContext.current

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

Because to really take advantage of the calculator... Do what it tells you!

Generative AI: Generative AI, mainly Claude AI, has been used for development of the calculator. However, its use was strictly code-based. Claude was not used to consult art direction, the story or to build the majority of visual artifacts. 3D models have been built from scratch in Blender, the story has been developed based only on human ideas & discussions, and the same goes for art direction.

For more comprehensive privacy policy (which is highly recommended you visit) please follow the link below:""",
                    fontSize = 14.sp,
                    color = Color(0xFF2D2D2D),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Link to full privacy policy
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://preonzi.github.io/JustAPrivacyPolicy/"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Full Privacy Policy",
                        fontSize = 13.sp,
                        fontFamily = CalculatorDisplayFont
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

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
