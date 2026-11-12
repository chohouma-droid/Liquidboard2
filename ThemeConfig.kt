package com.liquidboard.ime.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color

enum class ThemePreset {
    LIQUID_GLASS,
    MATERIAL_YOU,
    AMOLED_OBSIDIAN,
    DAYLIGHT_PAPER
}

enum class OneHandedMode { OFF, LEFT, RIGHT }

data class ThemeConfig(
    val preset: ThemePreset = ThemePreset.LIQUID_GLASS,
    val glassOpacity: Float = 0.34f,
    val blurRadiusDp: Float = 20f,
    val specularRimIntensity: Float = 0.68f,
    val keyCornerRadiusDp: Float = 11f,
    val showKeyBorders: Boolean = true,
    val showNumberRow: Boolean = true,
    val glideTypingEnabled: Boolean = true,
    val spacebarCursorScrub: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val floatingMode: Boolean = false,
    val primaryTint: Color = Color(0xFF3B82F6),
    val secondaryTint: Color = Color(0xFFF59E0B),
    val defaultTranslateTarget: String = "Spanish"
) {
    companion object {
        /**
         * Samples Android system wallpaper colors on API 27+ to adapt
         * Liquid Glass refraction tints dynamically to the user's home screen.
         */
        fun fromWallpaper(context: Context, base: ThemeConfig = ThemeConfig()): ThemeConfig {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return base
            val wm = WallpaperManager.getInstance(context)
            val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return base
            val primary = Color(colors.primaryColor.toArgb())
            val secondary = colors.secondaryColor?.let { Color(it.toArgb()) } ?: base.secondaryTint
            return base.copy(primaryTint = primary, secondaryTint = secondary)
        }
    }
}
