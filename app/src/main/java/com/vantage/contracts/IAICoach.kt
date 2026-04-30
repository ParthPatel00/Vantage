package com.vantage.contracts

import android.content.Context
import com.vantage.models.CoachingResult

interface IAICoach {
    suspend fun initialize(context: Context, modelPath: String)
    fun isReady(): Boolean
    suspend fun analyzeFrame(framePath: String): CoachingResult
    suspend fun sendVoiceCommand(text: String): CoachingResult
    suspend fun matchInspoStyle(description: String, framePath: String): CoachingResult
    fun close()
}
