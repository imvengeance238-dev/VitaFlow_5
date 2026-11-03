package app.vitaflow.com.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.vitaflow.com.data.vitaPrefs
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the Tavily API key encrypted with a key that never leaves the phone's Keystore. */
class SecretStore(private val context: Context) {
    private val alias = "vitaflow_secret_key"
    private val prefKey = stringPreferencesKey("tavily_key_enc")
    private val geminiPrefKey = stringPreferencesKey("gemini_key_enc")
    private val geminiModelPrefKey = stringPreferencesKey("gemini_model_name")

    val hasTavilyKey: Flow<Boolean> = context.vitaPrefs.data.map { !it[prefKey].isNullOrEmpty() }
    val hasGeminiKey: Flow<Boolean> = context.vitaPrefs.data.map { !it[geminiPrefKey].isNullOrEmpty() }
    val geminiModel: Flow<String> = context.vitaPrefs.data.map { it[geminiModelPrefKey]?.takeIf { m -> m.isNotBlank() } ?: DEFAULT_GEMINI_MODEL }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    suspend fun saveTavilyKey(value: String) {
        val v = value.trim()
        context.vitaPrefs.edit { p ->
            if (v.isEmpty()) {
                p.remove(prefKey)
            } else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val out = cipher.iv + cipher.doFinal(v.toByteArray(Charsets.UTF_8))
                p[prefKey] = Base64.encodeToString(out, Base64.NO_WRAP)
            }
        }
    }

    suspend fun tavilyKey(): String? {
        val enc = context.vitaPrefs.data.first()[prefKey] ?: return null
        return runCatching {
            val bytes = Base64.decode(enc, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun saveGeminiKey(value: String) {
        val v = value.trim()
        context.vitaPrefs.edit { p ->
            if (v.isEmpty()) p.remove(geminiPrefKey) else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val out = cipher.iv + cipher.doFinal(v.toByteArray(Charsets.UTF_8))
                p[geminiPrefKey] = Base64.encodeToString(out, Base64.NO_WRAP)
            }
        }
    }

    suspend fun geminiKey(): String? {
        val enc = context.vitaPrefs.data.first()[geminiPrefKey] ?: return null
        return runCatching {
            val bytes = Base64.decode(enc, Base64.NO_WRAP)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun saveGeminiModel(value: String) {
        val v = value.trim()
        context.vitaPrefs.edit { p -> if (v.isEmpty()) p.remove(geminiModelPrefKey) else p[geminiModelPrefKey] = v }
    }

    suspend fun geminiModelName(): String =
        context.vitaPrefs.data.first()[geminiModelPrefKey]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_GEMINI_MODEL

    companion object {
        /** Google-র "latest" alias: নতুন Flash মডেল এলে নিজে থেকেই সেটা ধরে। Settings থেকে বদলানো যায়। */
        const val DEFAULT_GEMINI_MODEL = "gemini-flash-latest"
    }
}
