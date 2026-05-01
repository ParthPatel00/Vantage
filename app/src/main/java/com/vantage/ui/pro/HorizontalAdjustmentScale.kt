package com.vantage.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vantage.settings.AdvancedSettingsRegistry
import com.vantage.ui.theme.AIAccentBlue
import com.vantage.ui.theme.White
import java.util.Locale
import kotlin.math.*

/**
 * A horizontal adjustment scale that appears above the shutter button.
 */
@Composable
fun HorizontalAdjustmentScale(
    type: AdvancedSettingsRegistry.SettingType,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine if this setting should use a logarithmic scale for smoother control
    val isLogarithmic = type == AdvancedSettingsRegistry.SettingType.SHUTTER || 
                        type == AdvancedSettingsRegistry.SettingType.ISO
    
    val sliderValue: Float
    val onSliderValueChange: (Float) -> Unit

    if (isLogarithmic) {
        // Map log space [log(min)..log(max)] to slider [0..1]
        val logMin = ln(range.start)
        val logMax = ln(range.endInclusive)
        
        sliderValue = ((ln(value.coerceAtLeast(range.start)) - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
        onSliderValueChange = { normalized ->
            val newValue = exp(logMin + normalized * (logMax - logMin))
            onValueChange(newValue.toFloat())
        }
    } else {
        sliderValue = value
        onSliderValueChange = onValueChange
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (type == AdvancedSettingsRegistry.SettingType.SHUTTER) 
                "SHUTTER: 1/${value.toInt()}s" 
            else 
                "${label.uppercase()}: ${String.format(Locale.US, "%.2f", value)}",
            color = White,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = sliderValue,
            onValueChange = onSliderValueChange,
            valueRange = if (isLogarithmic) 0f..1f else range,
            colors = SliderDefaults.colors(
                thumbColor = White,
                activeTrackColor = AIAccentBlue,
                inactiveTrackColor = White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.height(32.dp)
        )
    }
}
