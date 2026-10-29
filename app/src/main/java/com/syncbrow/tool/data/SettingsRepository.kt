package com.syncbrow.tool.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val LANGUAGE_KEY = stringPreferencesKey("language") // "zh" or "en"
        val THEME_MODE_KEY = intPreferencesKey("theme_mode") // 0: System, 1: Light, 2: Dark
        val DOH_ENABLED_KEY = booleanPreferencesKey("doh_enabled")
        val DOH_PROVIDER_KEY = stringPreferencesKey("doh_provider") // "opendns", "cloudflare", "google"
        val AD_BLOCK_KEY = booleanPreferencesKey("ad_block_enabled")
        val TRACKER_BLOCK_KEY = booleanPreferencesKey("tracker_block")
        val INCOGNITO_KEY = booleanPreferencesKey("incognito_mode")
        val SAFE_BROWSING_KEY = booleanPreferencesKey("safe_browsing")
        val AGREE_TERMS = booleanPreferencesKey("agree_terms")
        val FORCE_ENABLE_COPY_PASTE_KEY = booleanPreferencesKey("force_enable_copy_paste")
        val REMOVE_REDIRECT_PROMPT_KEY = booleanPreferencesKey("remove_redirect_prompt")
        val QUIC_ENABLED_KEY = booleanPreferencesKey("quic_enabled")
        val SEARCH_ENGINE_KEY = intPreferencesKey("search_engine") // 0: Google, 1: Bing, 2: Yahoo
        val PM_ENCRYPTION_ENABLED_KEY = booleanPreferencesKey("pm_encryption_enabled")
        val PM_REAL_PASSWORD_HASH_KEY = stringPreferencesKey("pm_real_password_hash")
        val PM_EMERGENCY_PASSWORD_HASH_KEY = stringPreferencesKey("pm_emergency_password_hash")
        val PM_FAILED_ATTEMPTS_KEY = intPreferencesKey("pm_failed_attempts")
        val PM_LOCKOUT_TIMESTAMP_KEY = longPreferencesKey("pm_lockout_timestamp")
        val PM_LAST_AUTH_TIMESTAMP_KEY = longPreferencesKey("pm_last_auth_timestamp")

        private const val PEPPER = "SyncBrow_Salt_Secured#2026"

        private fun computeHash(input: String, saltHex: String): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            var digest = md.digest((saltHex + input).toByteArray(Charsets.UTF_8))
            for (i in 1..10000) {
                md.reset()
                digest = md.digest(digest)
            }
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun computeLegacyPepperHash(input: String): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            var digest = md.digest((PEPPER + input).toByteArray(Charsets.UTF_8))
            for (i in 1..10000) {
                md.reset()
                digest = md.digest(digest)
            }
            return "SALT:" + digest.joinToString("") { "%02x".format(it) }
        }

        fun hashPassword(input: String): String {
            val saltBytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(saltBytes)
            val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
            val hashHex = computeHash(input, saltHex)
            return "SALT:$saltHex:$hashHex"
        }

        fun getSaltFromHash(storedHash: String): ByteArray? {
            if (storedHash.startsWith("SALT:")) {
                val parts = storedHash.removePrefix("SALT:").split(":")
                if (parts.size == 2) {
                    val saltHex = parts[0]
                    return try {
                        ByteArray(saltHex.length / 2) { i ->
                            saltHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            return null
        }

        fun verifyPassword(input: String, storedHash: String): Boolean {
            if (storedHash.isEmpty()) return false
            return if (storedHash.startsWith("SALT:")) {
                val parts = storedHash.removePrefix("SALT:").split(":")
                if (parts.size == 2) {
                    val saltHex = parts[0]
                    val expectedHashHex = parts[1]
                    val computedHashHex = computeHash(input, saltHex)
                    java.security.MessageDigest.isEqual(
                        computedHashHex.toByteArray(Charsets.UTF_8),
                        expectedHashHex.toByteArray(Charsets.UTF_8)
                    )
                } else {
                    val legacyHash = computeLegacyPepperHash(input)
                    java.security.MessageDigest.isEqual(
                        legacyHash.toByteArray(Charsets.UTF_8),
                        storedHash.toByteArray(Charsets.UTF_8)
                    )
                }
            } else {
                val legacy = legacySha256(input)
                java.security.MessageDigest.isEqual(
                    legacy.toByteArray(Charsets.UTF_8),
                    storedHash.toByteArray(Charsets.UTF_8)
                )
            }
        }

        private fun legacySha256(input: String): String {
            val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun sha256(input: String): String {
            return hashPassword(input)
        }
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: "zh" // Default to Chinese
    }

    val themeModeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: 0
    }

    val dohEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DOH_ENABLED_KEY] ?: false
    }

    val dohProviderFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DOH_PROVIDER_KEY] ?: "opendns"
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = lang
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
        }
    }

    suspend fun setDohEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DOH_ENABLED_KEY] = enabled
        }
    }

    suspend fun setDohProvider(provider: String) {
        context.dataStore.edit { prefs ->
            prefs[DOH_PROVIDER_KEY] = provider
        }
    }

    val adBlockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AD_BLOCK_KEY] ?: false
    }

    val trackerBlockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TRACKER_BLOCK_KEY] ?: false
    }

    val incognitoEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[INCOGNITO_KEY] ?: false
    }

    val termsAcceptedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AGREE_TERMS] ?: false
    }

    val safeBrowsingEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SAFE_BROWSING_KEY] ?: true // Default to true for safety
    }

    val forceEnableCopyPasteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[FORCE_ENABLE_COPY_PASTE_KEY] ?: false
    }

    val removeRedirectPromptEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[REMOVE_REDIRECT_PROMPT_KEY] ?: false
    }

    val quicEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[QUIC_ENABLED_KEY] ?: false
    }

    val searchEngineFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SEARCH_ENGINE_KEY] ?: 0
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AD_BLOCK_KEY] = enabled
        }
    }

    suspend fun setTrackerBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[TRACKER_BLOCK_KEY] = enabled
        }
    }

    suspend fun setIncognitoEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[INCOGNITO_KEY] = enabled
        }
    }

    suspend fun setTermsAccepted(accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AGREE_TERMS] = accepted
        }
    }

    suspend fun setSafeBrowsingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SAFE_BROWSING_KEY] = enabled
        }
    }

    suspend fun setForceEnableCopyPaste(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[FORCE_ENABLE_COPY_PASTE_KEY] = enabled
        }
    }

    suspend fun setRemoveRedirectPromptEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[REMOVE_REDIRECT_PROMPT_KEY] = enabled
        }
    }

    suspend fun setQuicEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[QUIC_ENABLED_KEY] = enabled
        }
    }

    suspend fun setSearchEngine(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[SEARCH_ENGINE_KEY] = mode
        }
    }

    // Password Manager
    val pmEncryptionEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PM_ENCRYPTION_ENABLED_KEY] ?: false
    }

    val pmRealPasswordHashFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PM_REAL_PASSWORD_HASH_KEY] ?: ""
    }

    val pmEmergencyPasswordHashFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PM_EMERGENCY_PASSWORD_HASH_KEY] ?: ""
    }

    val pmFailedAttemptsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PM_FAILED_ATTEMPTS_KEY] ?: 0
    }

    val pmLockoutTimestampFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[PM_LOCKOUT_TIMESTAMP_KEY] ?: 0L
    }

    val pmLastAuthTimestampFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[PM_LAST_AUTH_TIMESTAMP_KEY] ?: 0L
    }

    suspend fun setPmEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PM_ENCRYPTION_ENABLED_KEY] = enabled
        }
    }

    suspend fun setPmRealPasswordHash(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[PM_REAL_PASSWORD_HASH_KEY] = hash
        }
    }

    suspend fun setPmEmergencyPasswordHash(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[PM_EMERGENCY_PASSWORD_HASH_KEY] = hash
        }
    }

    suspend fun setPmFailedAttempts(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[PM_FAILED_ATTEMPTS_KEY] = count
        }
    }

    suspend fun setPmLockoutTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[PM_LOCKOUT_TIMESTAMP_KEY] = timestamp
        }
    }

    suspend fun setPmLastAuthTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[PM_LAST_AUTH_TIMESTAMP_KEY] = timestamp
        }
    }

    suspend fun resetPmLockout() {
        context.dataStore.edit { prefs ->
            prefs[PM_FAILED_ATTEMPTS_KEY] = 0
            prefs[PM_LOCKOUT_TIMESTAMP_KEY] = 0L
        }
    }
}
