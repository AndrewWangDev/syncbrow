package com.syncbrow.tool.ui.screens

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import com.syncbrow.tool.R
import com.syncbrow.tool.data.AppDatabase
import com.syncbrow.tool.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val language by settingsRepository.languageFlow.collectAsState(initial = "zh")
    val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = 0)
    val dohEnabled by settingsRepository.dohEnabledFlow.collectAsState(initial = false)
    val adBlockEnabled by settingsRepository.adBlockEnabledFlow.collectAsState(initial = false)
    val trackerBlockEnabled by settingsRepository.trackerBlockEnabledFlow.collectAsState(initial = false)
    val incognitoEnabled by settingsRepository.incognitoEnabledFlow.collectAsState(initial = false)
    val safeBrowsingEnabled by settingsRepository.safeBrowsingEnabledFlow.collectAsState(initial = true)
    val forceEnableCopyPaste by settingsRepository.forceEnableCopyPasteFlow.collectAsState(initial = false)
    val removeRedirectPromptEnabled by settingsRepository.removeRedirectPromptEnabledFlow.collectAsState(initial = false)
    val quicEnabled by settingsRepository.quicEnabledFlow.collectAsState(initial = false)
    val searchEngine by settingsRepository.searchEngineFlow.collectAsState(initial = 0)

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 24.dp))

        Text(
            text = stringResource(R.string.settings_category_appearance),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        )

        // Language
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_language_title)) },
            supportingContent = { 
                val currentLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                Text(if (currentLocale.startsWith("zh")) "中文" else "English")
            },
            modifier = Modifier.clickable { showLanguageDialog = true }
        )

        // Theme
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_theme_title)) },
            supportingContent = {
                Text(when(themeMode) {
                    1 -> stringResource(R.string.theme_light)
                    2 -> stringResource(R.string.theme_dark)
                    else -> stringResource(R.string.theme_system)
                })
            },
            modifier = Modifier.clickable { showThemeDialog = true }
        )

        // Search Engine
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_search_engine)) },
            supportingContent = {
                Text(when(searchEngine) {
                    1 -> stringResource(R.string.search_engine_bing)
                    2 -> stringResource(R.string.search_engine_yahoo)
                    else -> stringResource(R.string.search_engine_google)
                })
            },
            modifier = Modifier.clickable { showSearchEngineDialog = true }
        )

        Text(
            text = stringResource(R.string.settings_category_privacy),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // DoH Toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_doh_title)) },
            supportingContent = { Text(stringResource(R.string.settings_doh_desc)) },
            trailingContent = {
                Switch(checked = dohEnabled, onCheckedChange = { scope.launch { settingsRepository.setDohEnabled(it) } })
            }
        )

        // Ad Block Toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_ad_block)) },
            supportingContent = { Text(stringResource(R.string.settings_ad_block_desc)) },
            trailingContent = {
                Switch(checked = adBlockEnabled, onCheckedChange = { scope.launch { settingsRepository.setAdBlockEnabled(it) } })
            }
        )

        // Tracker Block Toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_tracker_block)) },
            supportingContent = { Text(stringResource(R.string.settings_tracker_block_desc)) },
            trailingContent = {
                Switch(checked = trackerBlockEnabled, onCheckedChange = { scope.launch { settingsRepository.setTrackerBlockEnabled(it) } })
            }
        )

        // Safe Browsing Toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_safe_browsing)) },
            supportingContent = { Text(stringResource(R.string.settings_safe_browsing_desc)) },
            trailingContent = {
                Switch(checked = safeBrowsingEnabled, onCheckedChange = { scope.launch { settingsRepository.setSafeBrowsingEnabled(it) } })
            }
        )

        // Incognito Mode Toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_incognito)) },
            supportingContent = { Text(stringResource(R.string.settings_incognito_desc)) },
            trailingContent = {
                Switch(checked = incognitoEnabled, onCheckedChange = { scope.launch { settingsRepository.setIncognitoEnabled(it) } })
            }
        )

        // QUIC Toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_quic_title)) },
            supportingContent = { Text(stringResource(R.string.settings_quic_desc)) },
            trailingContent = {
                Switch(checked = quicEnabled, onCheckedChange = { scope.launch { settingsRepository.setQuicEnabled(it) } })
            }
        )

        Text(
            text = stringResource(R.string.settings_category_extensions),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // CN Acceleration Toggle - REMOVED PER USER REQUEST
        // Enable Copy Paste Toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_enable_copy_paste)) },
            supportingContent = { Text(stringResource(R.string.settings_enable_copy_paste_desc)) },
            trailingContent = {
                Switch(checked = forceEnableCopyPaste, onCheckedChange = { scope.launch { settingsRepository.setForceEnableCopyPaste(it) } })
            }
        )

        // Remove Redirect Prompt Toggle
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_remove_redirect)) },
            supportingContent = { Text(stringResource(R.string.settings_remove_redirect_desc)) },
            trailingContent = {
                Switch(checked = removeRedirectPromptEnabled, onCheckedChange = { scope.launch { settingsRepository.setRemoveRedirectPromptEnabled(it) } })
            }
        )

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // About Us
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about)) },
            modifier = Modifier.clickable { navController.navigate("about") },
            trailingContent = { Icon(androidx.compose.material.icons.Icons.Default.KeyboardArrowRight, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Clear Browsing Data
        Button(
            onClick = {
                scope.launch {
                    db.historyDao().clearAllHistory()
                    val msg = context.getString(R.string.settings_clear_history_toast)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.settings_clear_history))
        }

        // Clear All Data
        Button(
            onClick = {
                scope.launch {
                    db.historyDao().clearAllHistory()
                    db.downloadDao().clearAllDownloads()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    WebStorage.getInstance().deleteAllData()
                    val msg = context.getString(R.string.settings_clear_data_toast)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.settings_clear_data))
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_select_language)) },
            text = {
                Column {
                    Text("中文", modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch { settingsRepository.setLanguage("zh") }
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh"))
                        showLanguageDialog = false
                    }.padding(16.dp))
                    Text("English", modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch { settingsRepository.setLanguage("en") }
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                        showLanguageDialog = false
                    }.padding(16.dp))
                }
            },
            confirmButton = {}
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.settings_select_theme)) },
            text = {
                Column {
                    val themes = listOf(0 to stringResource(R.string.theme_system), 1 to stringResource(R.string.theme_light), 2 to stringResource(R.string.theme_dark))
                    themes.forEach { (mode, name) ->
                        Text(name, modifier = Modifier.fillMaxWidth().clickable {
                            scope.launch { settingsRepository.setThemeMode(mode) }
                            showThemeDialog = false
                        }.padding(16.dp))
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text(stringResource(R.string.settings_search_engine)) },
            text = {
                Column {
                    val engines = listOf(
                        0 to stringResource(R.string.search_engine_google),
                        1 to stringResource(R.string.search_engine_bing),
                        2 to stringResource(R.string.search_engine_yahoo)
                    )
                    engines.forEach { (mode, name) ->
                        Text(name, modifier = Modifier.fillMaxWidth().clickable {
                            scope.launch { settingsRepository.setSearchEngine(mode) }
                            showSearchEngineDialog = false
                        }.padding(16.dp))
                    }
                }
            },
            confirmButton = {}
        )
    }
}
