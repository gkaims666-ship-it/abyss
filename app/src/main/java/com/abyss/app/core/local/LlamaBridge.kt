package com.abyss.app.core.local

import android.util.Log

/**
 * JNI bridge to a llama.cpp-based native library (libabyss_llm.so) that runs a
 * local GGUF model on-device. The native part is built by CI only when the
 * project is built with -PwithLocalLlm (see app/src/main/cpp), so the default
 * APK always builds. When the library is absent, [available] is false and the
 * rest of the app keeps working with cloud providers.
 */
object LlamaBridge {

    val available: Boolean = try {
        System.loadLibrary("abyss_llm")
        true
    } catch (t: Throwable) {
        Log.i("Abyss", "Local LLM native lib not present: ${t.message}")
        false
    }

    @Volatile private var loadedPath: String? = null

    /** Native handles (opaque pointers) kept on the C++ side keyed by context. */
    private external fun nativeInit(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeFree()

    @Synchronized
    fun load(modelPath: String, nCtx: Int = 4096, nThreads: Int = 4): Boolean {
        if (!available) return false
        if (loadedPath == modelPath) return true
        if (loadedPath != null) runCatching { nativeFree() }
        val ok = runCatching { nativeInit(modelPath, nCtx, nThreads) }.getOrDefault(false)
        loadedPath = if (ok) modelPath else null
        return ok
    }

    val isLoaded: Boolean get() = loadedPath != null

    @Synchronized
    fun generate(prompt: String, maxTokens: Int = 512, temperature: Float = 0.7f): String {
        check(available && loadedPath != null) { "Local model not loaded" }
        return nativeGenerate(prompt, maxTokens, temperature)
    }

    @Synchronized
    fun unload() {
        if (available && loadedPath != null) runCatching { nativeFree() }
        loadedPath = null
    }
}
