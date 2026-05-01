package com.vantage.models

enum class FilterType(val displayName: String, val category: String) {
    // Basic
    NATURAL("Natural", "Basic"),
    WARM("Warm", "Basic"),
    COOL("Cool", "Basic"),
    VIVID("Vivid", "Basic"),
    MUTED("Muted", "Basic"),
    FADE("Fade", "Basic"),

    // B&W
    NOIR("Noir", "B&W"),
    MONO("Mono", "B&W"),
    SILVERTONE("Silvertone", "B&W"),
    TRI_X("Tri-X", "B&W"),

    // Film Stocks
    CINEMATIC("Cinematic", "Film"),
    VINTAGE("Vintage", "Film"),
    KODAK_GOLD("Kodak Gold", "Film"),
    FUJI_VELVIA("Fuji Velvia", "Film"),
    PORTRA("Portra 400", "Film"),
    EKTACHROME("Ektachrome", "Film"),
    SUPERIA("Superia", "Film"),
    PROVIA("Provia", "Film"),

    // Cinematic
    DRAMATIC("Dramatic", "Cinema"),
    TEAL_ORANGE("Teal & Orange", "Cinema"),
    BLADE_RUNNER("Blade Runner", "Cinema"),
    MATRIX("Matrix", "Cinema"),
    MOONLIGHT("Moonlight", "Cinema"),

    // Mood
    GOLDEN_HOUR("Golden Hour", "Mood"),
    BLUE_HOUR("Blue Hour", "Mood"),
    NEON_NIGHT("Neon Night", "Mood"),
    ARCTIC("Arctic", "Mood"),
    MISTY("Misty", "Mood"),

    // Retro
    POLAROID("Polaroid", "Retro"),
    SEVENTIES("70s Funk", "Retro"),
    EIGHTIES("80s Neon", "Retro"),
    VHS("VHS", "Retro"),

    // Creative
    POP_ART("Pop Art", "Creative"),
    CROSS_PROCESS("Cross Process", "Creative"),
    LOMO("Lomo", "Creative"),
    INFRARED("Infrared", "Creative"),
    CANDY("Candy", "Creative"),

    // Portrait
    PORCELAIN("Porcelain", "Portrait"),
    PEACH("Peach", "Portrait"),
    GLOW("Glow", "Portrait"),

    // Professional
    MAGAZINE("Magazine", "Pro"),
    WEDDING("Wedding", "Pro"),
    FOOD("Food", "Pro"),
    CLEAN_EDIT("Clean Edit", "Pro");

    val isLutBased: Boolean
        get() = this !in legacyFilters

