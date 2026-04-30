package com.vantage.models

data class UnsplashPhoto(
    val id: String,
    val thumbUrl: String,
    val smallUrl: String,
    val regularUrl: String,
    val photographerName: String,
    val photographerUrl: String,
    val altDescription: String,
    val downloadLocation: String
)
