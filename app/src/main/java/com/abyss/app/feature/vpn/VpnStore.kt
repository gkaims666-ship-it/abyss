package com.abyss.app.feature.vpn

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.vpnDataStore by preferencesDataStore(name = "abyss_vpn")

/** Persists imported servers, the subscription URL and the selected server. */
class VpnStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private object K {
        val servers = stringPreferencesKey("servers")
        val selected = stringPreferencesKey("selected")
        val subUrl = stringPreferencesKey("sub_url")
    }

    val servers: Flow<List<VpnServer>> = context.vpnDataStore.data.map { p ->
        p[K.servers]?.let { runCatching { json.decodeFromString(ListSerializer(VpnServer.serializer()), it) }.getOrNull() } ?: emptyList()
    }
    val selectedId: Flow<String?> = context.vpnDataStore.data.map { it[K.selected] }
    val subscriptionUrl: Flow<String> = context.vpnDataStore.data.map { it[K.subUrl] ?: "" }

    suspend fun saveServers(list: List<VpnServer>) = context.vpnDataStore.edit {
        it[K.servers] = json.encodeToString(ListSerializer(VpnServer.serializer()), list)
    }
    suspend fun setSelected(id: String) = context.vpnDataStore.edit { it[K.selected] = id }
    suspend fun setSubscriptionUrl(url: String) = context.vpnDataStore.edit { it[K.subUrl] = url }
}
