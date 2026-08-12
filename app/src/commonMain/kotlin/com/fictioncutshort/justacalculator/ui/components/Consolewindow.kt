package com.fictioncutshort.justacalculator.ui.components

import com.fictioncutshort.justacalculator.platform.formatFixed
import com.fictioncutshort.justacalculator.platform.AppContext
import com.fictioncutshort.justacalculator.platform.screenMetrics
import com.fictioncutshort.justacalculator.platform.currentAppContext
import com.fictioncutshort.justacalculator.platform.appPackageSizeBytes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 * Marks a line as disabled. A line beginning with this character renders in a
 * dimmed green and the marker itself is stripped before display — used for the
 * Software Update entry before its unlock code has been entered, so the player
 * can see the option exists without it looking selectable.
 *
 * Kept as a sentinel rather than threading styled text through every menu:
 * only one line in the whole console needs it, and the menus stay plain
 * strings that are trivial to read and edit.
 */
private const val DIM = '\u0001'

private val ConsoleGreen = Color(0xFF00FF00)
private val ConsoleGreenDim = Color(0xFF1F5F1F)

/**
 * Splits the raw menu text into an [AnnotatedString], applying the dim style
 * to any line flagged with [DIM].
 */
private fun styleConsoleText(raw: String): AnnotatedString = buildAnnotatedString {
    raw.lines().forEachIndexed { index, line ->
        if (index > 0) append("\n")
        if (line.startsWith(DIM)) {
            withStyle(SpanStyle(color = ConsoleGreenDim)) { append(line.substring(1)) }
        } else {
            append(line)
        }
    }
}

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
    darkModeEnabled: Boolean,
    totalScreenTimeMs: Long,
    totalCalculations: Int,
    dateFormat24h: Boolean?,
    autoDateTime: Boolean?,
    updateUnlocked: Boolean,
    updateAdminEntered: Boolean,
    softwareUpdated: Boolean,
    onOpenContributeLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = currentAppContext()

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
        darkModeEnabled = darkModeEnabled,
        totalScreenTimeMs = totalScreenTimeMs,
        totalCalculations = totalCalculations,
        dateFormat24h = dateFormat24h,
        autoDateTime = autoDateTime,
        updateUnlocked = updateUnlocked,
        updateAdminEntered = updateAdminEntered,
        softwareUpdated = softwareUpdated,
        context = context
    )

    // Position console in the middle band — fixed height so it never covers buttons.
    // In landscape the keyboard takes a much taller share of the short side, so we
    // shrink the console height + top padding to keep the keyboard reachable.
    val configuration = screenMetrics()
    val screenHeight = configuration.heightDp
    val isLandscape =
        configuration.isLandscape
    val consoleTopPadding = if (isLandscape) {
        (screenHeight * 0.06f).coerceAtLeast(16.dp)
    } else {
        (screenHeight * 0.18f).coerceAtLeast(100.dp)
    }
    val consoleHeight = if (isLandscape) {
        (screenHeight * 0.55f).coerceIn(140.dp, 220.dp)
    } else {
        (screenHeight * 0.40f).coerceIn(180.dp, 300.dp)
    }

    // In landscape, restrict the console to the left half of the screen so it
    // sits alongside (rather than over) the calculator keyboard on the right.
    val consoleFillFraction = if (isLandscape) 0.5f else 1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = consoleTopPadding + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                start = 12.dp,
                end = 12.dp
            )
    ) {
        // Console container with retro terminal styling
        Box(
            modifier = Modifier
                .fillMaxWidth(consoleFillFraction)
                .height(consoleHeight)
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
                    text = styleConsoleText(menuContent),
                    color = ConsoleGreen,  // Green terminal text
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
    darkModeEnabled: Boolean,
    totalScreenTimeMs: Long,
    totalCalculations: Int,
    dateFormat24h: Boolean?,
    autoDateTime: Boolean?,
    updateUnlocked: Boolean,
    updateAdminEntered: Boolean,
    softwareUpdated: Boolean,
    context: AppContext
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

        1 -> {
            // Software update stays dimmed (and inert) until 7384 is entered.
            // The marker is stripped by styleConsoleText.
            val updateLine =
                if (updateUnlocked) " 4. Software update"
                else "$DIM 4. Software update [disabled]"
            """
                |═══════════════════════════════════
                |        GENERAL SETTINGS
                |═══════════════════════════════════
                |
                | 1. Design settings
                | 2. Date & time
                | 3. Language & region
                |$updateLine
                | 5. User guide
                |
                | 88. Back
                | 99. Exit console
                |═══════════════════════════════════
            """.trimMargin()
        }

        11 -> {
            val formatLabel = when (dateFormat24h) {
                null -> "Not set"
                true -> "24-hour"
                false -> "12-hour"
            }
            val autoLabel = when (autoDateTime) {
                null -> "Not set"
                true -> "Yes"
                false -> "No"
            }
            // Manual mode has nothing to configure beyond the switch itself —
            // the calculator just follows the device clock and says so.
            val manualNote =
                if (autoDateTime == false) "\n| Defaulting to system clock" else ""
            """
                |═══════════════════════════════════
                |          DATE & TIME
                |═══════════════════════════════════
                |
                | Date format: $formatLabel
                | Set automatically: $autoLabel$manualNote
                |
                | 1. Date format
                | 2. Set date & time automatically
                |
                | 88. Back
                | 99. Exit console
                |═══════════════════════════════════
            """.trimMargin()
        }

        12 -> {
            val currentLabel = when (dateFormat24h) {
                null -> "Not set"
                true -> "24-hour"
                false -> "12-hour"
            }
            """
                |═══════════════════════════════════
                |          DATE FORMAT
                |═══════════════════════════════════
                |
                | Current setting: $currentLabel
                |
                | 1. 12-hour
                | 2. 24-hour
                |
                | 88. Back
                | 99. Exit console
                |═══════════════════════════════════
            """.trimMargin()
        }

        13 -> {
            val currentLabel = when (autoDateTime) {
                null -> "Not set"
                true -> "Yes"
                false -> "No"
            }
            """
                |═══════════════════════════════════
                |   SET DATE & TIME AUTOMATICALLY
                |═══════════════════════════════════
                |
                | Current setting: $currentLabel
                |
                | 1. Yes
                | 2. No
                |
                | 88. Back
                | 99. Exit console
                |═══════════════════════════════════
            """.trimMargin()
        }

        14 -> """
            |═══════════════════════════════════
            |       LANGUAGE & REGION
            |═══════════════════════════════════
            |
            | Set automatically by device
            |
            | No configurable options available.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        15 -> if (!updateAdminEntered) """
            |═══════════════════════════════════
            |        SOFTWARE UPDATE
            |═══════════════════════════════════
            |
            | Access code required.
            | Enter code and confirm with ++
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin() else if (softwareUpdated) """
            |═══════════════════════════════════
            |        SOFTWARE UPDATE
            |═══════════════════════════════════
            |
            | Software is up to date.
            |
            | No further updates available.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin() else if (dateFormat24h == true && autoDateTime == false) """
            |═══════════════════════════════════
            |        SOFTWARE UPDATE
            |═══════════════════════════════════
            |
            | Update software?
            |
            | 1. Yes
            | 2. No
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin() else """
            |═══════════════════════════════════
            |        SOFTWARE UPDATE
            |═══════════════════════════════════
            |
            | To update, set compatible
            | date & time format.
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        // Deliberately compact: the console box is a fixed ~300.dp and this
        // screen has to fit without scrolling, because the unlock code on the
        // last content line is the whole point of the page. Adding a line here
        // pushes 7384 out of sight.
        16 -> """
            |═══════════════════════════════════
            |            USER GUIDE
            |═══════════════════════════════════
            | ++        = YES
            | --        = NO
            | [XXXX]++  = numerical answer
            | [N]++     = select menu item N
            | [CODE]++  = enter a code
            | 88++      = back
            | 99++      = exit console
            | 7384++    = enable software update
            |
            | 88. Back / 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        17 -> """
            |═══════════════════════════════════
            |        SOFTWARE UPDATE
            |═══════════════════════════════════
            |
            | Installing update...
            |
            | Update complete.
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
            | 2. Contribute
            | 3. Connectivity settings
            |
            | 88. Back
            | 99. Exit console
            |═══════════════════════════════════
        """.trimMargin()

        3 -> {
            // Get actual app size from APK file
            val appSize = try {
                val sizeInMB = appPackageSizeBytes(context) / (1024.0 * 1024.0)
                formatFixed(sizeInMB, 2) + " MB"
            } catch (_: Exception) {
                "Unknown"
            }

            // Format screen time as HH:MM:SS
            val hours = totalScreenTimeMs / (1000 * 60 * 60)
            val minutes = (totalScreenTimeMs / (1000 * 60)) % 60
            val seconds = (totalScreenTimeMs / 1000) % 60
            val screenTimeFormatted = listOf(hours, minutes, seconds)
                .joinToString(":") { it.toString().padStart(2, '0') }

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
            | Contacts & phone: Granted
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
            | Dark mode: ${if (darkModeEnabled) "ON" else "OFF"}
            |
            | 1. Toggle dark mode
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
            | 88. Back
            | 99. Exit console
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