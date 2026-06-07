package com.fictioncutshort.justacalculator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import com.fictioncutshort.justacalculator.R
import kotlinx.coroutines.delay

/**
 * Phone-app popup primitives used by [HomeScreenOverlay].
 *
 * `SimplePhoneAppPopup` — title + body + single Close button.
 * `ChoicePhoneAppPopup` — body + N choice buttons that map to follow-up replies.
 * `GrowingBirdsPopup` — Angry Birds gag: popup grows until it eats the screen
 *                      and auto-closes the app.
 * `CalcFakeNotification` — top-of-screen banner styled as a system notification
 *                          coming from JustACalculator.
 */

private val PopupBg = Color(0xFF1F1F1F)
private val PopupTextColor = Color.White
private val PopupSecondaryTextColor = Color(0xFFCFCFCF)
private val ButtonBg = Color(0xFF3A8DFF)
private val ButtonBgMuted = Color(0xFF3A3A3A)

@Composable
fun SimplePhoneAppPopup(
    body: String,
    onClose: () -> Unit,
    title: String? = null,
    closeLabel: String = "Close"
) {
    PopupScrim(onClose = onClose) {
        PopupCard {
            if (!title.isNullOrEmpty()) {
                Text(
                    text = title,
                    color = PopupTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = body,
                color = PopupSecondaryTextColor,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            PopupButton(text = closeLabel, onClick = onClose)
        }
    }
}

/**
 * Two-stage popup with a body, a row of choice buttons, then on selection a
 * follow-up body and Close button. [followUpFor] is called per choice and
 * supplies the post-selection text.
 */
@Composable
fun ChoicePhoneAppPopup(
    body: String,
    choices: List<String>,
    followUpFor: (String) -> String,
    onClose: () -> Unit
) {
    var picked by remember { mutableStateOf<String?>(null) }
    PopupScrim(onClose = onClose) {
        PopupCard {
            val current = picked
            if (current == null) {
                Text(
                    text = body,
                    color = PopupSecondaryTextColor,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    choices.forEach { choice ->
                        PopupButton(
                            text = choice,
                            onClick = { picked = choice },
                            modifier = Modifier.widthIn(min = 72.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = followUpFor(current),
                    color = PopupSecondaryTextColor,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                PopupButton(text = "Close", onClick = onClose)
            }
        }
    }
}

/**
 * Angry Birds popup — title scales up over ~3s. Once it fills the viewport, the
 * app auto-closes via [onClose]. Cannot be dismissed by tapping outside.
 */
@Composable
fun GrowingBirdsPopup(onClose: () -> Unit) {
    var target by remember { mutableFloatStateOf(0.5f) }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 3000),
        label = "birdsScale"
    )

    LaunchedEffect(Unit) {
        target = 12f  // target way past viewport so it visibly engulfs
        delay(3200)
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFFFE082))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = "file:///android_asset/phonescreen/phonedetour/birds.svg",
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "remember us?",
                    color = Color(0xFF3A2A00),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Slim banner at the top of the screen styled as a system notification from
 * JustACalculator. Auto-dismisses after [autoHideMs] unless tapped first.
 *
 * When [persistent] is true, the banner ignores both timers and taps — it only
 * disappears when its owner clears it. Used for first-time onboarding nudges
 * that a tester missed because they vanished mid-exploration.
 */
@Composable
fun CalcFakeNotification(
    body: String,
    onDismiss: () -> Unit,
    autoHideMs: Long = 4000L,
    persistent: Boolean = false
) {
    if (!persistent) {
        LaunchedEffect(body) {
            delay(autoHideMs)
            onDismiss()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 0.dp)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xEE2A2A2A))
                .then(
                    if (persistent) Modifier
                    else Modifier.clickable(onClick = onDismiss)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.calc_app_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text(
                    text = "JustACalculator",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = body,
                    color = PopupSecondaryTextColor,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun PopupScrim(
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        // Inner box swallows the scrim click so taps inside the card don't dismiss.
        Box(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .clickable(enabled = false) {}
        ) {
            content()
        }
    }
}

@Composable
private fun PopupCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PopupBg)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

@Composable
private fun PopupButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ButtonBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
