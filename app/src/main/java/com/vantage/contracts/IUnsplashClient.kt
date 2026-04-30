package com.vantage.contracts

import com.vantage.models.UnsplashPhoto

interface IUnsplashClient {
    suspend fun searchPhotos(query: String, perPage: Int = 6): Result<List<UnsplashPhoto>>
}
