package com.vantage.ui.pro

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vantage.models.FilterType
import com.vantage.ui.theme.AIAccentBlue
import com.vantage.ui.theme.White

/**
 * Horizontal selector strip for real-time filters.
 * Shows square samples with names below.
 */
@Composable
fun FilterSelectorStrip(
    visible: Boolean,
    currentFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                listOf(
                    FilterType.NATURAL, FilterType.WARM, FilterType.COOL, FilterType.NOIR,
                    FilterType.VIVID, FilterType.CINEMATIC, FilterType.VINTAGE, FilterType.DRAMATIC
                )
            ) { filter ->
                FilterItem(
                    filter = filter,
                    isSelected = currentFilter == filter,
                    onClick = { onFilterSelected(filter) }
                )
            }
        }
    }
}

@Composable
private fun FilterItem(
    filter: FilterType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(getFilterPreviewColor(filter))
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = if (isSelected) AIAccentBlue else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = filter.displayName,
            color = if (isSelected) AIAccentBlue else White,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun getFilterPreviewColor(filter: FilterType): Color {
    return when(filter) {
        FilterType.NATURAL -> Color(0xFF888888)
        FilterType.WARM -> Color(0xFFFFCC80)
        FilterType.COOL -> Color(0xFF90CAF9)
        FilterType.NOIR -> Color(0xFF444444)
        FilterType.VIVID -> Color(0xFFFF5252)
        FilterType.CINEMATIC -> Color(0xFF006064)
        FilterType.VINTAGE -> Color(0xFFA1887F)
        FilterType.DRAMATIC -> Color(0xFF263238)
        else -> Color.Gray
    }
}