    companion object {
        private val legacyFilters = setOf(
            NATURAL, WARM, COOL, NOIR, VIVID, DRAMATIC,
            SILVERTONE, CINEMATIC, VINTAGE, MUTED, FADE, MONO
        )

        private val keywordMap = mapOf(
            "EMERALD" to VIVID,
            "GREEN" to VIVID,
            "JADE" to COOL,
            "SAPPHIRE" to COOL,
            "AZURE" to COOL,
            "ICY" to ARCTIC,
            "FROST" to ARCTIC,
            "FROZEN" to ARCTIC,
            "OCEAN" to COOL,
            "AMBER" to WARM,
            "GOLDEN" to GOLDEN_HOUR,
            "SUNSET" to GOLDEN_HOUR,
            "SUNRISE" to GOLDEN_HOUR,
            "HONEY" to WARM,
            "SEPIA" to VINTAGE,
            "RETRO" to VINTAGE,
            "ANALOG" to VINTAGE,
            "NOSTALGIA" to VINTAGE,
            "CLASSIC" to VINTAGE,
            "MOODY" to DRAMATIC,
            "DARK" to DRAMATIC,
            "GRITTY" to DRAMATIC,
            "INTENSE" to DRAMATIC,
            "BOLD" to DRAMATIC,
            "STORMY" to DRAMATIC,
            "MOVIE" to CINEMATIC,
            "CINEMA" to CINEMATIC,
            "HOLLYWOOD" to TEAL_ORANGE,
            "TEAL" to TEAL_ORANGE,
            "BLOCKBUSTER" to TEAL_ORANGE,
            "BW" to NOIR,
            "MONOCHROME" to NOIR,
            "SHADOW" to NOIR,
            "PASTEL" to MUTED,
            "SOFT" to PORCELAIN,
            "DREAMY" to GLOW,
            "AIRY" to MISTY,
            "WASHED" to FADE,
            "FADED" to FADE,
            "BLEACH" to FADE,
            "SILVER" to SILVERTONE,
            "CHROME" to SILVERTONE,
            "STEEL" to SILVERTONE,
            "METALLIC" to SILVERTONE,
            "POP" to POP_ART,
            "PUNCH" to VIVID,
            "VIBRANT" to VIVID,
            "COLORFUL" to VIVID,
            "GREY" to MONO,
            "GRAY" to MONO,
            "KODAK" to KODAK_GOLD,
            "FUJI" to FUJI_VELVIA,
            "VELVIA" to FUJI_VELVIA,
            "PORTRA" to PORTRA,
            "EKTA" to EKTACHROME,
            "SUPERIA" to SUPERIA,
            "PROVIA" to PROVIA,
            "NEON" to NEON_NIGHT,
            "CYBER" to BLADE_RUNNER,
            "CYBERPUNK" to BLADE_RUNNER,
            "BLADE" to BLADE_RUNNER,
            "MOON" to MOONLIGHT,
            "NIGHT" to MOONLIGHT,
            "TWILIGHT" to BLUE_HOUR,
            "DUSK" to BLUE_HOUR,
            "DAWN" to GOLDEN_HOUR,
            "POLAROID" to POLAROID,
            "INSTANT" to POLAROID,
            "70S" to SEVENTIES,
            "SEVENTIES" to SEVENTIES,
            "FUNKY" to SEVENTIES,
            "DISCO" to SEVENTIES,
            "80S" to EIGHTIES,
            "EIGHTIES" to EIGHTIES,
            "SYNTHWAVE" to EIGHTIES,
            "VAPORWAVE" to EIGHTIES,
            "VHS" to VHS,
            "TAPE" to VHS,
            "CAMCORDER" to VHS,
            "LOMO" to LOMO,
            "LOMOGRAPHY" to LOMO,
            "CROSS" to CROSS_PROCESS,
            "XPRO" to CROSS_PROCESS,
            "INFRARED" to INFRARED,
            "THERMAL" to INFRARED,
            "CANDY" to CANDY,
            "SWEET" to CANDY,
            "BUBBLEGUM" to CANDY,
            "PORCELAIN" to PORCELAIN,
            "BEAUTY" to PORCELAIN,
            "SKIN" to PEACH,
            "PEACH" to PEACH,
            "GLOW" to GLOW,
            "RADIANT" to GLOW,
            "MAGAZINE" to MAGAZINE,
            "EDITORIAL" to MAGAZINE,
            "VOGUE" to MAGAZINE,
            "WEDDING" to WEDDING,
            "ROMANTIC" to WEDDING,
            "BRIDAL" to WEDDING,
            "FOOD" to FOOD,
            "APPETIZING" to FOOD,
            "DELICIOUS" to FOOD,
            "CLEAN" to CLEAN_EDIT,
            "MINIMAL" to CLEAN_EDIT,
            "FILM" to KODAK_GOLD,
            "MATRIX" to MATRIX,
            "MISTY" to MISTY,
            "FOG" to MISTY,
            "HAZE" to MISTY,
            "ARCTIC" to ARCTIC,
            "WINTER" to ARCTIC,
        )

        fun fromString(value: String): FilterType {
            val cleaned = value.trim().uppercase().replace("-", "_").replace(" ", "_")
            return entries.firstOrNull { it.name == cleaned }
                ?: entries.firstOrNull { cleaned.contains(it.name) }
                ?: keywordMap.entries.firstOrNull { cleaned.contains(it.key) }?.value
                ?: NATURAL
        }
    }
}
