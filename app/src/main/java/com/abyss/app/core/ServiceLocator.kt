package com.abyss.app.core

import android.content.Context
import com.abyss.app.core.data.AbyssDb
import com.abyss.app.core.keys.KeyStore
import com.abyss.app.core.improve.KnowledgeHarvester
import com.abyss.app.core.improve.ReflectionEngine
import com.abyss.app.core.local.LocalModelManager
import com.abyss.app.core.media.AudioRecorder
import com.abyss.app.core.media.MediaAI
import com.abyss.app.core.memory.MemoryRepository
import com.abyss.app.core.settings.SettingsStore
import com.abyss.app.core.translate.TranslateService
import com.abyss.app.feature.vpn.VpnStore

/**
 * Lightweight manual dependency container. Avoids pulling in a DI framework so
 * the build stays lean; everything is created once and shared.
 */
object ServiceLocator {
    private lateinit var appContext: Context

    val db: AbyssDb by lazy { AbyssDb.get(appContext) }
    val keyStore: KeyStore by lazy { KeyStore(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val memory: MemoryRepository by lazy { MemoryRepository(db.memoryDao()) }
    val translate: TranslateService by lazy { TranslateService(keyStore) }
    val mediaAI: MediaAI by lazy { MediaAI(keyStore) }
    val audioRecorder: AudioRecorder by lazy { AudioRecorder(appContext) }
    val localModel: LocalModelManager by lazy { LocalModelManager(appContext) }
    val vpnStore: VpnStore by lazy { VpnStore(appContext) }
    val harvester: KnowledgeHarvester by lazy { KnowledgeHarvester(keyStore, memory, settings) }
    val reflection: ReflectionEngine by lazy { ReflectionEngine(keyStore, memory, settings, localModel) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
