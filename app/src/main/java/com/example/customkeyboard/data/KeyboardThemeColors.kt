package com.example.customkeyboard.data

/**
 * A complete color palette for rendering the keyboard. Colors are plain ARGB ints so they can be
 * applied directly to [android.graphics.Paint] in [com.example.customkeyboard.ui.KeyboardView]
 * without any resource lookups — this is what makes runtime theme switching (no APK resources
 * involved) possible.
 */
data class KeyboardThemeColors(
    val id: String,
    val displayName: String,
    val background: Int,
    val keyBackground: Int,
    val keyPressedBackground: Int,
    val specialKeyBackground: Int,
    val keyText: Int,
    val keyHint: Int,
    val accent: Int,
    val popupBackground: Int,
    val divider: Int,
    val toolbarIcon: Int,
    /** Swatch color shown in the Settings theme picker (doesn't have to equal [background]). */
    val swatch: Int
)

/** Marks a theme that should render a picture behind translucent keys instead of a flat color. */
const val IMAGE_THEME_ID = "custom_image"

object KeyboardThemePresets {

    val MIDNIGHT_TEAL = KeyboardThemeColors(
        id = "midnight_teal",
        displayName = "Midnight Teal",
        background = 0xFF000000.toInt(),
        keyBackground = 0xFF2C2C2E.toInt(),
        keyPressedBackground = 0xFF48484A.toInt(),
        specialKeyBackground = 0xFF1C1C1E.toInt(),
        keyText = 0xFFF2F2F7.toInt(),
        keyHint = 0xFF9A9AA0.toInt(),
        accent = 0xFF5CD9C4.toInt(),
        popupBackground = 0xFF3A3A3C.toInt(),
        divider = 0xFF2C2C2E.toInt(),
        toolbarIcon = 0xFFB8B8BD.toInt(),
        swatch = 0xFF5CD9C4.toInt()
    )

    val OCEAN_BLUE = KeyboardThemeColors(
        id = "ocean_blue",
        displayName = "Ocean Blue",
        background = 0xFF0B1220.toInt(),
        keyBackground = 0xFF16233A.toInt(),
        keyPressedBackground = 0xFF223353.toInt(),
        specialKeyBackground = 0xFF0B1526.toInt(),
        keyText = 0xFFE8F0FE.toInt(),
        keyHint = 0xFF7C93B8.toInt(),
        accent = 0xFF4C9CFF.toInt(),
        popupBackground = 0xFF223353.toInt(),
        divider = 0xFF16233A.toInt(),
        toolbarIcon = 0xFF9AB2D9.toInt(),
        swatch = 0xFF4C9CFF.toInt()
    )

    val SUNSET_ORANGE = KeyboardThemeColors(
        id = "sunset_orange",
        displayName = "Sunset Orange",
        background = 0xFF1A0F0A.toInt(),
        keyBackground = 0xFF2E1B12.toInt(),
        keyPressedBackground = 0xFF4A2C1D.toInt(),
        specialKeyBackground = 0xFF120A06.toInt(),
        keyText = 0xFFFCEEE3.toInt(),
        keyHint = 0xFFC08A6C.toInt(),
        accent = 0xFFFF8A50.toInt(),
        popupBackground = 0xFF3A2417.toInt(),
        divider = 0xFF2E1B12.toInt(),
        toolbarIcon = 0xFFD9AF8F.toInt(),
        swatch = 0xFFFF8A50.toInt()
    )

    val FOREST_GREEN = KeyboardThemeColors(
        id = "forest_green",
        displayName = "Forest Green",
        background = 0xFF07140F.toInt(),
        keyBackground = 0xFF12271D.toInt(),
        keyPressedBackground = 0xFF1E3A2B.toInt(),
        specialKeyBackground = 0xFF06110C.toInt(),
        keyText = 0xFFE6F5EC.toInt(),
        keyHint = 0xFF7FAF93.toInt(),
        accent = 0xFF4CD37E.toInt(),
        popupBackground = 0xFF1E3A2B.toInt(),
        divider = 0xFF12271D.toInt(),
        toolbarIcon = 0xFF9CC7AC.toInt(),
        swatch = 0xFF4CD37E.toInt()
    )

    val ROSE_PINK = KeyboardThemeColors(
        id = "rose_pink",
        displayName = "Rose Pink",
        background = 0xFF1A0E14.toInt(),
        keyBackground = 0xFF2E1826.toInt(),
        keyPressedBackground = 0xFF472439.toInt(),
        specialKeyBackground = 0xFF120A10.toInt(),
        keyText = 0xFFFBEAF2.toInt(),
        keyHint = 0xFFCB90AC.toInt(),
        accent = 0xFFFF6FA5.toInt(),
        popupBackground = 0xFF3A1F30.toInt(),
        divider = 0xFF2E1826.toInt(),
        toolbarIcon = 0xFFDBA0BE.toInt(),
        swatch = 0xFFFF6FA5.toInt()
    )

    val CLASSIC_LIGHT = KeyboardThemeColors(
        id = "classic_light",
        displayName = "Classic Light",
        background = 0xFFF5F5F7.toInt(),
        keyBackground = 0xFFFFFFFF.toInt(),
        keyPressedBackground = 0xFFE0E0E5.toInt(),
        specialKeyBackground = 0xFFDEDEE3.toInt(),
        keyText = 0xFF1C1C1E.toInt(),
        keyHint = 0xFF9A9A9E.toInt(),
        accent = 0xFF2FA893.toInt(),
        popupBackground = 0xFFFFFFFF.toInt(),
        divider = 0xFFE5E5EA.toInt(),
        toolbarIcon = 0xFF6E6E73.toInt(),
        swatch = 0xFF2FA893.toInt()
    )

    /** Overlay palette used when a custom background image is active: translucent keys let the
     *  picture show through while staying legible, regardless of the picture's own colors. */
    val IMAGE_OVERLAY = KeyboardThemeColors(
        id = IMAGE_THEME_ID,
        displayName = "Custom Image",
        background = 0xFF000000.toInt(),
        keyBackground = 0x401C1C1E,
        keyPressedBackground = 0x801C1C1E.toInt(),
        specialKeyBackground = 0x59000000,
        keyText = 0xFFFFFFFF.toInt(),
        keyHint = 0xE6FFFFFF.toInt(),
        accent = 0xFF5CD9C4.toInt(),
        popupBackground = 0xF0222222.toInt(),
        divider = 0x40FFFFFF,
        toolbarIcon = 0xFFFFFFFF.toInt(),
        swatch = 0xFF8A8A8E.toInt()
    )

    /** The six selectable flat-color presets (image theme is offered separately in the UI). */
    val ALL: List<KeyboardThemeColors> = listOf(
        MIDNIGHT_TEAL, OCEAN_BLUE, SUNSET_ORANGE, FOREST_GREEN, ROSE_PINK, CLASSIC_LIGHT
    )

    fun byId(id: String): KeyboardThemeColors = ALL.find { it.id == id } ?: MIDNIGHT_TEAL
}
