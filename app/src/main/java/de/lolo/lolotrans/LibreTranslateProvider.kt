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
import java.net.UnknownHostException

private const val TAG = "LibreTranslateProvider"

class LibreTranslateProvider(
    private val getBaseUrl: () -> String,
    private val getApiKey: () -> String
) : TranslatorProvider {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000

        fun isLanguageSupported(code: String): Boolean = code in supportedLanguages

        val supportedLanguages = setOf(
            "en", "de", "fr", "es", "it", "pt", "nl", "pl", "ru",
            "ja", "ko", "zh", "ar", "tr", "hi", "th", "vi", "sv",
            "ro", "no", "da", "fi", "cs", "el", "hu", "id", "ms",
            "sk", "bg", "hr", "ca", "he", "uk"
        )
    }

    override suspend fun detectLanguage(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl().trimEnd('/')
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Kein LibreTranslate-Server konfiguriert. Bitte URL in den Einstellungen hinterlegen."))
            }
            val url = URL("$baseUrl/detect")
            val conn = openConnection(url, getApiKey())
            if (conn == null) {
                return@withContext Result.failure(
                    IllegalStateException("Server nicht erreichbar. Bitte URL und Internetverbindung prüfen."))
            }
            val requestBody = JSONObject().apply { put("q", text) }
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

            val code = conn.responseCode
            if (code != 200) {
                val errorBody = conn.errorStream?.let { readStream(it) } ?: ""
                Log.w(TAG, "detect failed HTTP $code: $errorBody")
                if (code == 404) {
                    // /detect not supported by all LibreTranslate servers, try /translate with auto
                    return@withContext Result.success("auto")
                }
                return@withContext Result.failure(
                    IllegalStateException("Spracherkennung fehlgeschlagen (HTTP $code)"))
            }

            val response = readStream(conn.inputStream)
            val json = JSONArray(response)
            if (json.length() > 0) {
                val detected = json.getJSONObject(0).optString("language", "")
                if (detected.isNotEmpty() && detected != "und") {
                    Log.d(TAG, "detected language: $detected")
                    return@withContext Result.success(detected)
                }
            }
            // fallback: let server auto-detect via translate
            Result.success("auto")
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Server nicht erreichbar: ${e.message}")
            Result.failure(IllegalStateException(
                "Server nicht erreichbar. Bitte URL und Internetverbindung prüfen."))
        } catch (e: Exception) {
            Log.e(TAG, "detectLanguage failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun ensureModelReady(
        sourceLanguage: String, targetLanguage: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl().trimEnd('/')
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Kein LibreTranslate-Server konfiguriert."))
        }
        try {
            val testUrl = URL("$baseUrl/languages")
            val conn = openConnection(testUrl, getApiKey()) ?: return@withContext Result.failure(
                IllegalStateException("Server nicht erreichbar."))
            val code = conn.responseCode
            if (code != 200) {
                return@withContext Result.failure(
                    IllegalStateException("Server antwortet nicht korrekt (HTTP $code)"))
            }
            Result.success(Unit)
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException(
                "Server nicht erreichbar. Bitte URL und Internetverbindung prüfen."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun translate(
        text: String, sourceLanguage: String, targetLanguage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl().trimEnd('/')
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Kein LibreTranslate-Server konfiguriert."))
            }
            val url = URL("$baseUrl/translate")
            val conn = openConnection(url, getApiKey())
                ?: return@withContext Result.failure(
                    IllegalStateException("Server nicht erreichbar."))
            val src = if (sourceLanguage == "auto") "auto" else sourceLanguage

            val requestBody = JSONObject().apply {
                put("q", text)
                put("source", src)
                put("target", targetLanguage)
                put("format", "text")
                val key = getApiKey()
                if (key.isNotBlank()) put("api_key", key)
            }
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

            val code = conn.responseCode
            when (code) {
                200 -> {
                    val response = readStream(conn.inputStream)
                    val result = JSONObject(response).optString("translatedText", "")
                    if (result.isEmpty()) {
                        Result.failure(IllegalStateException("Leeres Übersetzungsergebnis vom Server."))
                    } else {
                        Result.success(result)
                    }
                }
                403 -> Result.failure(IllegalStateException(
                    "Zugriff verweigert (HTTP 403). API-Key erforderlich oder ungültig."))
                429 -> Result.failure(IllegalStateException(
                    "Zu viele Anfragen (HTTP 429). Bitte später erneut versuchen."))
                500 -> Result.failure(IllegalStateException(
                    "Server-Fehler (HTTP 500). Der Server hat ein internes Problem."))
                else -> {
                    val errorBody = conn.errorStream?.let { readStream(it) } ?: ""
                    Result.failure(IllegalStateException(
                        "Übersetzungsfehler (HTTP $code): $errorBody"))
                }
            }
        } catch (e: UnknownHostException) {
            Result.failure(IllegalStateException(
                "Server nicht erreichbar. Bitte URL und Internetverbindung prüfen."))
        } catch (e: Exception) {
            Log.e(TAG, "translate failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun close() {}

    private fun openConnection(url: URL, apiKey: String): HttpURLConnection? {
        val conn = try {
            url.openConnection() as HttpURLConnection
        } catch (e: Exception) {
            Log.e(TAG, "Could not open connection: ${e.message}")
            return null
        }
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return conn
    }

    private fun readStream(inputStream: java.io.InputStream): String {
        return BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
    }
}
