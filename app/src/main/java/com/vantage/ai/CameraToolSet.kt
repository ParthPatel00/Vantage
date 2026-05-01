package com.vantage.ai

import android.util.Log
import com.vantage.contracts.IUnsplashClient
import com.vantage.models.UnsplashPhoto

/**
 * Tool surface that Gemma can invoke through LiteRT-LM function calling.
 *
 * Each function below is described in the same shape that will become a `@Tool` /
 * `@ToolParam` declaration once full LiteRT-LM tool registration is wired into
 * `CoachingSession.start()`. Until then, [CameraViewModel] also calls these
 * directly from the keyword-fallback voice path so the demo behaves correctly.
 *
 * To migrate to real tool calling, annotate each function with `@Tool(description=...)`
 * and each parameter with `@ToolParam(description=...)` matching the KDoc here, and
 * register an instance via `tools = listOf(tool(CameraToolSet(...)))` in
 * `ConversationConfig`.
 */
class CameraToolSet(private val unsplashClient: IUnsplashClient) {

    /**
     * Fetch a small set of inspiration photos from Unsplash to help the user shape
     * their shot. Gemma should call this when the user asks for visual references —
     * pose ideas, background ideas, style examples — not on every analysis cycle.
     *
     * @param userRequest      the user's natural language request, e.g. "find me poses
     *                         to do" or "show me background ideas".
     * @param sceneDescription short description of the current camera scene: person,
     *                         pose, background, objects, lighting, mood, camera angle.
     * @param inspirationType  one of "human_pose", "background", "style", "composition",
     *                         "mood". Pose requests should use "human_pose".
     * @param unsplashQuery    concise Unsplash query Gemma builds from the request and
     *                         scene. Pose queries should include terms like portrait
     *                         pose, posing ideas, full body portrait, fashion pose,
     *                         editorial pose, or environment-specific terms.
     * @param voiceMessage     short natural spoken response (under two sentences)
     *                         explaining what references are being shown.
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

/**
 * Result of a tool invocation. Sealed so the caller (ViewModel today, future
 * `ToolCallParser` once Gemma drives this) can handle the side effects without
 * coupling to the tool's wiring.
 */
sealed class ToolResult {
    data class Inspiration(
        val photos: List<UnsplashPhoto>,
        val voiceMessage: String,
        val inspirationType: String,
        val unsplashQuery: String,
        val fallback: Boolean
    ) : ToolResult()
}
