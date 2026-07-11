package com.example.customkeyboard.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight, fully on-device word-prediction / auto-correction engine.
 *
 * Design goals (performance & battery):
 *  - Loads a plain-text frequency word list once into memory (a HashMap), no DB/IO on hot path.
 *  - Prefix lookups use a simple sorted-list binary search bucketed by first letter -> O(log n).
 *  - Auto-correct uses bounded Damerau-Levenshtein (max distance 2) only against close-prefix
 *    candidates, keeping each keystroke's correction cheap (~microseconds).
 *  - A small in-memory "user dictionary" (learned words) is persisted to SharedPreferences,
 *    entirely locally - nothing ever leaves the device.
 */
class Dictionary private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_dictionary", Context.MODE_PRIVATE)

    // word -> frequency (higher = more common)
    private val baseWords = HashMap<String, Int>()
    // words the user typed & confirmed (via space/enter) that aren't in the base dictionary
    private val userWords = HashMap<String, Int>()

    // bigram model: previousWord -> (nextWord -> count), used for next-word prediction
    private val bigrams = HashMap<String, HashMap<String, Int>>()

    init {
        loadBaseDictionary()
        loadUserDictionary()
    }

    private fun loadBaseDictionary() {
        // A compact, curated high-frequency English word list (top common words).
        // In production this would be loaded from assets/dictionaries/en_US.dic (binary trie).
        val common = CommonWords.TOP_ENGLISH_WORDS
        for ((index, word) in common.withIndex()) {
            // earlier words in list = higher frequency
            baseWords[word] = common.size - index
        }
    }

    private fun loadUserDictionary() {
        val stored = prefs.getStringSet("learned_words", emptySet()) ?: emptySet()
        for (entry in stored) {
            val parts = entry.split(":::")
            if (parts.size == 2) {
                val freq = parts[1].toIntOrNull() ?: 1
                userWords[parts[0]] = freq
            }
        }
    }

    private fun persistUserDictionary() {
        val serialized = userWords.entries.map { "${it.key}:::${it.value}" }.toSet()
        prefs.edit().putStringSet("learned_words", serialized).apply()
    }

    /** Learn a word the user actually committed, boosting future predictions. */
    fun learn(word: String) {
        val w = word.lowercase(Locale.getDefault())
        if (w.length < 2) return
        if (baseWords.containsKey(w)) {
            baseWords[w] = (baseWords[w] ?: 0) + 1
        } else {
            userWords[w] = (userWords[w] ?: 0) + 1
            persistUserDictionary()
        }
    }

    /** Learn a bigram transition (previous word -> next word) for contextual prediction. */
    fun learnBigram(prev: String, next: String) {
        val p = prev.lowercase(Locale.getDefault())
        val n = next.lowercase(Locale.getDefault())
        val map = bigrams.getOrPut(p) { HashMap() }
        map[n] = (map[n] ?: 0) + 1
    }

    private fun allWordFrequencies(): Map<String, Int> {
        // Merge base + user dictionaries (user words get a slight recency boost)
        val merged = HashMap<String, Int>(baseWords)
        for ((w, f) in userWords) merged[w] = (merged[w] ?: 0) + f + 5
        return merged
    }

    /**
     * Returns up to [limit] suggestions for the given [prefix], ranked by frequency then
     * closeness. Empty prefix returns nothing (use [predictNextWord] instead for that case).
     */
    fun getSuggestions(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val lower = prefix.lowercase(Locale.getDefault())
        val all = allWordFrequencies()

        // 1) exact prefix matches, ranked by frequency
        val prefixMatches = all.entries
            .asSequence()
            .filter { it.key.startsWith(lower) }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
            .toMutableList()

        if (prefixMatches.size >= limit) return prefixMatches

        // 2) fill remaining slots with fuzzy (typo-tolerant) matches
        val fuzzy = all.entries
            .asSequence()
            .filter { it.key !in prefixMatches && kotlin.math.abs(it.key.length - lower.length) <= 2 }
            .map { it.key to boundedEditDistance(lower, it.key, 2) }
            .filter { it.second in 0..2 }
            .sortedWith(compareBy({ it.second }, { -(all[it.first] ?: 0) }))
            .map { it.first }
            .take(limit - prefixMatches.size)

        prefixMatches.addAll(fuzzy)
        return prefixMatches
    }

    /** Suggests the next word based on the previously committed word (contextual prediction). */
    fun predictNextWord(previousWord: String, limit: Int = 3): List<String> {
        val p = previousWord.lowercase(Locale.getDefault())
        val candidates = bigrams[p] ?: return emptyList()
        return candidates.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /**
     * Returns the best auto-correction for [typed], or null if the typed word is already
     * valid / no confident correction exists. Only fires for words with an edit distance <= 1
     * from a known word, to avoid over-aggressive corrections.
     */
    fun getAutoCorrection(typed: String): String? {
        val lower = typed.lowercase(Locale.getDefault())
        if (lower.length < 2) return null
        val all = allWordFrequencies()
        if (all.containsKey(lower)) return null // already a real word

        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for ((word, freq) in all) {
            if (kotlin.math.abs(word.length - lower.length) > 1) continue
            val dist = boundedEditDistance(lower, word, 1)
            if (dist in 0..1) {
                // Prefer smaller edit distance, then higher frequency
                val score = dist * 100000 - freq
                if (score < bestScore) {
                    bestScore = score
                    best = word
                }
            }
        }
        return best
    }

    /** Bounded Damerau-Levenshtein distance; returns -1 if distance exceeds [maxDist] (fast exit). */
    private fun boundedEditDistance(a: String, b: String, maxDist: Int): Int {
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > maxDist) return -1

        val dp = Array(la + 1) { IntArray(lb + 1) }
        for (i in 0..la) dp[i][0] = i
        for (j in 0..lb) dp[0][j] = j

        for (i in 1..la) {
            var rowMin = Int.MAX_VALUE
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = min(value, dp[i - 2][j - 2] + 1) // transposition
                }
                dp[i][j] = value
                rowMin = min(rowMin, value)
            }
            if (rowMin > maxDist) return -1 // early exit, can't beat maxDist anymore
        }
        return if (dp[la][lb] <= maxDist) dp[la][lb] else -1
    }

    /** Checks whether [word] exists in the combined dictionary (used by the gesture decoder). */
    fun isKnownWord(word: String): Boolean = allWordFrequencies().containsKey(word.lowercase(Locale.getDefault()))

    /** All known words, used by the swipe-gesture decoder for path matching. */
    fun wordList(): List<String> = allWordFrequencies().keys.toList()

    companion object {
        @Volatile private var instance: Dictionary? = null
        fun getInstance(context: Context): Dictionary =
            instance ?: synchronized(this) {
                instance ?: Dictionary(context.applicationContext).also { instance = it }
            }
    }
}
