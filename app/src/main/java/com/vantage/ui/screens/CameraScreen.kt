package com.vantage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vantage.models.CameraUiState
import com.vantage.ui.components.CoachingOverlay
import com.vantage.ui.components.WebcamPreview
import com.vantage.ui.theme.Black
import com.vantage.viewmodel.CameraViewModel

@Composable
fun CameraScreen(viewModel: CameraViewModel, uiState: CameraUiState) {
    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        WebcamPreview(modifier = Modifier.fillMaxSize())
        CoachingOverlay(
            suggestion = uiState.pendingUserActions.firstOrNull(),
            modifier = Modifier.fillMaxSize()
        )
    }
}
