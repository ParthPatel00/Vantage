package com.vantage.ai

import android.util.Log
import com.vantage.contracts.IUnsplashClient
import com.vantage.models.UnsplashPhoto

/**
 * Tool surface that Gemma can invoke through LiteRT-LM function calling. For now the
 * inspiration tool is also called directly from CameraViewModel as a keyword fallback so
 * the demo still works before tool registration lands in [GemmaEngine].
 */
class CameraToolSet(private val unsplashClient: IUnsplashClient) {

    /**
     * Fetch a small set of inspiration photos from Unsplash to help the user shape their
     * shot. Gemma should call this when the user asks for visual references — pose ideas,
     * background ideas, style examples — not on every analysis cycle.
     */
    suspend fun findInspirationPhotos(
        userRequest: String,
        sceneDescription: String,
        inspirationType: String,
        unsplashQuery: String,
        voiceMessage: String
    ): ToolResult.Inspiration {
        Log.d(TAG, "findInspirationPhotos type=$inspirationType q='$unsplashQuery' req='$userRequest'")
        val result = unsplashClient.searchPhotos(unsplashQuery, perPage = 6)
        val photos = result.getOrElse {
            Log.w(TAG, "Unsplash search failed for '$unsplashQuery': ${it.message}")
            return ToolResult.Inspiration(
                photos = emptyList(),
                voiceMessage = "I couldn't load inspiration photos right now, but I can still coach the shot.",
                inspirationType = inspirationType,
                unsplashQuery = unsplashQuery,
                fallback = true
            )
        }
        if (photos.isEmpty()) {
            return ToolResult.Inspiration(
                photos = emptyList(),
                voiceMessage = "I couldn't find references for that, but I can still coach the shot.",
                inspirationType = inspirationType,
                unsplashQuery = unsplashQuery,
                fallback = true
            )
        }
        return ToolResult.Inspiration(
            photos = photos,
            voiceMessage = voiceMessage,
            inspirationType = inspirationType,
            unsplashQuery = unsplashQuery,
            fallback = false
        )
    }

    private companion object { const val TAG = "CameraToolSet" }
}

sealed class ToolResult {
    data class Inspiration(
        val photos: List<UnsplashPhoto>,
        val voiceMessage: String,
        val inspirationType: String,
        val unsplashQuery: String,
        val fallback: Boolean
    ) : ToolResult()
}
