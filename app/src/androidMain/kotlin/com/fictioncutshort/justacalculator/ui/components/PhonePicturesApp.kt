package com.fictioncutshort.justacalculator.ui.components

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Gallery of pictures previously captured by the in-story camera. Reads from
 * MediaStore filtered by `RELATIVE_PATH = DCIM/JustACalculator/` (Android 10+),
 * or by display-name prefix `calculator_` on older versions.
 *
 * Empty state shows a plain message — no permission is requested here; if the
 * user never granted READ_MEDIA_IMAGES the list is empty rather than crashing.
 */
@Composable
fun PhonePicturesApp(onClose: () -> Unit) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        images = loadCalculatorImages(context)
        loaded = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
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
                Text("Photos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }

            Spacer(Modifier.height(12.dp))

            when {
                !loaded -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading…", color = Color.White.copy(alpha = 0.7f))
                    }
                }
                images.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No pictures yet.\nOpen Camera to take one.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(images) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { preview = uri }
                            )
                        }
                    }
                }
            }
        }

        // Full-screen preview when an image is tapped
        val current = preview
        if (current != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { preview = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = current,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun loadCalculatorImages(context: Context): List<Uri> {
    val out = mutableListOf<Uri>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME
    )

    val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // RELATIVE_PATH is the directory under DCIM/Pictures/Movies/etc.
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%DCIM/JustACalculator%")
    } else {
        "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?" to arrayOf("calculator_%")
    }
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    try {
        context.contentResolver.query(collection, projection, selection, args, sortOrder)?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.Images.Media._ID)
            if (idCol < 0) return@use
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += android.content.ContentUris.withAppendedId(collection, id)
            }
        }
    } catch (e: SecurityException) {
        // No media-images permission — return what we have (likely empty).
    }
    return out
}
