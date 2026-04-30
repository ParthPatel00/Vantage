package com.vantage.models

import android.net.Uri

data class CameraUiState(
    val isCoachingActive: Boolean = false,
    val appMode: AppMode = AppMode.DO_IT_FOR_ME,
    val currentFilter: FilterType = FilterType.NATURAL,
    val chatMessages: List<ChatMessage> = emptyList(),
    val inspoPhotos: List<UnsplashPhoto> = emptyList(),
    val selectedInspoPhoto: UnsplashPhoto? = null,
    val flashMode: FlashMode = FlashMode.OFF,
    val isListening: Boolean = false,
    val pendingUserActions: List<String> = emptyList(),
    val countdownValue: Int = 0,
    val readyToCapture: Boolean = false,
    val modelLoaded: Boolean = false,
    val modelLoadProgress: Float = 0f,
    val lastCapturedUri: Uri? = null,
    val errorMessage: String? = null
)
