package com.liquidboard.ime.gesture

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot

data class GestureCandidate(val word: String, val distance: Float)

class GestureDecoder(
    private val baseDictionary: List<String> = DEFAULT_WORDS
) {
    private val wordFreq = mutableMapOf<String, Int>()
    private val bigrams = mutableMapOf<String, MutableMap<String, Int>>()
    private val keyOffsets = mutableMapOf<Char, Offset>()

    fun learnWord(word: String, prevWord: String? = null) {
        val clean = word.lowercase().filter { it.isLetter() }
        if (clean.length < 2) return
        wordFreq[clean] = (wordFreq[clean] ?: 0) + 1
        val prev = prevWord?.lowercase()?.filter { it.isLetter() }
        if (!prev.isNullOrEmpty()) {
            val map = bigrams.getOrPut(prev) { mutableMapOf() }
            map[clean] = (map[clean] ?: 0) + 1
        }
    }

    fun decode(points: List<Offset>, prevWord: String? = null, maxResults: Int = 5): List<GestureCandidate> {
        if (points.size < 2) return emptyList()
        val userSamples = resample(points, 24)
        val start = points.first()
        val end = points.last()
        val vocab = (wordFreq.keys + baseDictionary).distinct()

        return vocab.mapNotNull { word ->
            val ideal = idealPointsFor(word)
            if (ideal.isEmpty()) return@mapNotNull null
            val startDist = dist(start, ideal.first())
            val endDist = dist(end, ideal.last())
            if (startDist > 0.22f || endDist > 0.22f) return@mapNotNull null

            val idealSamples = resample(ideal, 24)
            val dtw = windowedDtw(userSamples, idealSamples, 3)
            val freqBoost = kotlin.math.min(0.035f, kotlin.math.ln(1f + (wordFreq[word] ?: 0)) * 0.012f)
            val bigramBoost = prevWord?.let { p ->
                kotlin.math.min(0.05f, kotlin.math.ln(1f + (bigrams[p.lowercase()]?.get(word) ?: 0)) * 0.025f)
            } ?: 0f
            val score = (dtw * 0.85f + startDist * 0.65f + endDist * 0.65f - freqBoost - bigramBoost).coerceAtLeast(0.001f)
            if (score < 0.36f) GestureCandidate(word, score) else null
        }.sortedBy { it.distance }.take(maxResults)
    }

    private fun windowedDtw(a: List<Offset>, b: List<Offset>, window: Int): Float {
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { FloatArray(m + 1) { 1e6f } }
        dp[0][0] = 0f
        for (i in 1..n) {
            for (j in maxOf(1, i - window)..minOf(m, i + window)) {
                dp[i][j] = dist(a[i - 1], b[j - 1]) + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[n][m] / maxOf(n, m)
    }

    private fun idealPointsFor(word: String): List<Offset> {
        val out = mutableListOf<Offset>()
        for (ch in word.lowercase()) {
            val pt = KEY_MAP[ch] ?: continue
            if (out.lastOrNull() != pt) out.add(pt)
        }
        return out
    }

    private fun resample(pts: List<Offset>, count: Int): List<Offset> {
        if (pts.size <= 1) return List(count) { pts.firstOrNull() ?: Offset.Zero }
        val cum = FloatArray(pts.size)
        for (i in 1 until pts.size) cum[i] = cum[i - 1] + dist(pts[i - 1], pts[i])
        val total = cum.last().coerceAtLeast(1e-5f)
        return (0 until count).map { idx ->
            val target = (idx.toFloat() / (count - 1)) * total
            var seg = 0
            while (seg < cum.size - 2 && cum[seg + 1] < target) seg++
            val span = (cum[seg + 1] - cum[seg]).coerceAtLeast(1e-5f)
            val t = ((target - cum[seg]) / span).coerceIn(0f, 1f)
            Offset(
                pts[seg].x + (pts[seg + 1].x - pts[seg].x) * t,
                pts[seg].y + (pts[seg + 1].y - pts[seg].y) * t
            )
        }
    }

    private fun dist(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

    companion object {
        private val KEY_MAP = mapOf(
            'q' to Offset(0.05f, 0.16f), 'w' to Offset(0.15f, 0.16f), 'e' to Offset(0.25f, 0.16f),
            'r' to Offset(0.35f, 0.16f), 't' to Offset(0.45f, 0.16f), 'y' to Offset(0.55f, 0.16f),
            'u' to Offset(0.65f, 0.16f), 'i' to Offset(0.75f, 0.16f), 'o' to Offset(0.85f, 0.16f),
            'p' to Offset(0.95f, 0.16f),
            'a' to Offset(0.10f, 0.50f), 's' to Offset(0.20f, 0.50f), 'd' to Offset(0.30f, 0.50f),
            'f' to Offset(0.40f, 0.50f), 'g' to Offset(0.50f, 0.50f), 'h' to Offset(0.60f, 0.50f),
            'j' to Offset(0.70f, 0.50f), 'k' to Offset(0.80f, 0.50f), 'l' to Offset(0.90f, 0.50f),
            'z' to Offset(0.20f, 0.84f), 'x' to Offset(0.30f, 0.84f), 'c' to Offset(0.40f, 0.84f),
            'v' to Offset(0.50f, 0.84f), 'b' to Offset(0.60f, 0.84f), 'n' to Offset(0.70f, 0.84f),
            'm' to Offset(0.80f, 0.84f)
        )
        private val DEFAULT_WORDS = listOf("hello", "there", "thanks", "tomorrow", "liquid", "glass", "android", "meeting", "awesome")
    }
}
