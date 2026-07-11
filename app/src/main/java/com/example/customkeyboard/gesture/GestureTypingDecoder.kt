package com.example.customkeyboard.gesture

import com.example.customkeyboard.data.Dictionary
import kotlin.math.hypot
import kotlin.math.min

/** A single key's location, used to build the "ideal path" for gesture matching. */
data class KeyPoint(val char: Char, val cx: Float, val cy: Float)

/**
 * Decodes a swipe (gesture-typing) path into the most likely word, similar to Gboard/SwiftKey.
 *
 * Approach (lightweight & on-device, no ML model needed):
 *  1. Down-sample the raw touch path to a fixed number of points (keeps CPU cost constant
 *     regardless of swipe length/duration — important for battery).
 *  2. For every candidate word in the dictionary whose first & last letters roughly match the
 *     gesture's start/end keys, build the word's "ideal path" (the sequence of key centers for
 *     each letter) and also down-sample it to the same number of points.
 *  3. Score candidates by the mean Euclidean distance between corresponding down-sampled points
 *     (a simplified version of the "shape channel" in production gesture typing engines) plus a
 *     small bonus for word frequency.
 *  5. Return the best-scoring candidates as suggestions.
 *
 * This runs entirely on-device with no network calls.
 */
class GestureTypingDecoder(private val dictionary: Dictionary) {

    private val samplePoints = 24

    /**
     * @param rawPath the raw sequence of touch points captured during the swipe
     * @param keyLayout map of character -> its on-screen center, for the currently visible layout
     */
    fun decode(rawPath: List<KeyPoint>, keyLayout: Map<Char, KeyPoint>, maxResults: Int = 3): List<String> {
        if (rawPath.size < 2 || keyLayout.isEmpty()) return emptyList()

        val userPath = resample(rawPath.map { it.cx to it.cy }, samplePoints)
        val startChar = rawPath.first().char
        val endChar = rawPath.last().char

        val candidates = dictionary.wordList().filter { word ->
            word.length in 2..24 &&
                word.first().equals(startChar, ignoreCase = true)
        }

        val scored = candidates.mapNotNull { word ->
            val idealPoints = mutableListOf<Pair<Float, Float>>()
            for (c in word) {
                val kp = keyLayout[c.lowercaseChar()] ?: return@mapNotNull null
                idealPoints.add(kp.cx to kp.cy)
            }
            if (idealPoints.size < 2) return@mapNotNull null
            val idealResampled = resample(idealPoints, samplePoints)
            val shapeDistance = meanDistance(userPath, idealResampled)
            // Slight preference for words whose last letter matches the gesture's ending key.
            val endBonus = if (word.last().equals(endChar, ignoreCase = true)) -8f else 0f
            val freqBonus = -(dictionary.getSuggestions(word.take(1), 50).indexOf(word)).let {
                if (it < 0) 0f else it * 0.1f
            }
            word to (shapeDistance + endBonus + freqBonus)
        }

        return scored.sortedBy { it.second }.take(maxResults).map { it.first }
    }

    /** Resamples a path to exactly [count] evenly-spaced points along its length. */
    private fun resample(points: List<Pair<Float, Float>>, count: Int): List<Pair<Float, Float>> {
        if (points.size == 1) return List(count) { points[0] }
        val distances = FloatArray(points.size)
        var total = 0f
        for (i in 1 until points.size) {
            val d = hypot((points[i].first - points[i - 1].first), (points[i].second - points[i - 1].second))
            total += d
            distances[i] = total
        }
        if (total == 0f) return List(count) { points[0] }

        val step = total / (count - 1)
        val result = mutableListOf<Pair<Float, Float>>()
        var segmentIndex = 0
        for (i in 0 until count) {
            val targetDist = step * i
            while (segmentIndex < distances.size - 2 && distances[segmentIndex + 1] < targetDist) segmentIndex++
            val segStart = distances[segmentIndex]
            val segEnd = distances[min(segmentIndex + 1, distances.size - 1)]
            val segLen = (segEnd - segStart).takeIf { it > 0f } ?: 1f
            val t = ((targetDist - segStart) / segLen).coerceIn(0f, 1f)
            val p0 = points[segmentIndex]
            val p1 = points[min(segmentIndex + 1, points.size - 1)]
            result.add(p0.first + (p1.first - p0.first) * t to p0.second + (p1.second - p0.second) * t)
        }
        return result
    }

    private fun meanDistance(a: List<Pair<Float, Float>>, b: List<Pair<Float, Float>>): Float {
        var sum = 0f
        val n = min(a.size, b.size)
        for (i in 0 until n) {
            sum += hypot(a[i].first - b[i].first, a[i].second - b[i].second)
        }
        return sum / n
    }
}
