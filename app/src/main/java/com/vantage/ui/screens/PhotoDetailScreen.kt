package com.vantage.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vantage.models.EnhancementInfo
import com.vantage.models.PhotoPair

@Composable
fun PhotoDetailScreen(
    photos: List<PhotoPair>,
    initialIndex: Int,
    enhancementMetadata: Map<Long, EnhancementInfo> = emptyMap(),
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    var showEnhanced by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pair = photos[page]
            Crossfade(
                targetState = showEnhanced,
                animationSpec = tween(300),
                label = "photo_toggle"
            ) { enhanced ->
                AsyncImage(
                    model = if (enhanced) pair.enhancedUri else pair.originalUri,
                    contentDescription = if (enhanced) "Enhanced" else "Original",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Info button (top right, only when viewing enhanced)
        val currentPair = photos.getOrNull(pagerState.currentPage)
        val info = currentPair?.let { enhancementMetadata[it.captureTimestamp] }

        if (showEnhanced && info != null) {
            IconButton(
                onClick = { showInfo = !showInfo },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    if (showInfo) Icons.Default.Close else Icons.Default.Info,
                    contentDescription = "Enhancement Info",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Info overlay
        AnimatedVisibility(
            visible = showInfo && info != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (info != null) {
                EnhancementInfoPanel(info)
            }
        }

        // Original / Enhanced toggle
        if (!showInfo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TogglePill("Original", selected = !showEnhanced) { showEnhanced = false }
                TogglePill("Enhanced", selected = showEnhanced) { showEnhanced = true }
            }
        }
    }
}

@Composable
fun PhotoDetailScreen(
    photoPair: PhotoPair,
    onBack: () -> Unit
) {
    PhotoDetailScreen(photos = listOf(photoPair), initialIndex = 0, onBack = onBack)
}

@Composable
private fun EnhancementInfoPanel(info: EnhancementInfo) {
    val gold = Color(0xFFE0C040)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Enhancement Details", color = gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (info.voicePrompt.isNotBlank()) {
            InfoRow("Voice Prompt", "\"${info.voicePrompt}\"")
            Spacer(Modifier.height(8.dp))
        }

        if (info.referenceImageUrl.isNotBlank()) {
            SectionHeader("Reference Image")
            AsyncImage(
                model = info.referenceImageUrl,
                contentDescription = "Reference image from Unsplash",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.height(12.dp))
        }

        SectionHeader("Filter")
        InfoRow("Style", info.filter.displayName)
        InfoRow("Category", info.filter.category)

        Spacer(Modifier.height(8.dp))
        SectionHeader("Camera Settings")
        InfoRow("ISO", info.iso.toString())
        InfoRow("Shutter", "1/${info.shutter}s")
        InfoRow("White Balance", info.whiteBalance)
        InfoRow("Zoom", "${info.zoom}x")

        Spacer(Modifier.height(8.dp))
        SectionHeader("Post-Processing")
        InfoRow("Brightness", formatValue(info.brightness, "0"))
        InfoRow("Contrast", formatValue(info.contrast, "1.0"))
        InfoRow("Saturation", formatValue(info.saturation, "1.0"))
        InfoRow("Gamma", formatValue(info.gamma, "1.0"))

        if (info.sceneDescription.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            SectionHeader("AI Analysis")
            Text(info.sceneDescription, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }

        if (info.aiReasoning.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(info.aiReasoning, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }

        if (info.photographyTip.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Photography Tip")
            Text(info.photographyTip, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        color = Color(0xFFE0C040).copy(alpha = 0.7f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatValue(value: Float, defaultStr: String): String {
    val formatted = if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        String.format("%.2f", value)
    }
    return if (formatted == defaultStr) "$formatted (default)" else formatted
}

@Composable
private fun TogglePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFFE0C040) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
