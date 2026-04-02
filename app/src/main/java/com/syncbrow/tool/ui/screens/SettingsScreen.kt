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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.rotate

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
    var showNetDiagDialog by remember { mutableStateOf(false) }

    // Password Manager state (top-level for dialog access)
    val pmEncryptionEnabled by settingsRepository.pmEncryptionEnabledFlow.collectAsState(initial = false)
    val pmRealPasswordHash by settingsRepository.pmRealPasswordHashFlow.collectAsState(initial = "")
    val pmEmergencyPasswordHash by settingsRepository.pmEmergencyPasswordHashFlow.collectAsState(initial = "")
    val pmFailedAttempts by settingsRepository.pmFailedAttemptsFlow.collectAsState(initial = 0)
    val pmLockoutTimestamp by settingsRepository.pmLockoutTimestampFlow.collectAsState(initial = 0L)
    val pmLastAuthTimestamp by settingsRepository.pmLastAuthTimestampFlow.collectAsState(initial = 0L)
    var showPmPasswordDialog by remember { mutableStateOf(false) }
    var pmInputPassword by remember { mutableStateOf("") }

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

        // ---- Account & Password ----
        Text(
            text = stringResource(R.string.settings_category_account),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // Password Manager

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_password_manager)) },
            supportingContent = { Text(stringResource(R.string.settings_password_manager_desc)) },
            trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable {
                if (pmEncryptionEnabled && pmRealPasswordHash.isNotEmpty()) {
                    // Check lockout
                    val now = System.currentTimeMillis()
                    if (pmLockoutTimestamp > 0 && now - pmLockoutTimestamp < 3600_000L) {
                        Toast.makeText(context, context.getString(R.string.pm_locked_out), Toast.LENGTH_LONG).show()
                    } else {
                        // Check if within 30-minute session
                        if (now - pmLastAuthTimestamp < 30 * 60 * 1000L) {
                            navController.navigate("password_manager")
                        } else {
                            // Reset lockout if 1 hour has passed
                            if (pmLockoutTimestamp > 0 && now - pmLockoutTimestamp >= 3600_000L) {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) { settingsRepository.resetPmLockout() }
                            }
                            pmInputPassword = ""
                            showPmPasswordDialog = true
                        }
                    }
                } else {
                    navController.navigate("password_manager")
                }
            }
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

        // Network Diagnosis
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_net_diagnosis)) },
            supportingContent = { Text(stringResource(R.string.settings_net_diagnosis_desc)) },
            leadingContent = { Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            modifier = Modifier.clickable { showNetDiagDialog = true },
            trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) }
        )

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
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) { settingsRepository.setSearchEngine(mode) }
                            showSearchEngineDialog = false
                        }.padding(16.dp))
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Password Manager Verification Dialog
    if (showPmPasswordDialog) {
        var showPwText by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showPmPasswordDialog = false },
            title = { Text(stringResource(R.string.pm_enter_password)) },
            text = {
                OutlinedTextField(
                    value = pmInputPassword,
                    onValueChange = { pmInputPassword = it },
                    label = { Text(stringResource(R.string.pm_password)) },
                    singleLine = true,
                    visualTransformation = if (showPwText) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPwText = !showPwText }) {
                            Icon(
                                if (showPwText) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val inputHash = SettingsRepository.sha256(pmInputPassword)
                    when {
                        inputHash == pmRealPasswordHash -> {
                            // Correct real password
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                settingsRepository.resetPmLockout()
                                settingsRepository.setPmLastAuthTimestamp(System.currentTimeMillis())
                            }
                            Toast.makeText(context, context.getString(R.string.pm_password_correct), Toast.LENGTH_SHORT).show()
                            showPmPasswordDialog = false
                            navController.navigate("password_manager")
                        }
                        pmEmergencyPasswordHash.isNotEmpty() && inputHash == pmEmergencyPasswordHash -> {
                            // Emergency password: wipe all passwords silently, navigate normally
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val pmDb = AppDatabase.getDatabase(context)
                                pmDb.passwordDao().deleteAllPasswords()
                                settingsRepository.resetPmLockout()
                                settingsRepository.setPmLastAuthTimestamp(System.currentTimeMillis())
                            }
                            Toast.makeText(context, context.getString(R.string.pm_password_correct), Toast.LENGTH_SHORT).show()
                            showPmPasswordDialog = false
                            navController.navigate("password_manager")
                        }
                        else -> {
                            // Wrong password
                            val newAttempts = pmFailedAttempts + 1
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                if (newAttempts >= 10) {
                                    settingsRepository.setPmFailedAttempts(newAttempts)
                                    settingsRepository.setPmLockoutTimestamp(System.currentTimeMillis())
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.pm_locked_out), Toast.LENGTH_LONG).show()
                                        showPmPasswordDialog = false
                                    }
                                } else {
                                    settingsRepository.setPmFailedAttempts(newAttempts)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        val remaining: Int = 10 - newAttempts
                                        Toast.makeText(context, context.getString(R.string.pm_password_wrong, remaining), Toast.LENGTH_SHORT).show()
                                        pmInputPassword = ""
                                    }
                                }
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.bookmark_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPmPasswordDialog = false }) {
                    Text(stringResource(R.string.bookmark_cancel))
                }
            }
        )
    }

    if (showNetDiagDialog) {
        NetworkDiagnosisDialog { showNetDiagDialog = false }
    }
}

