package com.vantage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vantage.models.CameraUiState
import com.vantage.ui.theme.Black
import com.vantage.ui.theme.SecondaryText
import com.vantage.viewmodel.CameraViewModel

@Composable
fun CameraScreen(viewModel: CameraViewModel, uiState: CameraUiState) {
    // Phase 1C builds out the full UI with viewfinder, filter strip,
    // controls, chat bubbles, inspo row, and overlays.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Camera viewfinder placeholder", color = SecondaryText)
    }
}
