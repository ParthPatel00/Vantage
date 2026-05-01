package com.vantage.ui.pro

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.vantage.settings.AdvancedSettingsRegistry
import com.vantage.ui.theme.AIAccentBlue
import com.vantage.ui.theme.White
import com.vantage.viewmodel.CameraViewModel

@Composable
fun ProSettingsDropdown(
    expanded: Boolean,
    activeAdjustment: AdvancedSettingsRegistry.SettingType,
    onAdjustmentSelected: (AdvancedSettingsRegistry.SettingType) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        // Limit height to approximately 5 items (each item ~48dp + padding)
        val dropdownHeight = (48 * 5 + 16).dp

        LazyColumn(
            state = listState,
            modifier = Modifier
                .width(180.dp)
                .heightIn(max = dropdownHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(8.dp)
                .drawWithContent {
                    drawContent()
                    val firstVisibleElementIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                    val needScrollbar = listState.layoutInfo.totalItemsCount > listState.layoutInfo.visibleItemsInfo.size

                    if (needScrollbar && firstVisibleElementIndex != null) {
                        val elementHeight = this.size.height / listState.layoutInfo.totalItemsCount
                        val scrollbarHeight = listState.layoutInfo.visibleItemsInfo.size * elementHeight
                        val scrollbarOffsetY = firstVisibleElementIndex * elementHeight

                        drawRect(
                            color = White.copy(alpha = 0.3f),
                            topLeft = Offset(this.size.width - 4.dp.toPx(), scrollbarOffsetY),
                            size = Size(2.dp.toPx(), scrollbarHeight),
                        )
                    }
                }
        ) {
            items(AdvancedSettingsRegistry.ALL_SETTINGS) { setting ->
                SettingsItem(
                    label = setting.label, 
                    isSelected = activeAdjustment == setting.type
                ) {
                    onAdjustmentSelected(setting.type)
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AIAccentBlue.copy(alpha = 0.3f) else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) AIAccentBlue else White,
            fontSize = 14.sp
        )
    }
}
