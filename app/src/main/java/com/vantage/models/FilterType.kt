package com.vantage.models

enum class FilterType(val displayName: String) {
    NATURAL("Natural"),
    WARM("Warm"),
    COOL("Cool"),
    NOIR("Noir"),
    VIVID("Vivid"),
    DRAMATIC("Dramatic"),
    SILVERTONE("Silvertone"),
    CINEMATIC("Cinematic"),
    VINTAGE("Vintage"),
    MUTED("Muted"),
    FADE("Fade"),
    MONO("Mono");

    companion object {
        fun fromString(value: String): FilterType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: NATURAL
    }
}
