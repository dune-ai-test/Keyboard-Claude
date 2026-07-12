package com.example.customkeyboard.data

/**
 * Static definitions of the three keyboard "pages": letters (QWERTY), numbers, and symbols.
 *
 * Letter keys show a small top-right corner "hint" character (e.g. Q^%, W^\, A^@ ...) — a purely
 * visual preview of what long-pressing that key reveals, matching stock Android/Gboard-style
 * keyboards. Long-pressing shows that hint symbol first, then any accented variants.
 */
object KeyboardLayouts {

    private val accentMap = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "į"),
        "o" to listOf("ò", "ó", "ô", "ö", "õ", "ø", "œ"),
        "u" to listOf("ù", "ú", "û", "ü", "ū"),
        "s" to listOf("ß", "ś", "š"),
        "c" to listOf("ç", "ć", "č"),
        "n" to listOf("ñ", "ń"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
        "l" to listOf("ł"),
        "g" to listOf("ĝ"),
        "d" to listOf("ð", "đ")
    )

    /** The small corner-hint symbol shown on each letter key (matches the reference design). */
    private val hintMap = mapOf(
        "q" to "%", "w" to "\\", "e" to "!", "r" to "=", "t" to "[",
        "y" to "]", "u" to "<", "i" to ">", "o" to "{", "p" to "}",
        "a" to "@", "s" to "#", "d" to "$", "f" to "-", "g" to "&",
        "h" to "-", "j" to "+", "k" to "(", "l" to ")",
        "z" to "*", "x" to "\"", "c" to "'", "v" to ":", "b" to ";", "n" to "!", "m" to "?"
    )

    /** Builds a character key: corner hint symbol first in the long-press popup, then accents. */
    private fun charKey(c: String): KeyModel {
        val hint = hintMap[c]
        val accents = accentMap[c] ?: emptyList()
        val longPress = listOfNotNull(hint) + accents
        return KeyModel(label = c, type = KeyType.CHARACTER, longPressLabels = longPress, hintLabel = hint)
    }

    fun letters(): KeyboardLayout {
        val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
            KeyModel(it, KeyType.CHARACTER)
        }
        val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { charKey(it) }
        val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { charKey(it) }
        val row3 = mutableListOf<KeyModel>()
        row3.add(KeyModel("⬆", KeyType.SHIFT, widthWeight = 1.5f))
        row3.addAll(listOf("z", "x", "c", "v", "b", "n", "m").map { charKey(it) })
        row3.add(KeyModel("⌫", KeyType.BACKSPACE, widthWeight = 1.5f))

        val row4 = listOf(
            KeyModel("?123", KeyType.NUMBERS, widthWeight = 1.5f),
            KeyModel(",", KeyType.COMMA, widthWeight = 1f, longPressLabels = listOf(";", ":")),
            KeyModel("😀", KeyType.EMOJI, widthWeight = 1f),
            KeyModel("space", KeyType.SPACE, widthWeight = 4f),
            KeyModel(".", KeyType.PERIOD, widthWeight = 1f, longPressLabels = listOf("!", "?")),
            KeyModel("⏎", KeyType.ENTER, widthWeight = 1.5f, isPrimary = true)
        )
        return KeyboardLayout(listOf(KeyRow(numberRow), KeyRow(row1), KeyRow(row2), KeyRow(row3), KeyRow(row4)))
    }

    /**
     * Numbers page: a narrow left column of arithmetic operators (+ - * /) running alongside a
     * 3x3 number grid, matching the reference "?123" layout.
     */
    fun numbers(): KeyboardLayout {
        val row1 = listOf(
            KeyModel("+", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("1", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("2", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("3", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("%", KeyType.CHARACTER, widthWeight = 1f)
        )
        val row2 = listOf(
            KeyModel("-", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("4", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("5", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("6", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("_", KeyType.CHARACTER, widthWeight = 1f)
        )
        val row3 = listOf(
            KeyModel("*", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("7", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("8", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("9", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("⌫", KeyType.BACKSPACE, widthWeight = 1f)
        )
        val row4 = listOf(
            KeyModel("/", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("ABC", KeyType.LETTERS, widthWeight = 1f),
            KeyModel(",", KeyType.COMMA, widthWeight = 1f),
            KeyModel("!?#", KeyType.SYMBOLS, widthWeight = 1f),
            KeyModel("0", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel("=", KeyType.CHARACTER, widthWeight = 1f),
            KeyModel(".", KeyType.PERIOD, widthWeight = 1f),
            KeyModel("⏎", KeyType.ENTER, widthWeight = 1.5f, isPrimary = true)
        )
        return KeyboardLayout(listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), KeyRow(row4)))
    }

    /** Symbols page: extended punctuation & math symbols, matching the reference "!?#" layout. */
    fun symbols(): KeyboardLayout {
        val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
            KeyModel(it, KeyType.CHARACTER)
        }
        val row2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map {
            KeyModel(it, KeyType.CHARACTER)
        }
        val row3 = mutableListOf<KeyModel>()
        row3.add(KeyModel("=\\<", KeyType.NUMBERS, widthWeight = 1.5f))
        row3.addAll(listOf("*", "\"", "'", ":", ";", "!", "?").map { KeyModel(it, KeyType.CHARACTER) })
        row3.add(KeyModel("⌫", KeyType.BACKSPACE, widthWeight = 1.5f))

        val row4 = listOf(
            KeyModel("ABC", KeyType.LETTERS, widthWeight = 1.5f),
            KeyModel(",", KeyType.COMMA, widthWeight = 1f),
            KeyModel("1234", KeyType.NUMBERS, widthWeight = 1.2f),
            KeyModel("space", KeyType.SPACE, widthWeight = 3f),
            KeyModel(".", KeyType.PERIOD, widthWeight = 1f),
            KeyModel("⏎", KeyType.ENTER, widthWeight = 1.5f, isPrimary = true)
        )
        return KeyboardLayout(listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), KeyRow(row4)))
    }
}
