package com.abyss.app.core.translate

import com.abyss.app.core.keys.KeyStore
import com.abyss.app.core.provider.LlmMessage
import com.abyss.app.core.provider.LlmRequest
import com.abyss.app.core.provider.ProviderRegistry
import com.abyss.app.core.provider.Role
import com.abyss.app.core.settings.AbyssSettings

/**
 * Applies Abyss's translator policy on top of [TranslationEngine], mirroring
 * X's behavior:
 *  - detect each text's language;
 *  - if it differs from the user's reading language and isn't muted, it's a
 *    translation candidate (auto or on tap, per settings);
 *  - translate via on-device ML Kit, or via the user's LLM when chosen.
 */
class TranslateService(private val keyStore: KeyStore) {

    data class Decision(val shouldOffer: Boolean, val sourceLang: String)

    /** Decide whether to show/apply translation for [text]. */
    suspend fun decide(text: String, settings: AbyssSettings): Decision {
        if (text.isBlank() || text.length < 2) return Decision(false, "und")
        val src = TranslationEngine.detect(text)
        val offer = src != "und" &&
            src != settings.targetLang &&
            src !in settings.mutedLangs
        return Decision(offer, src)
    }

    suspend fun translate(text: String, settings: AbyssSettings, sourceHint: String? = null): Result<Translation2> {
        return if (settings.translateEngine == "llm") translateWithLlm(text, settings, sourceHint)
        else TranslationEngine.translate(text, settings.targetLang, sourceHint)
    }

    private suspend fun translateWithLlm(
        text: String, settings: AbyssSettings, sourceHint: String?,
    ): Result<Translation2> = runCatching {
        val provider = ProviderRegistry.byId(settings.providerId) ?: ProviderRegistry.openAi
        val key = keyStore.getKey(provider.id)
            ?: if (provider.id == "local") "" else error("No API key for ${provider.displayName}")
        val targetName = Languages.name(settings.targetLang)
        val out = provider.complete(
            LlmRequest(
                model = settings.model,
                messages = listOf(LlmMessage(Role.USER,
                    "Translate the text below into $targetName. " +
                        "Output ONLY the translation, no notes.\n\n$text")),
                temperature = 0.2, maxTokens = 1024, stream = false,
            ), key,
        ).trim()
        val src = sourceHint ?: TranslationEngine.detect(text)
        Translation2(src, out, out.isNotBlank() && out != text)
    }
}
