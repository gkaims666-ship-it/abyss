package com.abyss.app.core.media

import android.util.Base64
import com.abyss.app.core.keys.KeyStore
import com.abyss.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Speech-to-text and media understanding using the user's existing keys:
 *  - transcription via OpenAI's audio endpoint (Whisper), when an OpenAI key
 *    is set; otherwise via Gemini;
 *  - "what is happening / what is heard" for audio, video and images via
 *    Gemini (inline base64), which understands all three directly.
 */
class MediaAI(private val keyStore: KeyStore) {

    private val geminiVisionModel = "gemini-2.0-flash"

    suspend fun transcribe(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val openAiKey = keyStore.getKey("openai")
            if (openAiKey != null) transcribeOpenAi(file, openAiKey)
            else {
                val g = keyStore.getKey("gemini") ?: error(com.abyss.app.ui.i18n.I18n.current.errNeedTranscribeKey)
                understandGemini(file, "audio/mp4", "Transcribe this audio verbatim.", g)
            }
        }
    }

    suspend fun understand(file: File, mime: String, prompt: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val g = keyStore.getKey("gemini")
                    ?: error(com.abyss.app.ui.i18n.I18n.current.errNeedGeminiKey)
                understandGemini(file, mime, prompt, g)
            }
        }

    private fun transcribeOpenAi(file: File, key: String): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("file", file.name,
                file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val req = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $key")
            .post(body).build()
        Http.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("${com.abyss.app.ui.i18n.I18n.current.errTranscription} (${resp.code})")
            return Http.json.parseToJsonElement(text).jsonObject["text"]
                ?.jsonPrimitive?.content ?: error(com.abyss.app.ui.i18n.I18n.current.errEmptyTranscript)
        }
    }

    private fun understandGemini(file: File, mime: String, prompt: String, key: String): String {
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                addJsonObject {
                    put("parts", buildJsonArray {
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", mime); put("data", b64)
                            }
                        }
                        addJsonObject { put("text", prompt) }
                    })
                }
            })
        }.toString()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "$geminiVisionModel:generateContent?key=$key"
        val req = Request.Builder().url(url)
            .addHeader("content-type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        Http.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("${com.abyss.app.ui.i18n.I18n.current.errAnalysis} (${resp.code})")
            val obj = Http.json.parseToJsonElement(text).jsonObject
            val parts = obj["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            return parts?.joinToString("") {
                it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
            }?.ifBlank { com.abyss.app.ui.i18n.I18n.current.errNoAnalysis } ?: error(com.abyss.app.ui.i18n.I18n.current.errNoAnalysis)
        }
    }
}
