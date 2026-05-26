package de.lolo.lolotrans

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "FreeTranslationsProvider"

class FreeTranslationsProvider(
    private val getApiKey: () -> String
) : TranslatorProvider {

    companion object {
        private const val ENDPOINT = "https://translate-pa.googleapis.com/v1/translateHtml"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000

        fun mapLanguageCode(code: String): String {
            return when (code.lowercase().trim()) {
                "auto" -> "auto"
                "zh" -> "zh-CN"
                else -> code.lowercase().trim()
            }
        }
    }

    override suspend fun detectLanguage(text: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Kein FreeTranslations API-Key konfiguriert."))
        }
        Result.success("auto")
    }

    override suspend fun ensureModelReady(
        sourceLanguage: String, targetLanguage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Kein FreeTranslations API-Key konfiguriert."))
        }
        try {
            val conn = openConnection(URL(ENDPOINT), apiKey)
            val testBody = JSONArray().apply {
                put(JSONArray().apply {
                    put(JSONArray().apply { put("test") })
                    put("en")
                    put("de")
                })
                put("te")
            }
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(testBody.toString()) }
            val code = conn.responseCode
            if (code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException("FreeTranslations-Server antwortet nicht (HTTP $code)"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureModelReady failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun translate(
        text: String, sourceLanguage: String, targetLanguage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Kein FreeTranslations API-Key konfiguriert."))
            }
            val src = mapLanguageCode(sourceLanguage)
            val tgt = mapLanguageCode(targetLanguage)

            val texts = JSONArray().apply { put(text) }
            val body = JSONArray().apply {
                put(JSONArray().apply {
                    put(texts)
                    put(src)
                    put(tgt)
                })
                put("te")
            }

            val conn = openConnection(URL(ENDPOINT), apiKey)
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            when (code) {
                200 -> {
                    val response = readStream(conn.inputStream)
                    val translated = parseTranslatedText(response)
                    if (translated.isNullOrEmpty()) {
                        Result.failure(IllegalStateException("Leeres Uebersetzungsergebnis von FreeTranslations."))
                    } else {
                        Result.success(translated)
                    }
                }
                403 -> Result.failure(IllegalStateException(
                    "Zugriff verweigert (HTTP 403). API-Key ungultig."))
                429 -> Result.failure(IllegalStateException(
                    "Zu viele Anfragen (HTTP 429). Bitte spater erneut versuchen."))
                500 -> Result.failure(IllegalStateException(
                    "Server-Fehler (HTTP 500). Der Server hat ein internes Problem."))
                else -> {
                    val errorBody = conn.errorStream?.let { readStream(it) } ?: ""
                    Result.failure(IllegalStateException(
                        "Uebersetzungsfehler (HTTP $code): $errorBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "translate failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun close() {}

    private fun parseTranslatedText(content: String): String? {
        return try {
            val root = JSONArray(content)
            if (root.length() == 0) return null
            val values = mutableListOf<String>()
            collectStrings(root.opt(0), values)
            if (values.isEmpty()) null else cleanText(values.joinToString(""))
        } catch (e: Exception) {
            Log.e(TAG, "parseTranslatedText failed: ${e.message}", e)
            null
        }
    }

    private fun collectStrings(element: Any?, values: MutableList<String>) {
        when (element) {
            is String -> values.add(cleanText(element))
            is JSONArray -> {
                for (i in 0 until element.length()) {
                    collectStrings(element.opt(i), values)
                }
            }
        }
    }

    private fun cleanText(text: String): String {
        return android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("<[^>]+>"), "")
    }

    private fun openConnection(url: URL, apiKey: String): HttpURLConnection {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json+protobuf")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-goog-api-key", apiKey)
            setRequestProperty("Origin", "https://www.freetranslations.org")
            setRequestProperty("Referer", "https://www.freetranslations.org/")
        }
        return conn
    }

    private fun readStream(inputStream: java.io.InputStream): String {
        return BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
    }
}
