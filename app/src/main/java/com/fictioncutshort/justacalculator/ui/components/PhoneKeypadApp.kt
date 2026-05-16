package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual keypad. Shows a number display with a soft 3×4 keypad below. The green
 * call button does nothing (it does not place a real call) — it's purely an
 * affordance for the gag.
 *
 * [initialNumber] lets the phonebook pre-fill a contact's number when the user
 * taps a phonebook entry.
 */
@Composable
fun PhoneKeypadApp(
    onClose: () -> Unit,
    initialNumber: String = "",
    onPlaceCall: (String) -> Unit = {}
) {
    var entered by remember { mutableStateOf(initialNumber) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
    ) {
        if (isLandscape) {
            PhoneKeypadLandscape(
                entered = entered,
                onDigit = { entered += it },
                onBackspace = { entered = entered.dropLast(1) },
                onSubmit = { onPlaceCall(entered) },
                onClose = onClose
            )
        } else {
            PhoneKeypadPortrait(
                entered = entered,
                onDigit = { entered += it },
                onBackspace = { entered = entered.dropLast(1) },
                onSubmit = { onPlaceCall(entered) },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun PhoneKeypadPortrait(
    entered: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        KeypadTopBar(onClose = onClose)

        Spacer(Modifier.weight(0.5f))

        KeypadNumberDisplay(entered = entered, fontSize = 36.sp)

        Spacer(Modifier.weight(0.5f))

        KeypadGrid(keySize = 72.dp, verticalPadding = 6.dp, onDigit = onDigit)

        Spacer(Modifier.height(12.dp))

        KeypadActionRow(
            entered = entered,
            actionSize = 72.dp,
            backspaceSize = 64.dp,
            onSubmit = onSubmit,
            onBackspace = onBackspace
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PhoneKeypadLandscape(
    entered: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        KeypadTopBar(onClose = onClose)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KeypadNumberDisplay(entered = entered, fontSize = 28.sp)
                Spacer(Modifier.height(20.dp))
                KeypadActionRow(
                    entered = entered,
                    actionSize = 60.dp,
                    backspaceSize = 52.dp,
                    onSubmit = onSubmit,
                    onBackspace = onBackspace
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                KeypadGrid(keySize = 52.dp, verticalPadding = 3.dp, onDigit = onDigit)
            }
        }
    }
}

@Composable
private fun KeypadTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = Color.White, fontSize = 18.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Phone",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun KeypadNumberDisplay(
    entered: String,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    Text(
        text = if (entered.isEmpty()) "—" else entered,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Light,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun KeypadGrid(
    keySize: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp,
    onDigit: (String) -> Unit
) {
    val rows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )
    rows.forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            row.forEach { (digit, letters) ->
                KeypadKey(digit = digit, letters = letters, size = keySize) {
                    onDigit(digit)
                }
            }
        }
    }
}

@Composable
private fun KeypadActionRow(
    entered: String,
    actionSize: androidx.compose.ui.unit.Dp,
    backspaceSize: androidx.compose.ui.unit.Dp,
    onSubmit: () -> Unit,
    onBackspace: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.size(backspaceSize))
        Box(
            modifier = Modifier
                .size(actionSize)
                .clip(CircleShape)
                .background(Color(0xFF2BB36C))
                .clickable(enabled = entered.isNotEmpty(), onClick = onSubmit),
            contentAlignment = Alignment.Center
        ) {
            Text("📞", fontSize = (actionSize.value * 0.4f).sp)
        }
        Box(
            modifier = Modifier
                .size(backspaceSize)
                .clickable(enabled = entered.isNotEmpty(), onClick = onBackspace),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⌫",
                color = if (entered.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f),
                fontSize = (backspaceSize.value * 0.34f).sp
            )
        }
    }
}

@Composable
private fun KeypadKey(
    digit: String,
    letters: String,
    size: androidx.compose.ui.unit.Dp = 72.dp,
    onClick: () -> Unit
) {
    val digitFontSize = (size.value * 0.36f).sp
    val lettersFontSize = (size.value * 0.125f).sp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                color = Color.White,
                fontSize = digitFontSize,
                fontWeight = FontWeight.Light
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = lettersFontSize,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
