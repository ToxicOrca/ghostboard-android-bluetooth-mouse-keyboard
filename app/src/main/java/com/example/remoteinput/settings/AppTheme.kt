package com.example.remoteinput.settings

import android.graphics.Color

data class AppTheme(
    val name: String,
    val background: Int,
    val surface: Int,
    val trackpadBg: Int,
    val trackpadBorder: Int,
    val keyBg: Int,
    val keyPressed: Int,
    val keyText: Int,
    val accent: Int,
    val accentDim: Int,
    val textSecondary: Int
) {
    companion object {
        val MIDNIGHT = AppTheme(
            name = "Midnight",
            background = Color.parseColor("#1A1A2E"),
            surface = Color.parseColor("#16213E"),
            trackpadBg = Color.parseColor("#0F3460"),
            trackpadBorder = Color.parseColor("#533483"),
            keyBg = Color.parseColor("#2A2A4A"),
            keyPressed = Color.parseColor("#533483"),
            keyText = Color.parseColor("#E0E0E0"),
            accent = Color.parseColor("#7B68EE"),
            accentDim = Color.parseColor("#5A4FCF"),
            textSecondary = Color.parseColor("#9E9E9E")
        )

        val OCEAN = AppTheme(
            name = "Ocean",
            background = Color.parseColor("#0D1B2A"),
            surface = Color.parseColor("#1B2838"),
            trackpadBg = Color.parseColor("#1B3A4B"),
            trackpadBorder = Color.parseColor("#006D77"),
            keyBg = Color.parseColor("#1F3044"),
            keyPressed = Color.parseColor("#006D77"),
            keyText = Color.parseColor("#E0E0E0"),
            accent = Color.parseColor("#00B4D8"),
            accentDim = Color.parseColor("#0096B7"),
            textSecondary = Color.parseColor("#8EACBB")
        )

        val FOREST = AppTheme(
            name = "Forest",
            background = Color.parseColor("#1A1F16"),
            surface = Color.parseColor("#2D3328"),
            trackpadBg = Color.parseColor("#2E4032"),
            trackpadBorder = Color.parseColor("#4A7C59"),
            keyBg = Color.parseColor("#3A4A3A"),
            keyPressed = Color.parseColor("#4A7C59"),
            keyText = Color.parseColor("#D8E8D0"),
            accent = Color.parseColor("#6BCB77"),
            accentDim = Color.parseColor("#4AA95A"),
            textSecondary = Color.parseColor("#8FA888")
        )

        val CRIMSON = AppTheme(
            name = "Crimson",
            background = Color.parseColor("#1A1012"),
            surface = Color.parseColor("#2D1B21"),
            trackpadBg = Color.parseColor("#3D1F28"),
            trackpadBorder = Color.parseColor("#8B2252"),
            keyBg = Color.parseColor("#3A2A30"),
            keyPressed = Color.parseColor("#8B2252"),
            keyText = Color.parseColor("#E8D0D8"),
            accent = Color.parseColor("#E63946"),
            accentDim = Color.parseColor("#C5303D"),
            textSecondary = Color.parseColor("#B08890")
        )

        val SLATE = AppTheme(
            name = "Slate",
            background = Color.parseColor("#1E1E1E"),
            surface = Color.parseColor("#2D2D2D"),
            trackpadBg = Color.parseColor("#333333"),
            trackpadBorder = Color.parseColor("#555555"),
            keyBg = Color.parseColor("#3C3C3C"),
            keyPressed = Color.parseColor("#555555"),
            keyText = Color.parseColor("#D4D4D4"),
            accent = Color.parseColor("#CCCCCC"),
            accentDim = Color.parseColor("#AAAAAA"),
            textSecondary = Color.parseColor("#808080")
        )

        val AMOLED = AppTheme(
            name = "AMOLED",
            background = Color.parseColor("#000000"),
            surface = Color.parseColor("#0A0A0A"),
            trackpadBg = Color.parseColor("#111111"),
            trackpadBorder = Color.parseColor("#7B68EE"),
            keyBg = Color.parseColor("#1A1A1A"),
            keyPressed = Color.parseColor("#533483"),
            keyText = Color.parseColor("#FFFFFF"),
            accent = Color.parseColor("#7B68EE"),
            accentDim = Color.parseColor("#5A4FCF"),
            textSecondary = Color.parseColor("#666666")
        )

        val ALL_THEMES = listOf(MIDNIGHT, OCEAN, FOREST, CRIMSON, SLATE, AMOLED)

        fun fromName(name: String): AppTheme {
            return ALL_THEMES.firstOrNull { it.name == name } ?: MIDNIGHT
        }
    }
}
