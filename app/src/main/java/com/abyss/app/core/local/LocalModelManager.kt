package com.abyss.app.core.local

import android.content.Context
import com.abyss.app.core.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Manages on-device GGUF model files for the local uncensored model:
 * download (from a HuggingFace direct link the user provides), list, select
 * and load into [LlamaBridge].
 *
 * Weights are gigabytes and live in the app's files dir — downloaded on the
 * device, never shipped in the APK.
 */
class LocalModelManager(context: Context) {

    private val dir = File(context.filesDir, "models").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readyLock = Mutex()
    @Volatile private var autoStarted = false

    companion object {
        /** Default keyless model: downloaded once on first run, then fully offline. */
        const val DEFAULT_URL =
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val DEFAULT_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val DEFAULT_LABEL = "Qwen2.5 1.5B Instruct (Q4_K_M, ~1.1 GB)"
    }

    /** True once the engine is present and a model is loaded (ready to answer). */
    fun ready(): Boolean = LlamaBridge.available && LlamaBridge.isLoaded

    /** True when this APK was built with the on-device engine. */
    fun enginePresent(): Boolean = LlamaBridge.available

    /**
     * First-run provisioning, fired once from app start (and again on first chat
     * if needed). If a model is already downloaded it is loaded; otherwise the
     * default model is downloaded and then loaded. No key, no account — when it
     * finishes the app has a working AI entirely on the device.
     */
    fun autoStart() {
        if (autoStarted || !LlamaBridge.available) return
        autoStarted = true
        scope.launch { runCatching { ensureReady() } }
    }

    /** Make sure a model is loaded; download the default if none exists yet. */
    suspend fun ensureReady(): Boolean = readyLock.withLock {
        if (!LlamaBridge.available) return@withLock false
        if (LlamaBridge.isLoaded) return@withLock true
        val existing = listModels().firstOrNull()
        val file = existing ?: download(DEFAULT_URL, DEFAULT_NAME).getOrNull() ?: return@withLock false
        load(file)
    }

    data class DownloadProgress(val bytes: Long, val total: Long, val done: Boolean, val error: String? = null)

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    fun listModels(): List<File> =
        dir.listFiles()?.filter { it.isFile && it.extension == "gguf" }?.sortedBy { it.name } ?: emptyList()

    fun nativeAvailable(): Boolean = LlamaBridge.available
    fun isLoaded(): Boolean = LlamaBridge.isLoaded

    suspend fun download(url: String, fileName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val out = File(dir, fileName.ifBlank { url.substringAfterLast('/').ifBlank { "model.gguf" } })
            val req = Request.Builder().url(url).build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed (${resp.code})")
                val body = resp.body ?: error("Empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(1 shl 16)
                        var read: Int
                        var sum = 0L
                        while (input.read(buf).also { read = it } >= 0) {
                            output.write(buf, 0, read)
                            sum += read
                            _progress.value = DownloadProgress(sum, total, false)
                        }
                    }
                }
            }
            _progress.value = DownloadProgress(out.length(), out.length(), true)
            out
        }.onFailure { _progress.value = DownloadProgress(0, 0, true, it.message) }
    }

    /** Load a model into the engine. Returns false if native lib missing or load fails. */
    suspend fun load(file: File, nThreads: Int = 4): Boolean = withContext(Dispatchers.Default) {
        LlamaBridge.load(file.absolutePath, nThreads = nThreads)
    }

    suspend fun generate(prompt: String, maxTokens: Int, temperature: Float): String =
        withContext(Dispatchers.Default) { LlamaBridge.generate(prompt, maxTokens, temperature) }

    fun delete(file: File) = file.delete()
}
