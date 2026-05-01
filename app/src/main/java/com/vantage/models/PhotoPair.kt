package com.vantage.models

import android.net.Uri

data class PhotoPair(
    val originalUri: Uri,
    val enhancedUri: Uri,
    val timestamp: Long,
    val captureTimestamp: Long = 0L
)
