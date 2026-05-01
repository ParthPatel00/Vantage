package com.vantage.api

import android.util.Log
import com.vantage.contracts.IUnsplashClient
import com.vantage.models.UnsplashPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UnsplashClientImpl(
    private val service: UnsplashApiService = UnsplashApiService()
) : IUnsplashClient {

    private val cache = object : LinkedHashMap<String, List<UnsplashPhoto>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<UnsplashPhoto>>): Boolean = size > 10
    }

    override suspend fun searchPhotos(query: String, perPage: Int): Result<List<UnsplashPhoto>> =
        withContext(Dispatchers.IO) {
            // Random page 1..5 so repeated identical queries still surface fresh photos
            // (Unsplash returns up to ~10k results per query for popular terms).
            val page = (1..5).random()
            val key = "$query|$perPage|$page"
            cache[key]?.let { return@withContext Result.success(it) }
            try {
                val raw = service.fetchSearchPhotos(query, perPage, page)
                val photos = parsePhotos(raw)
                cache[key] = photos
                Result.success(photos)
            } catch (t: Throwable) {
                Log.w(TAG, "Unsplash search failed for '$query' page=$page: ${t.message}")
                Result.failure(t)
            }
        }

    private fun parsePhotos(rawJson: String): List<UnsplashPhoto> {
        val results = JSONObject(rawJson).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            val urls = o.optJSONObject("urls") ?: return@mapNotNull null
            val user = o.optJSONObject("user") ?: return@mapNotNull null
            val userLinks = user.optJSONObject("links")
            val links = o.optJSONObject("links")
            UnsplashPhoto(
                id = o.optString("id"),
                thumbUrl = urls.optString("thumb"),
                smallUrl = urls.optString("small"),
                regularUrl = urls.optString("regular"),
                photographerName = user.optString("name"),
                photographerUrl = (userLinks?.optString("html") ?: "").withUtm(),
                altDescription = o.optString("alt_description").takeIf { it.isNotBlank() }
                    ?: o.optString("description"),
                downloadLocation = links?.optString("download_location") ?: ""
            )
        }
    }

    private fun String.withUtm(): String = when {
        isBlank() -> this
        contains("?") -> "$this&utm_source=vantage&utm_medium=referral"
        else -> "$this?utm_source=vantage&utm_medium=referral"
    }

    private companion object { const val TAG = "Unsplash" }
}
