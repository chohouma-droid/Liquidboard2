package com.liquidboard.ime.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SentimentSnapshot(val label: String, val tone: String)

data class AiPredictionResult(
    val suggestions: List<String>,
    val sentiment: SentimentSnapshot,
    val translatedText: String
)

class GeminiImeRepository(private val baseUrl: String) {

    suspend fun predict(
        text: String,
        gestureCandidates: List<String>,
        targetLanguage: String,
        translateEnabled: Boolean
    ): AiPredictionResult? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$baseUrl/api/ai/predict").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 4000
                readTimeout = 6000
                doOutput = true
            }
            val body = JSONObject().apply {
                put("text", text)
                put("gestureCandidates", JSONArray(gestureCandidates))
                put("targetLanguage", targetLanguage)
                put("translateEnabled", translateEnabled)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return@withContext null

            val raw = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(raw)
            val suggArray = json.optJSONArray("suggestions") ?: JSONArray()
            val suggestions = List(suggArray.length()) { suggArray.getString(it) }
            val sent = json.optJSONObject("sentiment")
            AiPredictionResult(
                suggestions = suggestions,
                sentiment = SentimentSnapshot(
                    label = sent?.optString("label", "Neutral") ?: "Neutral",
                    tone = sent?.optString("tone", "Balanced") ?: "Balanced"
                ),
                translatedText = json.optString("translatedText", "")
            )
        }.getOrNull()
    }
}
