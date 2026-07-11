package com.example.customkeyboard.data

/**
 * Static definitions of the three keyboard "pages": letters (QWERTY), numbers, and symbols.
 * Long-press labels provide accented / alternate characters, matching common Gboard-style behavior.
 */
object KeyboardLayouts {

    private val longPressMap = mapOf(
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

    /** Builds a character key, attaching long-press accents when defined. */
    private fun charKey(c: String): KeyModel =
        KeyModel(label = c, type = KeyType.CHARACTER, longPressLabels = longPressMap[c] ?: emptyList())

    fun letters(): KeyboardLayout {
        val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").map { charKey(it) }
        val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { charKey(it) }
        val row3 = mutableListOf<KeyModel>()
        row3.add(KeyModel("⇧", KeyType.SHIFT, widthWeight = 1.5f))
        row3.addAll(listOf("z", "x", "c", "v", "b", "n", "m").map { charKey(it) })
        row3.add(KeyModel("⌫", KeyType.BACKSPACE, widthWeight = 1.5f))

        val row4 = listOf(
            KeyModel("?123", KeyType.NUMBERS, widthWeight = 1.5f),
            KeyModel("😀", KeyType.EMOJI, widthWeight = 1f),
            KeyModel("🎤", KeyType.VOICE, widthWeight = 1f),
            KeyModel("space", KeyType.SPACE, widthWeight = 4f),
            KeyModel(",", KeyType.COMMA, widthWeight = 1f, longPressLabels = listOf(";", ":")),
            KeyModel(".", KeyType.PERIOD, widthWeight = 1f, longPressLabels = listOf("!", "?")),
            KeyModel("⏎", KeyType.ENTER, widthWeight = 1.5f)
        )
        return KeyboardLayout(listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), KeyRow(row4)))
    }

    fun numbers(): KeyboardLayout {
        val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
            KeyModel(it, KeyType.CHARACTER)
        }
        val row2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map {
            KeyModel(it, KeyType.CHARACTER)
        }
        val row3 = mutableListOf<KeyModel>()
        row3.add(KeyModel("=\\<", KeyType.SYMBOLS, widthWeight = 1.5f))
        row3.addAll(listOf("*", "\"", "'", ":", ";", "!", "?").map { KeyModel(it, KeyType.CHARACTER) })
        row3.add(KeyModel("⌫", KeyType.BACKSPACE, widthWeight = 1.5f))

        val row4 = listOf(
            KeyModel("ABC", KeyType.LETTERS, widthWeight = 1.5f),
            KeyModel("🎤", KeyType.VOICE, widthWeight = 1f),
            KeyModel("space", KeyType.SPACE, widthWeight = 4f),
            KeyModel(",", KeyType.COMMA, widthWeight = 1f),
            KeyModel(".", KeyType.PERIOD, widthWeight = 1f),
            KeyModel("⏎", KeyType.ENTER, widthWeight = 1.5f)
        )
        return KeyboardLayout(listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), KeyRow(row4)))
    }

    fun symbols(): KeyboardLayout {
        val row1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map {
            KeyModel(it, KeyType.CHARACTER)
        }
        val row2 = listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\").map {
            KeyModel(it, KeyType.CHARACTER)
        }
        val row3 = mutableListOf<KeyModel>()
        row3.add(KeyModel("?123", KeyType.NUMBERS, widthWeight = 1.5f))
        row3.addAll(listOf("%", "©", "®", "™", "✓", "[", "]").map { KeyModel(it, KeyType.CHARACTER) })
        row3.add(KeyModel("⌫", KeyType.BACKSPACE, widthWeight = 1.5f))

        val row4 = listOf(
            KeyModel("ABC", KeyType.LETTERS, widthWeight = 1.5f),
            KeyModel("🎤", KeyType.VOICE, widthWeight = 1f),
            KeyModel("space", KeyType.SPACE, widthWeight = 4f),
            KeyModel(",", KeyType.COMMA, widthWeight = 1f),
            KeyModel(".", KeyType.PERIOD, widthWeight = 1f),
            KeyModel("⏎", KeyType.ENTER, widthWeight = 1.5f)
        )
        return KeyboardLayout(listOf(KeyRow(row1), KeyRow(row2), KeyRow(row3), KeyRow(row4)))
    }
}
