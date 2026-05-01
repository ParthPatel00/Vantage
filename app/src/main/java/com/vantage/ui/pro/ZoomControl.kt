package com.vantage.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vantage.ui.theme.AIAccentBlue
import com.vantage.ui.theme.White
import java.util.Locale

/**
 * Manual zoom control buttons (1x, 2x, 5x) or a slider.
 * Positioned above the mode buttons on the left side.
 */
@Composable
fun ZoomControl(
    currentZoom: Float,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomOptions = listOf(0.6f, 1.0f, 2.0f, 5.0f, 10.0f)
    
    Column(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        zoomOptions.forEach { zoom ->
            val isSelected = currentZoom == zoom
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AIAccentBlue else Color.Transparent)
                    .clickable { onZoomSelected(zoom) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (zoom < 1f) "0.6x" else "${zoom.toInt()}x",
                    color = White,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