@Composable
fun NetworkDiagnosisDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isChecking by remember { mutableStateOf(true) }
    
    // Status tracking
    var statusOnline by remember { mutableStateOf(0) } // 0:Checking, 1:Yes, 2:No
    var statusDns by remember { mutableStateOf(0) }    // 0:Checking, 1:Normal, 2:Abnormal, 3:Skipped
    var statusIpConn by remember { mutableStateOf(0) } // 0:Checking, 1:Normal, 2:Abnormal, 3:Skipped
    var statusPortConn by remember { mutableStateOf(0) } // 0:Checking, 1:Normal, 2:Abnormal, 3:Skipped
    var statusGfw by remember { mutableStateOf(0) }    // 0:Checking, 1:Not Exist, 2:Exists, 3:Skipped

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // 1. Online Check
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            val isOnline = capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
            
            statusOnline = if (isOnline) 1 else 2

            // 2. DNS Check (dns.google)
            try {
                val addresses = InetAddress.getAllByName("dns.google")
                statusDns = if (addresses.isNotEmpty()) 1 else 2
            } catch (_: Exception) {
                statusDns = 2
            }

            // 3. IP Connectivity (Apple - 17.253.144.10)
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("17.253.144.10", 443), 4000)
                socket.close()
                statusIpConn = 1
                statusPortConn = 1 // Socket check covers port connectivity
            } catch (_: Exception) {
                statusIpConn = 2
                statusPortConn = 2
            }

            // 5. GFW Status (google.com)
            try {
                val socketGfw = Socket()
                socketGfw.connect(InetSocketAddress("google.com", 443), 4000)
                socketGfw.close()
                statusGfw = 1
            } catch (_: Exception) {
                statusGfw = 2
            }

            isChecking = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(if (isChecking) R.string.net_diag_checking else R.string.net_diag_title))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                DiagItem(stringResource(R.string.net_diag_is_online), statusOnline, listOf(R.string.net_diag_status_yes, R.string.net_diag_status_no))
                DiagItem(stringResource(R.string.net_diag_dns), statusDns, listOf(R.string.net_diag_status_normal, R.string.net_diag_status_abnormal))
                DiagItem(stringResource(R.string.net_diag_ip_conn), statusIpConn, listOf(R.string.net_diag_status_normal, R.string.net_diag_status_abnormal))
                DiagItem(stringResource(R.string.net_diag_port_conn), statusPortConn, listOf(R.string.net_diag_status_normal, R.string.net_diag_status_abnormal))
                DiagItem(stringResource(R.string.net_diag_gfw), statusGfw, listOf(R.string.net_diag_status_not_exist, R.string.net_diag_status_exist))

                if (!isChecking) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        stringResource(R.string.net_diag_conclusion),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val (conclusionRes, conclusionColor) = when {
                        statusOnline == 2 -> R.string.net_diag_conclusion_offline to MaterialTheme.colorScheme.error
                        statusOnline == 1 && statusDns == 1 && statusIpConn == 1 && statusPortConn == 1 && statusGfw == 1 -> 
                            R.string.net_diag_conclusion_all_ok to Color(0xFF4CAF50)
                        statusOnline == 1 && statusDns == 1 && statusIpConn == 1 && statusPortConn == 1 && statusGfw == 2 ->
                            R.string.net_diag_conclusion_gfw to Color(0xFFFF9800)
                        else -> R.string.net_diag_conclusion_problem to MaterialTheme.colorScheme.error
                    }
                    
                    Text(
                        stringResource(conclusionRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = conclusionColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bookmark_cancel))
            }
        }
    )
}

@Composable
fun DiagItem(label: String, status: Int, statusStrings: List<Int>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        
        when (status) {
            0 -> { // Checking
                val infiniteTransition = rememberInfiniteTransition()
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )
                Icon(
                    Icons.Default.NetworkCheck,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(angle),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
            1 -> { // Normal / Yes / Not Exist
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                Text(
                    stringResource(statusStrings[0]),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            2 -> { // Abnormal / No / Exist
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(statusStrings[1]),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            3 -> { // Skipped
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.net_diag_status_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFC107),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
