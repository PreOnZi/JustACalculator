package com.fictioncutshort.justacalculator.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.RetroCream

/**
 * DonationPage.kt
 *
 * Full-screen overlay that appears when user taps on an ad banner.
 * Instead of actual ads, it humorously asks for donations.
 *
 * Links to Ko-fi page for tips.
 */

/**
 * Donation/tip landing page overlay.
 *
 * @param onDismiss Called when user wants to close the page
 * @param onDonate Called when user taps the donate button (should open Ko-fi link)
 * @param modifier Modifier for the container
 */
@Composable
fun DonationLandingPage(
    onDismiss: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RetroCream)
            .clickable(enabled = false) {},  // Prevent click-through to calculator
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Main donate button with humorous text
            Button(
                onClick = onDonate,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(70.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentOrange,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = "Send money to a stranger\nover the internet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Thank you message
            Text(
                text = "thank you",
                fontSize = 18.sp,
                color = Color(0xFF6B6B6B),
                fontStyle = FontStyle.Italic
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Subtle close button
            TextButton(onClick = onDismiss) {
                Text(
                    text = "close",
                    fontSize = 14.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}