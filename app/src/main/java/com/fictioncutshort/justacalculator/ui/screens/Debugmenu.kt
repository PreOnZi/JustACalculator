package com.fictioncutshort.justacalculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fictioncutshort.justacalculator.data.CHAPTERS
import com.fictioncutshort.justacalculator.data.Chapter
import com.fictioncutshort.justacalculator.util.AccentOrange

/**
 * DebugMenu.kt
 *
 * Hidden developer menu for testing and replaying story sections.
 *
 * Access: Tap the mute button 5 times rapidly (within 2 seconds)
 * Reset: Tap the mute button 10 times rapidly
 *
 * Features:
 * - Jump to any chapter in the story
 * - See current conversation step
 * - Reset game to fresh state
 */

/**
 * Debug menu overlay for chapter selection and game reset.
 *
 * @param currentStep Current conversation step (displayed for reference)
 * @param onJumpToChapter Called when a chapter button is pressed
 * @param onResetGame Called when reset button is pressed
 * @param onClose Called when close button is pressed
 */
@Composable
fun DebugMenu(
    currentStep: Int,
    onJumpToChapter: (Chapter) -> Unit,
    onResetGame: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "DEBUG MENU",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AccentOrange,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Current step indicator
            Text(
                text = "Current Step: $currentStep",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Scrollable chapter list
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                CHAPTERS.forEach { chapter ->
                    ChapterButton(
                        chapter = chapter,
                        isCompleted = currentStep >= chapter.startStep,
                        onClick = { onJumpToChapter(chapter) }
                    )
                }
            }

            // Reset button
            Button(
                onClick = onResetGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Reset Game",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Close button
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Close", color = Color.White)
            }
        }
    }
}

/**
 * Individual chapter button in the debug menu.
 */
@Composable
private fun ChapterButton(
    chapter: Chapter,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isCompleted) AccentOrange else Color(0xFFE0E0E0)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = chapter.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCompleted) Color.White else Color.DarkGray
            )
            Text(
                text = "Step ${chapter.startStep}: ${chapter.description}",
                fontSize = 10.sp,
                color = if (isCompleted) Color.White.copy(alpha = 0.8f) else Color.Gray
            )
        }
    }
}