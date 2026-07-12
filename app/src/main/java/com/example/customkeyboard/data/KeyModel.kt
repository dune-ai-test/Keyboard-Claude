package com.example.customkeyboard.data

/** The functional type of a keyboard key. */
enum class KeyType {
    CHARACTER, SHIFT, CAPS_LOCK, BACKSPACE, ENTER, SPACE, SYMBOLS, NUMBERS, LETTERS, EMOJI, VOICE, COMMA, PERIOD
}

/**
 * Represents a single key on the keyboard.
 * @param label main character shown / committed on tap
 * @param longPressLabels alternate characters shown in a long-press popup (accents, symbols).
 *   The first entry, if present, is also used as the small corner [hintLabel] when one isn't
 *   explicitly provided.
 * @param widthWeight relative width vs. a standard key (1f = standard)
 * @param hintLabel a small secondary character drawn in the key's top-right corner (purely a
 *   visual hint of what long-pressing will reveal — matches the "Q%, W\\, E!, ..." style hints
 *   seen on stock Android/Gboard-style keyboards). Defaults to the first long-press label.
 * @param isPrimary marks the visually-distinct primary action key (e.g. Enter), rendered with
 *   the accent/teal fill instead of the standard dark key background.
 */
data class KeyModel(
    val label: String,
    val type: KeyType = KeyType.CHARACTER,
    val longPressLabels: List<String> = emptyList(),
    val widthWeight: Float = 1f,
    val icon: Int? = null,
    val hintLabel: String? = longPressLabels.firstOrNull(),
    val isPrimary: Boolean = false
)

/** A full row of keys. */
data class KeyRow(val keys: List<KeyModel>)

/** A full keyboard layout (a set of rows), e.g. QWERTY letters, numbers, or symbols page. */
data class KeyboardLayout(val rows: List<KeyRow>)
