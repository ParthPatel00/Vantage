package com.vantage.api

import com.vantage.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class UnsplashApiService(
    private val client: OkHttpClient = defaultClient,
    private val accessKey: String = BuildConfig.UNSPLASH_ACCESS_KEY
) {

    fun fetchSearchPhotos(query: String, perPage: Int, page: Int = 1): String {
        require(accessKey.isNotBlank()) { "UNSPLASH_ACCESS_KEY is missing" }
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = "$BASE_URL/search/photos?query=$encoded&per_page=$perPage&page=$page&client_id=$accessKey"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Unsplash returned HTTP ${response.code}")
            return response.body?.string() ?: error("Unsplash returned empty body")
        }
    }

    fun downloadImage(url: String, destination: File): Boolean {
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                response.body?.byteStream()?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: return false
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val BASE_URL = "https://api.unsplash.com"
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
