package com.example.customkeyboard.data

/** The functional type of a keyboard key. */
enum class KeyType {
    CHARACTER, SHIFT, CAPS_LOCK, BACKSPACE, ENTER, SPACE, SYMBOLS, NUMBERS, LETTERS, EMOJI, VOICE, COMMA, PERIOD
}

/**
 * Represents a single key on the keyboard.
 * @param label main character shown / committed on tap
 * @param longPressLabels alternate characters shown in a long-press popup (accents, symbols)
 * @param widthWeight relative width vs. a standard key (1f = standard)
 */
data class KeyModel(
    val label: String,
    val type: KeyType = KeyType.CHARACTER,
    val longPressLabels: List<String> = emptyList(),
    val widthWeight: Float = 1f,
    val icon: Int? = null
)

/** A full row of keys. */
data class KeyRow(val keys: List<KeyModel>)

/** A full keyboard layout (a set of rows), e.g. QWERTY letters, numbers, or symbols page. */
data class KeyboardLayout(val rows: List<KeyRow>)
