package com.vantage.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.vantage.camera.standard.LensSwitchingProvider
import com.vantage.ui.theme.AIAccentBlue
import com.vantage.ui.theme.White

/**
 * Circular "pill" buttons for switching between physical sensors.
 */
@Composable
fun LensPillSelector(
    availableLenses: List<LensSwitchingProvider.LensInfo>,
    selectedLensId: String,
    onLensSelected: (LensSwitchingProvider.LensInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        availableLenses.forEach { lens ->
            val isSelected = lens.physicalId == selectedLensId || (lens.physicalId == null && lens.logicalId == selectedLensId)
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AIAccentBlue else Color.Transparent)
                    .clickable { onLensSelected(lens) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lens.label,
                    color = White,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
