package de.lolo.lolotrans

import android.os.Build
import android.util.Log
import io.xbot.tdlib.TdLib
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val TD_TAG = "TelegramTdlib"

sealed class TelegramAuthStatus {
    data object Starting : TelegramAuthStatus()
    data object WaitPhoneNumber : TelegramAuthStatus()
    data object WaitCode : TelegramAuthStatus()
    data object WaitPassword : TelegramAuthStatus()
    data object Ready : TelegramAuthStatus()
    data class Error(val message: String) : TelegramAuthStatus()
}

object TelegramTdlibClient {
    private const val MAX_TRANSLATE_SEGMENT_LENGTH = 1_500
    private var job = SupervisorJob()
    private val scope get() = CoroutineScope(job + Dispatchers.IO)
    private val extraCounter = AtomicLong(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val _authStatus = MutableStateFlow<TelegramAuthStatus>(TelegramAuthStatus.Starting)

    val authStatus: StateFlow<TelegramAuthStatus> = _authStatus

    @Volatile
    private var started = false
    private var clientId: Int = 0

    fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            clientId = TdLib.createClientId()
            TdLib.execute("""{"@type":"setLogVerbosityLevel","new_verbosity_level":1}""")
            scope.launch { receiveLoop() }
            sendRaw(JSONObject().put("@type", "getAuthorizationState"))
        }
    }

    /**
     * Stoppt die Receive-Loop und schließt die TDLib-Instanz.
     * Löscht keine Datenbank, keine Session und kein Login.
     * Nach [stop] kann [start] erneut aufgerufen werden, um einen neuen Client zu erzeugen.
     */
    fun stop() {
        synchronized(this) {
            if (!started) return
            started = false
        }
        try {
            sendRaw(JSONObject().put("@type", "close"))
        } catch (_: Exception) {}
        val stopped = IllegalStateException("TDLib client stopped")
        pending.values.forEach { it.completeExceptionally(stopped) }
        pending.clear()
        job.cancel()
        job = SupervisorJob()
        clientId = 0
        _authStatus.value = TelegramAuthStatus.Starting
        Log.d(TD_TAG, "TDLib client stopped")
    }

    suspend fun submitPhoneNumber(phoneNumber: String): Result<Unit> {
        start()
        val normalized = phoneNumber.trim().replace(" ", "").replace("-", "")
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Telefonnummer fehlt."))
        }
        return sendForResult(
            JSONObject()
                .put("@type", "setAuthenticationPhoneNumber")
                .put("phone_number", normalized)
                .put("settings", JSONObject().put("@type", "phoneNumberAuthenticationSettings"))
        ).map { Unit }
    }

    suspend fun submitCode(code: String): Result<Unit> {
        start()
        val cleanCode = code.trim()
        if (cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Telegram-Code fehlt."))
        }
        return sendForResult(
            JSONObject()
                .put("@type", "checkAuthenticationCode")
                .put("code", cleanCode)
        ).map { Unit }
    }

    suspend fun submitPassword(password: String): Result<Unit> {
        start()
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("2FA-Passwort fehlt."))
        }
        return sendForResult(
            JSONObject()
                .put("@type", "checkAuthenticationPassword")
                .put("password", password)
        ).map { Unit }
    }

    suspend fun translate(text: String, targetLanguage: String): Result<String> {
        start()
        if (_authStatus.value != TelegramAuthStatus.Ready) {
            return Result.failure(IllegalStateException("Telegram ist noch nicht angemeldet."))
        }
        val normalizedText = text.replace("\r\n", "\n").replace("\r", "\n")
        if (normalizedText.contains('\n') || normalizedText.length > MAX_TRANSLATE_SEGMENT_LENGTH) {
            return translateSegments(normalizedText, targetLanguage)
        }
        return translateSingleSegment(normalizedText, targetLanguage)
    }

    private suspend fun translateSegments(text: String, targetLanguage: String): Result<String> {
        val parts = splitIntoTranslateSegments(text)
        val translated = StringBuilder()
        for (part in parts) {
            if (part.isEmpty()) continue
            val lineBreak = if (part.endsWith("\n")) "\n" else ""
            val body = part.removeSuffix("\n")
            if (body.isBlank()) {
                translated.append(body).append(lineBreak)
                continue
            }
            val result = translateSingleSegment(body, targetLanguage)
            if (result.isFailure) return result
            translated.append(result.getOrThrow()).append(lineBreak)
        }
        return Result.success(translated.toString())
    }

    private fun splitIntoTranslateSegments(text: String): List<String> {
        val segments = mutableListOf<String>()
        val lines = text.split(Regex("(?<=\\n)"))
        for (line in lines) {
            if (line.length <= MAX_TRANSLATE_SEGMENT_LENGTH) {
                segments.add(line)
                continue
            }
            var remaining = line
            while (remaining.length > MAX_TRANSLATE_SEGMENT_LENGTH) {
                val limit = MAX_TRANSLATE_SEGMENT_LENGTH.coerceAtMost(remaining.length)
                val splitAt = listOf(
                    remaining.lastIndexOf(". ", limit),
                    remaining.lastIndexOf("! ", limit),
                    remaining.lastIndexOf("? ", limit),
                    remaining.lastIndexOf("; ", limit),
                    remaining.lastIndexOf(", ", limit),
                    remaining.lastIndexOf(" ", limit)
                ).filter { it > 200 }.maxOrNull() ?: limit
                segments.add(remaining.substring(0, splitAt).trimEnd())
                remaining = remaining.substring(splitAt).trimStart()
            }
            if (remaining.isNotEmpty()) segments.add(remaining)
        }
        return segments
    }

    private suspend fun translateSingleSegment(text: String, targetLanguage: String): Result<String> {
        return sendForResult(
            JSONObject()
                .put("@type", "translateText")
                .put("text", JSONObject()
                    .put("@type", "formattedText")
                    .put("text", text)
                    .put("entities", JSONArray()))
                .put("to_language_code", mapLanguageCode(targetLanguage))
        ).mapCatching { result ->
            val translated = parseTranslatedText(result)
            if (translated.isBlank()) {
                Log.w(TD_TAG, "Empty Telegram translation result type=${result.optString("@type")} inputLength=${text.length}")
                text
            } else {
                translated
            }
        }
    }

    private fun parseTranslatedText(result: JSONObject): String {
        val rawText = result.opt("text") ?: return ""
        return when (rawText) {
            is String -> rawText
            is JSONObject -> rawText.optString("text", "")
            else -> rawText.toString()
        }
    }

    private suspend fun sendForResult(request: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val extra = extraCounter.getAndIncrement().toString()
            request.put("@extra", extra)
            val deferred = CompletableDeferred<JSONObject>()
            pending[extra] = deferred
            sendRaw(request)
            val result = withTimeoutResult(45_000, deferred)
            if (result.optString("@type") == "error") {
                Result.failure(IllegalStateException(result.optString("message", "Telegram-Fehler")))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun withTimeoutResult(
        timeoutMs: Long,
        deferred: CompletableDeferred<JSONObject>
    ): JSONObject {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!deferred.isCompleted && System.currentTimeMillis() < deadline) {
            delay(50)
        }
        if (!deferred.isCompleted) {
            throw IllegalStateException("Telegram antwortet nicht.")
        }
        return deferred.await()
    }

    private fun sendRaw(request: JSONObject) {
        TdLib.send(clientId, request.toString())
    }

    private fun receiveLoop() {
        while (started) {
            try {
                val raw = TdLib.receive(1.0) ?: continue
                val event = JSONObject(raw)
                val extra = event.optString("@extra", "")
                if (extra.isNotBlank()) {
                    pending.remove(extra)?.complete(event)
                }
                handleUpdate(event)
            } catch (e: Exception) {
                if (started) Log.w(TD_TAG, "TDLib event failed: ${e.message}")
            }
        }
    }

    private fun handleUpdate(event: JSONObject) {
        if (event.optString("@type") != "updateAuthorizationState") return
        handleAuthorizationState(event.optJSONObject("authorization_state") ?: return)
    }

    private fun handleAuthorizationState(state: JSONObject) {
        when (val type = state.optString("@type")) {
            "authorizationStateWaitTdlibParameters" -> sendTdlibParameters()
            "authorizationStateWaitPhoneNumber" -> _authStatus.value = TelegramAuthStatus.WaitPhoneNumber
            "authorizationStateWaitCode" -> _authStatus.value = TelegramAuthStatus.WaitCode
            "authorizationStateWaitPassword" -> _authStatus.value = TelegramAuthStatus.WaitPassword
            "authorizationStateReady" -> _authStatus.value = TelegramAuthStatus.Ready
            "authorizationStateLoggingOut", "authorizationStateClosing", "authorizationStateClosed" -> {
                _authStatus.value = TelegramAuthStatus.Starting
            }
            else -> {
                Log.w(TD_TAG, "Unhandled auth state: $type")
                _authStatus.value = TelegramAuthStatus.Error(
                    "Unbekannter Telegram-Authentifizierungsstatus: $type"
                )
            }
        }
    }

    private fun sendTdlibParameters() {
        if (BuildConfig.TELEGRAM_API_ID <= 0 || BuildConfig.TELEGRAM_API_HASH.isBlank()) {
            _authStatus.value = TelegramAuthStatus.Error("Telegram API-Zugangsdaten sind nicht konfiguriert.")
            return
        }
        val context = AppContext.require()
        val tdDir = File(context.filesDir, "tdlib").apply { mkdirs() }
        val filesDir = File(context.filesDir, "tdlib-files").apply { mkdirs() }
        sendRaw(
            JSONObject()
                .put("@type", "setTdlibParameters")
                .put("use_test_dc", false)
                .put("database_directory", tdDir.absolutePath)
                .put("files_directory", filesDir.absolutePath)
                .put("database_encryption_key", "")
                .put("use_file_database", true)
                .put("use_chat_info_database", false)
                .put("use_message_database", false)
                .put("use_secret_chats", false)
                .put("api_id", BuildConfig.TELEGRAM_API_ID)
                .put("api_hash", BuildConfig.TELEGRAM_API_HASH)
                .put("system_language_code", "de")
                .put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("system_version", "Android ${Build.VERSION.RELEASE}")
                .put("application_version", BuildConfig.VERSION_NAME)
                .put("enable_storage_optimizer", true)
                .put("ignore_file_names", true)
        )
    }

    private fun mapLanguageCode(code: String): String {
        return when (code.lowercase().trim()) {
            "zh" -> "zh-Hans"
            "zh-cn" -> "zh-Hans"
            "zh-tw" -> "zh-Hant"
            "iw" -> "he"
            else -> code.lowercase().trim()
        }
    }
}
