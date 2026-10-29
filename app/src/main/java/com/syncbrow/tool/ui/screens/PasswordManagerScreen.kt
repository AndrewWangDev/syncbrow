package com.syncbrow.tool.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.syncbrow.tool.R
import com.syncbrow.tool.data.AppDatabase
import com.syncbrow.tool.data.PasswordEntity
import com.syncbrow.tool.data.SettingsRepository
import com.syncbrow.tool.util.CryptoManager
import com.syncbrow.tool.util.PasswordUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun copyToClipboardSensitive(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val settingsRepository = remember { SettingsRepository(context) }

    val passwordDao = remember(db) { db.passwordDao() }
    val rawPasswordList by passwordDao.getAllPasswords().collectAsState(initial = emptyList())

    val encryptionEnabled by settingsRepository.pmEncryptionEnabledFlow.collectAsState(initial = false)
    val realPasswordHash by settingsRepository.pmRealPasswordHashFlow.collectAsState(initial = "")
    val emergencyPasswordHash by settingsRepository.pmEmergencyPasswordHashFlow.collectAsState(initial = "")
    val pmFailedAttempts by settingsRepository.pmFailedAttemptsFlow.collectAsState(initial = 0)
    val pmLockoutTimestamp by settingsRepository.pmLockoutTimestampFlow.collectAsState(initial = 0L)
    val pmLastAuthTimestamp by settingsRepository.pmLastAuthTimestampFlow.collectAsState(initial = 0L)

    var isSessionAuthed by rememberSaveable { mutableStateOf(false) }
    var isDecoySession by rememberSaveable { mutableStateOf(false) }
    var authInputPassword by remember { mutableStateOf("") }
    var showAuthPwText by remember { mutableStateOf(false) }

    // Preset believable decoy credentials
    var decoyPasswords by remember {
        mutableStateOf(
            listOf(
                PasswordEntity(id = -101, siteName = "google.com", username = "alex.miller@gmail.com", password = "P@ssw0rdGoogle#2024", timestamp = System.currentTimeMillis()),
                PasswordEntity(id = -102, siteName = "github.com", username = "alexmiller-dev", password = "ghp_X7v91bK9zL0m2Qp8aJ4e", timestamp = System.currentTimeMillis()),
                PasswordEntity(id = -103, siteName = "microsoft.com", username = "alex.m@outlook.com", password = "MsftSecret!982", timestamp = System.currentTimeMillis()),
                PasswordEntity(id = -104, siteName = "amazon.com", username = "alex.miller@gmail.com", password = "AmzShop_Secure@77", timestamp = System.currentTimeMillis()),
                PasswordEntity(id = -105, siteName = "netflix.com", username = "alex.miller@gmail.com", password = "StreamNetflix!4K", timestamp = System.currentTimeMillis())
            )
        )
    }

    // Monotonic clock for session and lockout
    val nowElapsed = SystemClock.elapsedRealtime()
    val isSessionValid = (nowElapsed - pmLastAuthTimestamp) < 30 * 60 * 1000L
    val isLockedOut = pmLockoutTimestamp > 0 && (nowElapsed - pmLockoutTimestamp) < 3600_000L
    val needsAuth = encryptionEnabled && realPasswordHash.isNotEmpty() && !isSessionValid && !isSessionAuthed

    // Active password list: gated behind authentication
    val activePasswordList = remember(needsAuth, isDecoySession, decoyPasswords, rawPasswordList) {
        when {
            needsAuth -> emptyList()
            isDecoySession -> decoyPasswords
            else -> rawPasswordList
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredPasswords = remember(activePasswordList, searchQuery) {
        if (searchQuery.isBlank()) activePasswordList
        else activePasswordList.filter {
            it.siteName.contains(searchQuery, ignoreCase = true) ||
            it.username.contains(searchQuery, ignoreCase = true)
        }
    }

    // UI Dialog States
    var selectedPassword by remember { mutableStateOf<PasswordEntity?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingPassword by remember { mutableStateOf<PasswordEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Add/Edit Fields
    var inputSite by remember { mutableStateOf("") }
    var inputUsername by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }

    // Settings Dialog States
    var settingsEncryptionEnabled by remember { mutableStateOf(false) }
    var settingsRealPassword by remember { mutableStateOf("") }
    var settingsEmergencyPassword by remember { mutableStateOf("") }

    if (needsAuth) {
        if (isLockedOut) {
            AlertDialog(
                onDismissRequest = { navController.popBackStack() },
                title = { Text(stringResource(R.string.pm_locked_out)) },
                text = { Text(stringResource(R.string.pm_locked_out)) },
                confirmButton = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text(stringResource(R.string.pm_cancel))
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { navController.popBackStack() },
                title = { Text(stringResource(R.string.pm_enter_password)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = authInputPassword,
                            onValueChange = { authInputPassword = it },
                            label = { Text(stringResource(R.string.pm_password)) },
                            singleLine = true,
                            visualTransformation = if (showAuthPwText) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showAuthPwText = !showAuthPwText }) {
                                    Icon(
                                        if (showAuthPwText) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        when {
                            SettingsRepository.verifyPassword(authInputPassword, realPasswordHash) -> {
                                val salt = SettingsRepository.getSaltFromHash(realPasswordHash) ?: "syncbrow_default_salt".toByteArray()
                                CryptoManager.setSessionPassword(authInputPassword, salt)
                                scope.launch(Dispatchers.IO) {
                                    settingsRepository.resetPmLockout()
                                    settingsRepository.setPmLastAuthTimestamp(SystemClock.elapsedRealtime())
                                }
                                isDecoySession = false
                                isSessionAuthed = true
                                Toast.makeText(context, context.getString(R.string.pm_password_correct), Toast.LENGTH_SHORT).show()
                            }
                            emergencyPasswordHash.isNotEmpty() && SettingsRepository.verifyPassword(authInputPassword, emergencyPasswordHash) -> {
                                // Decoy mode: Do NOT delete real passwords. Display realistic decoy vault
                                scope.launch(Dispatchers.IO) {
                                    settingsRepository.resetPmLockout()
                                    settingsRepository.setPmLastAuthTimestamp(SystemClock.elapsedRealtime())
                                }
                                isDecoySession = true
                                isSessionAuthed = true
                                Toast.makeText(context, context.getString(R.string.pm_password_correct), Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                val newAttempts = pmFailedAttempts + 1
                                scope.launch(Dispatchers.IO) {
                                    if (newAttempts >= 10) {
                                        settingsRepository.setPmFailedAttempts(newAttempts)
                                        settingsRepository.setPmLockoutTimestamp(SystemClock.elapsedRealtime())
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(R.string.pm_locked_out), Toast.LENGTH_LONG).show()
                                            navController.popBackStack()
                                        }
                                    } else {
                                        settingsRepository.setPmFailedAttempts(newAttempts)
                                        withContext(Dispatchers.Main) {
                                            val remaining = 10 - newAttempts
                                            Toast.makeText(context, context.getString(R.string.pm_password_wrong, remaining), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text(stringResource(R.string.pm_cancel))
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pm_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        settingsEncryptionEnabled = encryptionEnabled
                        settingsRealPassword = ""
                        settingsEmergencyPassword = ""
                        showSettingsDialog = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingPassword = null
                inputSite = ""
                inputUsername = ""
                inputPassword = ""
                showAddEditDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pm_add))
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Search Bar
            if (activePasswordList.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text(stringResource(R.string.pm_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (activePasswordList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.pm_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else if (filteredPasswords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.pm_search_no_results, searchQuery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(filteredPasswords) { pw ->
                        ListItem(
                            headlineContent = { 
                                Text(pw.siteName, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            },
                            supportingContent = { 
                                Text(pw.username, maxLines = 1, overflow = TextOverflow.Ellipsis) 
                            },
                            leadingContent = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = { 
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) 
                            },
                            modifier = Modifier.clickable {
                                selectedPassword = pw
                                showDetailDialog = true
                            }
                        )
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    // Detail Dialog (Single-item Lazy Decryption)
    if (showDetailDialog && selectedPassword != null) {
        val pw = selectedPassword!!
        var showPwText by remember { mutableStateOf(false) }
        val plainPassword = remember(pw) {
            if (isDecoySession) pw.password
            else try {
                CryptoManager.decrypt(pw.password)
            } catch (e: Exception) {
                android.util.Log.e("PasswordManager", "Failed to decrypt password id=${pw.id}", e)
                "******"
            }
        }

        AlertDialog(
            onDismissRequest = {
                showDetailDialog = false
                showPwText = false
            },
            title = { Text(stringResource(R.string.pm_detail_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pw.siteName,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.pm_site_name)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pw.username,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.pm_username)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = plainPassword,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.pm_password)) },
                        readOnly = true,
                        visualTransformation = if (showPwText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    copyToClipboardSensitive(context, "password", plainPassword)
                                    Toast.makeText(context, context.getString(R.string.pm_password_copied), Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.pm_password_copied)
                                    )
                                }
                                IconButton(onClick = { 
                                    showPwText = !showPwText 
                                }) {
                                    Icon(
                                        if (showPwText) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        editingPassword = pw
                        inputSite = pw.siteName
                        inputUsername = pw.username
                        inputPassword = plainPassword
                        showDetailDialog = false
                        showAddEditDialog = true
                    }) {
                        Text(stringResource(R.string.pm_edit))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        showDetailDialog = false
                        showDeleteConfirm = true
                    }) {
                        Text(stringResource(R.string.pm_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDetailDialog = false
                    showPwText = false
                }) {
                    Text(stringResource(R.string.pm_cancel))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm && selectedPassword != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.pm_delete)) },
            text = { Text(stringResource(R.string.pm_confirm_delete)) },
            confirmButton = {
                TextButton(onClick = {
                    val passwordToDelete = selectedPassword!!
                    showDeleteConfirm = false
                    selectedPassword = null
                    if (isDecoySession) {
                        decoyPasswords = decoyPasswords.filter { it.id != passwordToDelete.id }
                        Toast.makeText(context, context.getString(R.string.pm_password_deleted), Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch(Dispatchers.IO) {
                            db.passwordDao().deletePassword(passwordToDelete)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.pm_password_deleted), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.pm_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.pm_cancel))
                }
            }
        )
    }

    // Add/Edit Dialog
    if (showAddEditDialog) {
        val isEdit = editingPassword != null
        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(stringResource(if (isEdit) R.string.pm_edit else R.string.pm_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = inputSite,
                        onValueChange = { inputSite = it },
                        label = { Text(stringResource(R.string.pm_site_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputUsername,
                        onValueChange = { inputUsername = it },
                        label = { Text(stringResource(R.string.pm_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it },
                        label = { Text(stringResource(R.string.pm_password)) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                inputPassword = PasswordUtils.generateStrongPassword()
                                copyToClipboardSensitive(context, "password", inputPassword)
                                Toast.makeText(context, context.getString(R.string.pm_password_copied), Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Generate")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Strength Indicator
                    val strengthResult = PasswordUtils.evaluateStrength(inputPassword)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = when(strengthResult.strength) {
                                PasswordUtils.Strength.VERY_WEAK -> 0.1f
                                PasswordUtils.Strength.WEAK -> 0.3f
                                PasswordUtils.Strength.NORMAL -> 0.5f
                                PasswordUtils.Strength.STRONG -> 0.8f
                                PasswordUtils.Strength.VERY_STRONG -> 1.0f
                            },
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = strengthResult.color,
                            trackColor = strengthResult.color.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(strengthResult.labelResId),
                            style = MaterialTheme.typography.bodySmall,
                            color = strengthResult.color
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputSite.isBlank() || inputUsername.isBlank() || inputPassword.isBlank()) return@TextButton
                    if (inputPassword == "******") {
                        Toast.makeText(context, context.getString(R.string.pm_password_decrypt_failed), Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    if (isDecoySession) {
                        if (isEdit) {
                            decoyPasswords = decoyPasswords.map {
                                if (it.id == editingPassword!!.id) it.copy(siteName = inputSite.trim(), username = inputUsername.trim(), password = inputPassword.trim())
                                else it
                            }
                        } else {
                            decoyPasswords = decoyPasswords + PasswordEntity(
                                id = -System.currentTimeMillis(),
                                siteName = inputSite.trim(),
                                username = inputUsername.trim(),
                                password = inputPassword.trim(),
                                timestamp = System.currentTimeMillis()
                            )
                        }
                        Toast.makeText(context, context.getString(R.string.pm_password_saved), Toast.LENGTH_SHORT).show()
                        showAddEditDialog = false
                    } else {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val encryptedPassword = CryptoManager.encrypt(inputPassword.trim())
                                if (isEdit) {
                                    db.passwordDao().updatePassword(
                                        editingPassword!!.copy(
                                            siteName = inputSite.trim(),
                                            username = inputUsername.trim(),
                                            password = encryptedPassword
                                        )
                                    )
                                } else {
                                    db.passwordDao().insertPassword(
                                        PasswordEntity(
                                            siteName = inputSite.trim(),
                                            username = inputUsername.trim(),
                                            password = encryptedPassword
                                        )
                                    )
                                }
                                launch(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.pm_password_saved), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PasswordManager", "Failed to encrypt password", e)
                                launch(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.pm_password_encrypt_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showAddEditDialog = false
                    }
                }) {
                    Text(stringResource(R.string.pm_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text(stringResource(R.string.pm_cancel))
                }
            }
        )
    }

    // Encryption Settings Dialog
    if (showSettingsDialog) {
        var showRealPw by remember { mutableStateOf(false) }
        var showEmergencyPw by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(stringResource(R.string.pm_settings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.pm_enable_encryption),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settingsEncryptionEnabled,
                            onCheckedChange = { settingsEncryptionEnabled = it }
                        )
                    }

                    if (settingsEncryptionEnabled) {
                        OutlinedTextField(
                            value = settingsRealPassword,
                            onValueChange = { settingsRealPassword = it },
                            label = { Text(stringResource(R.string.pm_real_password)) },
                            supportingText = { Text(stringResource(R.string.pm_real_password_hint)) },
                            singleLine = true,
                            visualTransformation = if (showRealPw) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showRealPw = !showRealPw }) {
                                    Icon(
                                        if (showRealPw) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settingsEmergencyPassword,
                            onValueChange = { settingsEmergencyPassword = it },
                            label = { Text(stringResource(R.string.pm_emergency_password)) },
                            supportingText = { Text(stringResource(R.string.pm_emergency_password_hint)) },
                            singleLine = true,
                            visualTransformation = if (showEmergencyPw) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showEmergencyPw = !showEmergencyPw }) {
                                    Icon(
                                        if (showEmergencyPw) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        if (settingsEncryptionEnabled) {
                            if (settingsRealPassword.isBlank()) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.pm_real_password_required), Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            if (settingsEmergencyPassword.isNotBlank() && settingsRealPassword == settingsEmergencyPassword) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.pm_passwords_must_differ), Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            val masterHash = SettingsRepository.hashPassword(settingsRealPassword)
                            val salt = SettingsRepository.getSaltFromHash(masterHash) ?: "syncbrow_default_salt".toByteArray()
                            CryptoManager.setSessionPassword(settingsRealPassword, salt)

                            settingsRepository.setPmEncryptionEnabled(true)
                            settingsRepository.setPmRealPasswordHash(masterHash)
                            if (settingsEmergencyPassword.isNotBlank()) {
                                settingsRepository.setPmEmergencyPasswordHash(SettingsRepository.hashPassword(settingsEmergencyPassword))
                            } else {
                                settingsRepository.setPmEmergencyPasswordHash("")
                            }
                            settingsRepository.resetPmLockout()
                            withContext(Dispatchers.Main) {
                                isSessionAuthed = true
                                isDecoySession = false
                                Toast.makeText(context, context.getString(R.string.pm_encryption_saved), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            CryptoManager.clearSessionKey()
                            settingsRepository.setPmEncryptionEnabled(false)
                            settingsRepository.setPmRealPasswordHash("")
                            settingsRepository.setPmEmergencyPasswordHash("")
                            settingsRepository.resetPmLockout()
                            withContext(Dispatchers.Main) {
                                isSessionAuthed = false
                                isDecoySession = false
                                Toast.makeText(context, context.getString(R.string.pm_encryption_disabled), Toast.LENGTH_SHORT).show()
                            }
                        }
                        withContext(Dispatchers.Main) {
                            showSettingsDialog = false
                        }
                    }
                }) {
                    Text(stringResource(R.string.pm_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.pm_cancel))
                }
            }
        )
    }
}
