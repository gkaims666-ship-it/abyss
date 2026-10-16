package com.abyss.app.core.translate

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device translation + language detection via ML Kit. No API key, works
 * offline once a language model has been downloaded (downloaded on first use).
 *
 * This is the engine behind Abyss's X-style inline translator: detect the
 * language of a piece of text, and translate it on demand.
 */
object TranslationEngine {

    private val languageId = LanguageIdentification.getClient()
    private val clients = ConcurrentHashMap<String, Translator>()

    /** BCP-47 tag (e.g. "en", "ru", "ja") or "und" when undetermined. */
    suspend fun detect(text: String): String =
        runCatching { languageId.identifyLanguage(text).await() }.getOrDefault("und")

    /** True when ML Kit can translate this BCP-47 tag. */
    fun isSupported(tag: String): Boolean = TranslateLanguage.fromLanguageTag(tag) != null

    private fun clientFor(src: String, tgt: String): Translator {
        val key = "$src>$tgt"
        return clients.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(tgt)
                .build()
            Translation.getClient(options)
        }
    }

    /**
     * Translate [text] from [sourceTag] (auto-detected if null) into [targetTag].
     * Downloads the model if missing. Returns failure on unsupported language.
     */
    suspend fun translate(
        text: String,
        targetTag: String,
        sourceTag: String? = null,
    ): Result<Translation2> = runCatching {
        val detected = sourceTag ?: detect(text)
        val src = TranslateLanguage.fromLanguageTag(detected)
            ?: error(com.abyss.app.ui.i18n.I18n.current.unsupportedLanguage(com.abyss.app.ui.i18n.I18n.current.languageName(detected)))
        val tgt = TranslateLanguage.fromLanguageTag(targetTag)
            ?: error(com.abyss.app.ui.i18n.I18n.current.unsupportedLanguage(com.abyss.app.ui.i18n.I18n.current.languageName(targetTag)))
        if (src == tgt) return@runCatching Translation2(detected, text, false)

        val client = clientFor(src, tgt)
        client.downloadModelIfNeeded().await()
        val out = client.translate(text).await()
        Translation2(detected, out, true)
    }

    fun clear() {
        clients.values.forEach { it.close() }
        clients.clear()
    }
}

/** Result of a translation: detected source, output text, whether it changed. */
data class Translation2(
    val sourceLang: String,
    val text: String,
    val translated: Boolean,
)
