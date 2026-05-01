package com.vantage.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vantage.models.UnsplashPhoto

private val CardBg = Color(0xCC1A1A1A)        // 80% black
private val Accent = Color(0xFF2979FF)        // matches AIAccentBlue
private val ThumbBg = Color(0x661A1A1A)
private val LabelWhite = Color(0xFFFFFFFF)

/**
 * Floating top-right inspiration card. Shown only when [photos] is non-empty. Two states:
 * a 2-col grid of 6 thumbs, or a hero-plus-strip view once the user picks one.
 */
@Composable
fun InspoCard(
    photos: List<UnsplashPhoto>,
    selected: UnsplashPhoto?,
    onSelect: (UnsplashPhoto) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = photos.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(if (selected != null) 156.dp else 144.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Inspo",
                        color = LabelWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(LabelWhite.copy(alpha = 0.12f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close inspiration",
                            tint = LabelWhite.copy(alpha = 0.85f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                if (selected != null) {
                    SelectedHero(photo = selected)
                    val remaining = photos.filter { it.id != selected.id }
                    if (remaining.isNotEmpty()) {
                        ThumbGrid(
                            photos = remaining,
                            selectedId = selected.id,
                            thumbSize = 36.dp,
                            columns = 3,
                            onSelect = onSelect
                        )
                    }
                } else {
                    ThumbGrid(
                        photos = photos.take(6),
                        selectedId = null,
                        thumbSize = 60.dp,
                        columns = 2,
                        onSelect = onSelect
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedHero(photo: UnsplashPhoto) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ThumbBg)
                .border(2.dp, Accent, RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model = photo.smallUrl.ifBlank { photo.thumbUrl },
                contentDescription = photo.altDescription.ifBlank { "Selected reference" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (photo.photographerName.isNotBlank()) {
            Text(
                text = "by ${photo.photographerName}",
                color = LabelWhite.copy(alpha = 0.7f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ThumbGrid(
    photos: List<UnsplashPhoto>,
    selectedId: String?,
    thumbSize: Dp,
    columns: Int,
    onSelect: (UnsplashPhoto) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        photos.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { photo ->
                    Thumb(
                        photo = photo,
                        size = thumbSize,
                        isSelected = photo.id == selectedId,
                        onClick = { onSelect(photo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumb(
    photo: UnsplashPhoto,
    size: Dp,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(ThumbBg)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        AsyncImage(
            model = photo.thumbUrl,
            contentDescription = photo.altDescription.ifBlank { "Inspiration thumbnail" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size)
        )
    }
}
