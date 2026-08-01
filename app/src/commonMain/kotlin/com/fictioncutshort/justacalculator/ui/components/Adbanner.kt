package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.util.BannerGray

/**
 * AdBanner.kt
 *
 * The fake advertisement banner that appears in the calculator UI.
 *
 * Part of the story - the calculator becomes aware of these ads during
 * the "crisis" sequence and is horrified to discover they exist.
 *
 * Ad states:
 * - Gray: Empty placeholder (shown before crisis)
 * - Green: "YOU WON!" scam ad
 * - Pink: "$500/DAY FROM HOME" scam ad
 * - Purple: Post-chaos ad
 * - Cyan: Post-chaos ad
 *
 * Tapping an ad opens the donation page (a humorous twist).
 */

/**
 * Fake advertisement banner.
 *
 * @param adPhase Main ad animation phase (0=none, 1=green, 2=pink)
 * @param postChaosPhase Post-chaos ad phase (0=none, 1=purple, 2=cyan)
 * @param bannersDisabled True if ads have been disabled via console
 * @param onAdClick Called when ad is tapped (opens donation page)
 * @param modifier Modifier for positioning
 */
@Composable
fun AdBanner(
    adPhase: Int,
    postChaosPhase: Int,
    bannersDisabled: Boolean,
    onAdClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine background color based on ad state
    val backgroundColor = when {
        postChaosPhase == 1 -> Color(0xFF9C27B0)  // Purple
        postChaosPhase == 2 -> Color(0xFF00BCD4)  // Cyan
        adPhase == 1 -> Color(0xFF4CAF50)  // Green
        adPhase == 2 -> Color(0xFFE91E63)  // Pink
        else -> BannerGray  // Default gray
    }

    // Determine if ad is clickable (has active ad content)
    val isClickable = adPhase > 0 || postChaosPhase > 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(backgroundColor)
            .then(
                if (isClickable) {
                    Modifier.clickable { onAdClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ad text content
        when {
            postChaosPhase == 1 -> {
                Text(
                    text = "✨ UNLOCK YOUR POTENTIAL TODAY! ✨",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            postChaosPhase == 2 -> {
                Text(
                    text = "🚀 LIMITED TIME OFFER - ACT NOW! 🚀",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            adPhase == 1 -> {
                Text(
                    text = "🎉 YOU WON! Click here! 🎉",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            adPhase == 2 -> {
                Text(
                    text = "💰 EARN ${'$'}500/DAY FROM HOME! 💰",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            // Empty gray banner - no text
        }
    }
}

/**
 * Determines if the ad banner should be visible based on current step.
 *
 * @param conversationStep Current story step
 * @param bannersDisabled True if ads disabled via console
 * @param adPhase Current ad animation phase
 * @param postChaosPhase Post-chaos ad phase
 * @return True if banner should be shown
 */
fun shouldShowAdBanner(
    conversationStep: Int,
    bannersDisabled: Boolean,
    adPhase: Int,
    postChaosPhase: Int
): Boolean {
    // Always show during ad animations
    if (adPhase > 0 || postChaosPhase > 0) return true

    // Don't show if disabled
    if (bannersDisabled) return false

    // Show during these step ranges:
    // - Steps 10-18 (early conversation)
    // - Steps 26+ (after wake-up question)
    return (conversationStep in 10..18) || (conversationStep >= 26)
}