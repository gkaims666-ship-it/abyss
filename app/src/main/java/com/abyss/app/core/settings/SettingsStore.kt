package com.abyss.app.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "abyss_settings")

data class AbyssSettings(
    val paletteId: String = "abyss",
    val providerId: String = "openai",
    val model: String = "gpt-4o",
    val agentId: String = "auto",
    val backgroundHarvest: Boolean = false, // off by default: it spends money/tokens
    // Translator (X-style)
    val targetLang: String = "en",          // user's preferred reading language
    val autoTranslate: Boolean = true,       // inline auto-translate like X
    val translateEngine: String = "mlkit",   // "mlkit" (on-device) | "llm"
    val mutedLangs: Set<String> = emptySet(), // languages the user turned off
    // Call scam guard
    val guardEnabled: Boolean = false,
    val guardLookupUrl: String = "",         // optional {number} data-source template
    // Local model + self-improvement
    val localModelUrl: String = "",          // HuggingFace direct GGUF link
    val localModelActivePath: String = "",   // currently loaded model file
    val harvestPerRun: Int = 3,              // external questions per background run
    val harvestIntervalHours: Int = 6,       // how often the harvester runs
    val improvementCount: Int = 0,           // knowledge "version" counter
    // Generation controls
    val temperature: Float = 0.7f,
    val systemPrompt: String = "",
    // Personalisation
    val userName: String = "",
    val language: String = "system",          // "system" or a UI language code
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val palette = stringPreferencesKey("palette")
        val provider = stringPreferencesKey("provider")
        val model = stringPreferencesKey("model")
        val agent = stringPreferencesKey("agent")
        val harvest = booleanPreferencesKey("harvest")
        val targetLang = stringPreferencesKey("target_lang")
        val autoTranslate = booleanPreferencesKey("auto_translate")
        val translateEngine = stringPreferencesKey("translate_engine")
        val mutedLangs = stringSetPreferencesKey("muted_langs")
        val guardEnabled = booleanPreferencesKey("guard_enabled")
        val guardUrl = stringPreferencesKey("guard_url")
        val localUrl = stringPreferencesKey("local_url")
        val localActive = stringPreferencesKey("local_active")
        val harvestPerRun = intPreferencesKey("harvest_per_run")
        val harvestInterval = intPreferencesKey("harvest_interval")
        val improvement = intPreferencesKey("improvement")
        val temperature = floatPreferencesKey("temperature")
        val systemPrompt = stringPreferencesKey("system_prompt")
        val userName = stringPreferencesKey("user_name")
        val language = stringPreferencesKey("language")
    }

    val settings: Flow<AbyssSettings> = context.dataStore.data.map { p ->
        AbyssSettings(
            paletteId = p[Keys.palette] ?: "abyss",
            providerId = p[Keys.provider] ?: "openai",
            model = p[Keys.model] ?: "gpt-4o",
            agentId = p[Keys.agent] ?: "auto",
            backgroundHarvest = p[Keys.harvest] ?: false,
            targetLang = p[Keys.targetLang] ?: "en",
            autoTranslate = p[Keys.autoTranslate] ?: true,
            translateEngine = p[Keys.translateEngine] ?: "mlkit",
            mutedLangs = p[Keys.mutedLangs] ?: emptySet(),
            guardEnabled = p[Keys.guardEnabled] ?: false,
            guardLookupUrl = p[Keys.guardUrl] ?: "",
            localModelUrl = p[Keys.localUrl] ?: "",
            localModelActivePath = p[Keys.localActive] ?: "",
            harvestPerRun = p[Keys.harvestPerRun] ?: 3,
            harvestIntervalHours = p[Keys.harvestInterval] ?: 6,
            improvementCount = p[Keys.improvement] ?: 0,
            temperature = p[Keys.temperature] ?: 0.7f,
            systemPrompt = p[Keys.systemPrompt] ?: "",
            userName = p[Keys.userName] ?: "",
            language = p[Keys.language] ?: "system",
        )
    }

    suspend fun setPalette(id: String) = context.dataStore.edit { it[Keys.palette] = id }
    suspend fun setProvider(id: String) = context.dataStore.edit { it[Keys.provider] = id }
    suspend fun setModel(model: String) = context.dataStore.edit { it[Keys.model] = model }
    suspend fun setAgent(id: String) = context.dataStore.edit { it[Keys.agent] = id }
    suspend fun setHarvest(on: Boolean) = context.dataStore.edit { it[Keys.harvest] = on }
    suspend fun setTargetLang(code: String) = context.dataStore.edit { it[Keys.targetLang] = code }
    suspend fun setAutoTranslate(on: Boolean) = context.dataStore.edit { it[Keys.autoTranslate] = on }
    suspend fun setTranslateEngine(e: String) = context.dataStore.edit { it[Keys.translateEngine] = e }
    suspend fun setGuardEnabled(on: Boolean) = context.dataStore.edit { it[Keys.guardEnabled] = on }
    suspend fun setGuardUrl(url: String) = context.dataStore.edit { it[Keys.guardUrl] = url }
    suspend fun setLocalModelUrl(url: String) = context.dataStore.edit { it[Keys.localUrl] = url }
    suspend fun setLocalModelActive(path: String) = context.dataStore.edit { it[Keys.localActive] = path }
    suspend fun setHarvestPerRun(n: Int) = context.dataStore.edit { it[Keys.harvestPerRun] = n }
    suspend fun setHarvestInterval(h: Int) = context.dataStore.edit { it[Keys.harvestInterval] = h }
    suspend fun bumpImprovement() = context.dataStore.edit {
        it[Keys.improvement] = (it[Keys.improvement] ?: 0) + 1
    }
    suspend fun setTemperature(t: Float) = context.dataStore.edit { it[Keys.temperature] = t }
    suspend fun setSystemPrompt(s: String) = context.dataStore.edit { it[Keys.systemPrompt] = s }
    suspend fun setUserName(n: String) = context.dataStore.edit { it[Keys.userName] = n.trim() }
    suspend fun setLanguage(code: String) = context.dataStore.edit { it[Keys.language] = code }
    suspend fun toggleMutedLang(code: String) = context.dataStore.edit { p ->
        val cur = p[Keys.mutedLangs] ?: emptySet()
        p[Keys.mutedLangs] = if (code in cur) cur - code else cur + code
    }
}
