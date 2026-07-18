package com.fictioncutshort.justacalculator.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.AccentOrange
import com.fictioncutshort.justacalculator.util.BezelBrown
import com.fictioncutshort.justacalculator.util.CalculatorDisplayFont
import com.fictioncutshort.justacalculator.util.DarkText
import com.fictioncutshort.justacalculator.util.LcdBackground
import com.fictioncutshort.justacalculator.util.RetroCream

/**
 * Reusable "Part 1 complete" card. Just the LCD-styled panel with the
 * sign-off text and the Play Store button — no full-screen background.
 *
 * Embedded inside [DormancyScreen] (so it floats above the static / RAD
 * grid during dormancy) and inside [EndOfPart1Screen] (full-screen mode
 * shown once dormancy ends).
 */
@Composable
fun Part1CompletePanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
            .background(LcdBackground, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "As much as I don't want to talk to you right now,\nI may change my mind in the future.",
            fontSize = 16.sp,
            fontFamily = CalculatorDisplayFont,
            color = DarkText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "You have completed the first part of the story.",
            fontSize = 14.sp,
            fontFamily = CalculatorDisplayFont,
            color = DarkText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Other stages are in development and will be available soon.",
            fontSize = 13.sp,
            fontFamily = CalculatorDisplayFont,
            color = DarkText.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { openPlayStore(context) },
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentOrange,
                contentColor = RetroCream
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
            modifier = Modifier.shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp)
            )
        ) {
            Text(
                text = "Rate on Google Play",
                fontSize = 15.sp,
                fontFamily = CalculatorDisplayFont
            )
        }
    }
}

/**
 * Full-screen wrapper around [Part1CompletePanel] for the post-dormancy
 * state (when there's nothing meaningful to layer the panel over).
 */
@Composable
fun EndOfPart1Screen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BezelBrown)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Part1CompletePanel()
    }
}

private fun openPlayStore(context: android.content.Context) {
    val pkg = context.packageName
    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$pkg")
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
        context.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        // Play Store app not installed — fall back to the web link.
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(webIntent)
    }
}
