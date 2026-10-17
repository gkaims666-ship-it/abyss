package com.abyss.app.core.keys

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for provider API keys. Keys are entered manually by the
 * user and stored with Android Keystore-backed encryption, never in plaintext.
 */
class KeyStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "abyss_keys",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Decrypted keys cached in memory: EncryptedSharedPreferences decrypts on
     * every read (~1 ms each), which used to stall the UI thread whenever the
     * Settings screen opened. Filled once on first access.
     */
    private val cache: MutableMap<String, String> by lazy {
        val m = java.util.concurrent.ConcurrentHashMap<String, String>()
        runCatching {
            prefs.all.forEach { (k, v) -> if (k.startsWith("key_") && v is String && v.isNotBlank()) m[k] = v }
        }
        m
    }

    /** Warm the cipher + cache off the UI thread (called at app start). */
    fun warmUp() { cache.size }

    fun getKey(providerId: String): String? = cache["key_$providerId"]

    fun setKey(providerId: String, value: String) {
        val v = value.trim()
        if (v.isBlank()) { clearKey(providerId); return }
        cache["key_$providerId"] = v
        prefs.edit().putString("key_$providerId", v).apply()
    }

    fun clearKey(providerId: String) {
        cache.remove("key_$providerId")
        prefs.edit().remove("key_$providerId").apply()
    }

    fun hasKey(providerId: String): Boolean = getKey(providerId) != null
}
