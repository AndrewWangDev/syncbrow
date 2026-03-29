package com.syncbrow.tool.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        val AD_BLOCK_KEY = booleanPreferencesKey("ad_block_enabled")
        val TRACKER_BLOCK_KEY = booleanPreferencesKey("tracker_block")
        val INCOGNITO_KEY = booleanPreferencesKey("incognito_mode")
        val SAFE_BROWSING_KEY = booleanPreferencesKey("safe_browsing")
        val AGREE_TERMS = booleanPreferencesKey("agree_terms")
        val FORCE_ENABLE_COPY_PASTE_KEY = booleanPreferencesKey("force_enable_copy_paste")
        val REMOVE_REDIRECT_PROMPT_KEY = booleanPreferencesKey("remove_redirect_prompt")
        val QUIC_ENABLED_KEY = booleanPreferencesKey("quic_enabled")
        val SEARCH_ENGINE_KEY = intPreferencesKey("search_engine") // 0: Google, 1: Bing, 2: Yahoo
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: "zh" // Default to Chinese as requested by user context
    }

    val themeModeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: 0
    }

    val dohEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DOH_ENABLED_KEY] ?: false
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
}
