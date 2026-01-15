package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * ConsoleWindow.kt
 *
 * The hidden "developer console" that appears during step 112+.
 * Player must navigate menus to disable ads and free the calculator.
 *
 * Access code: 353942320485
 * Admin code: 12340
 *
 * Navigation:
 * - Enter number + (++) to select menu item
 * - 88(++) = Go back
 * - 99(++) = Exit console
 */

/**
 * Console overlay that displays menu content.
 *
 * @param consoleStep Current menu step (determines what content to show)
 * @param adminCodeEntered True if admin code has been entered
 * @param currentInput Current number being typed (shown at prompt)
 * @param bannersDisabled True if banner ads have been disabled
 * @param fullScreenAdsEnabled True if full-screen ads are enabled
 * @param totalScreenTimeMs Total time app has been open (for app info display)
 * @param totalCalculations Total calculations performed (for app info display)
 * @param onOpenContributeLink Called when "Contribute" menu item is selected
 * @param modifier Modifier for positioning
 */
@Composable
fun ConsoleWindow(
    consoleStep: Int,
    adminCodeEntered: Boolean,
    currentInput: String,
    bannersDisabled: Boolean,
    fullScreenAdsEnabled: Boolean,
    totalScreenTimeMs: Long,
    totalCalculations: Int,
    onOpenContributeLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Trigger link opening when on contribute step
    LaunchedEffect(consoleStep) {
        if (consoleStep == 31) {
            onOpenContributeLink()
        }
    }

    // Generate menu content based on current step
    val menuContent = getConsoleMenuContent(
        consoleStep = consoleStep,
        adminCodeEntered = adminCodeEntered,
        bannersDisabled = bannersDisabled,
        fullScreenAdsEnabled = fullScreenAdsEnabled,
        totalScreenTimeMs = totalScreenTimeMs,
        totalCalculations = totalCalculations,
        context = context
    )

    // Calculate padding to position console in middle of screen
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val consoleTopPadding = (screenHeight * 0.25f).coerceAtLeast(150.dp)
    val consoleBottomPadding = (screenHeight * 0.30f).coerceAtLeast(200.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = consoleTopPadding + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = consoleBottomPadding,
                start = 12.dp,
                end = 12.dp
            )
    ) {
        // Console container with retro terminal styling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .padding(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A1A0A))  // Dark green tint
                    .padding(12.dp)
            ) {
                // Scrollable menu content
                Text(
                    text = menuContent,
                    color = Color(0xFF00FF00),  // Green terminal text
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )

                // Input prompt line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF001500))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "> ",
                        color = Color(0xFF00FF00),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (currentInput == "0") "_" else "${currentInput}_",
                        color = Color(0xFF00FF00),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Generates the menu content string for the current console step.
 */
private fun getConsoleMenuContent(
    consoleStep: Int,
    adminCodeEntered: Boolean,
    bannersDisabled: Boolean,
    fullScreenAdsEnabled: Boolean,
    totalScreenTimeMs: Long,
    totalCalculations: Int,
    context: android.content.Context
): String {
    return when (consoleStep) {
        0 -> """
            |═══════════════════════════════════
            |        SYSTEM CONSOLE v1.2
            |═══════════════════════════════════
            |
            | 1. General settings
            | 2. Administrator settings
            | 3. Application information
            |
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        1 -> """
            |═══════════════════════════════════
            |        GENERAL SETTINGS
            |═══════════════════════════════════
            |
            | No configurable options available.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        2 -> if (!adminCodeEntered) """
            |═══════════════════════════════════
            |      ADMINISTRATOR SETTINGS
            |═══════════════════════════════════
            |
            | Access code required.
            | Enter code and confirm with ++
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin() else """
            |═══════════════════════════════════
            |      ADMINISTRATOR SETTINGS
            |═══════════════════════════════════
            |
            | 1. Permissions & allowances
            | 2. Design settings
            | 3. Contribute
            | 4. Connectivity settings
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        3 -> {
            // Get actual app size from APK file
            val appSize = try {
                val appFile = File(context.applicationInfo.sourceDir)
                val sizeInMB = appFile.length() / (1024.0 * 1024.0)
                String.format("%.2f MB", sizeInMB)
            } catch (_: Exception) {
                "Unknown"
            }

            // Format screen time as HH:MM:SS
            val hours = totalScreenTimeMs / (1000 * 60 * 60)
            val minutes = (totalScreenTimeMs / (1000 * 60)) % 60
            val seconds = (totalScreenTimeMs / 1000) % 60
            val screenTimeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

            """
                |═══════════════════════════════════
                |      APPLICATION INFORMATION
                |═══════════════════════════════════
                |
                | Status: Operational
                |         Administrator access
                |         restricted
                |
                | Version: 1.2
                | Developer: FictionCutShort
                | Licence: All licences and rights
                |          reserved.
                |          For someone special.
                | Size: $appSize
                |
                | --- Usage Statistics ---
                | Screen time: $screenTimeFormatted
                | Calculations: $totalCalculations
                |
                | 88. Back
                | 99. Exit console
                |═══════════════════════════════════
            """.trimMargin()
        }

        4 -> """
            |═══════════════════════════════════
            |      CONNECTIVITY SETTINGS
            |═══════════════════════════════════
            |
            | 1. Network preferences
            | 2. Promotion & advertising options
            | 3. Data usage
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        41 -> """
            |═══════════════════════════════════
            |      NETWORK PREFERENCES
            |═══════════════════════════════════
            |
            | Current setting: Default
            |
            | No configurable options available.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        43 -> """
            |═══════════════════════════════════
            |          DATA USAGE
            |═══════════════════════════════════
            |
            | Current setting: Minimal
            |
            | No configurable options available.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        5 -> {
            val bannerStatus = if (bannersDisabled) "Disabled" else "Enabled"
            val fullScreenStatus = if (fullScreenAdsEnabled) "Enabled" else "Disabled"
            """
                |═══════════════════════════════════
                |   PROMOTION & ADVERTISING OPTIONS
                |═══════════════════════════════════
                |
                | Banner advertising: $bannerStatus
                | Full-screen advertising: $fullScreenStatus
                |
                | 1. Banner advertising
                | 2. Full-screen advertising
                |
                | 88. Back
                | 99. Exit console
                |═══════════════════════════════════
            """.trimMargin()
        }

        51 -> {
            val currentStatus = if (bannersDisabled) "Disabled" else "Enabled"
            """
                |═══════════════════════════════════
                |      BANNER ADVERTISING
                |═══════════════════════════════════
                |
                | Current status: $currentStatus
                |
                | 1. Enable
                | 2. Disable
                |
                | Note: Disabling banner ads will
                | enable full-screen advertising.
                |
                | 88. Back
                | 99. Exit console
                |═══════════════════════════════════
            """.trimMargin()
        }

        52 -> {
            val currentStatus = if (fullScreenAdsEnabled) "Enabled" else "Disabled"
            """
                |═══════════════════════════════════
                |    FULL-SCREEN ADVERTISING
                |═══════════════════════════════════
                |
                | Current status: $currentStatus
                |
                | 1. Enable
                | 2. Disable
                |
                | Note: Disabling full-screen ads
                | will enable banner advertising.
                |
                | 88. Back
                | 99. Exit console
                |═══════════════════════════════════
            """.trimMargin()
        }

        6 -> """
            |═══════════════════════════════════
            |      PERMISSIONS & ALLOWANCES
            |═══════════════════════════════════
            |
            | Camera access: Granted
            | Storage access: Granted
            | Notifications: Granted
            | Contacts & phone: Not requested
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        7 -> """
            |═══════════════════════════════════
            |        DESIGN SETTINGS
            |═══════════════════════════════════
            |
            | Dark mode: Unavailable
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        99 -> """
            |═══════════════════════════════════
            |        SETTINGS UPDATED
            |═══════════════════════════════════
            |
            | Banner advertising has been
            | DISABLED.
            |
            | Full-screen advertising has been
            | ENABLED.
            |
            | Changes will take effect
            | immediately.
            |
            | Press 99++ to close console.
            |═══════════════════════════════════
        """.trimMargin()

        else -> """
            |═══════════════════════════════════
            |           ERROR
            |═══════════════════════════════════
            |
            | Unknown menu state.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()
    }
}