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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vantage.models.UnsplashPhoto
import com.vantage.ui.theme.AIAccentBlue
import com.vantage.ui.theme.ChatBubbleBg
import com.vantage.ui.theme.White

/**
 * Floating top-right inspiration card. Shown only when [photos] is non-empty.
 *
 * Two visual states:
 *  - No selection — compact 2-col grid of up to 6 thumbnails.
 *  - Selection — selected hero (~132dp tall) above a 3-col strip of remaining thumbs.
 *
 * Tapping a thumbnail invokes [onSelect]. The selected thumbnail gets a blue border.
 */
@Composable
fun InspoCard(
    photos: List<UnsplashPhoto>,
    selected: UnsplashPhoto?,
    onSelect: (UnsplashPhoto) -> Unit,
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
                .background(ChatBubbleBg.copy(alpha = 0.92f))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Inspo",
                    color = White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
                .background(ChatBubbleBg.copy(alpha = 0.6f))
                .border(2.dp, AIAccentBlue, RoundedCornerShape(10.dp))
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
                color = White.copy(alpha = 0.7f),
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
            .background(ChatBubbleBg.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, AIAccentBlue, RoundedCornerShape(8.dp))
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
