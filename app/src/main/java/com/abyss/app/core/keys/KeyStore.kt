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

    fun getKey(providerId: String): String? =
        prefs.getString("key_$providerId", null)?.takeIf { it.isNotBlank() }

    fun setKey(providerId: String, value: String) {
        prefs.edit().putString("key_$providerId", value.trim()).apply()
    }

    fun clearKey(providerId: String) {
        prefs.edit().remove("key_$providerId").apply()
    }

    fun hasKey(providerId: String): Boolean = getKey(providerId) != null
}
