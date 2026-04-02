package com.syncbrow.tool.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.syncbrow.tool.R
import com.syncbrow.tool.data.AppDatabase
import com.syncbrow.tool.data.PasswordEntity
import com.syncbrow.tool.data.SettingsRepository
import com.syncbrow.tool.util.PasswordUtils
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val settingsRepository = remember { SettingsRepository(context) }

    val passwordDao = remember(db) { db.passwordDao() }
    val passwordFlow = remember(passwordDao) { passwordDao.getAllPasswords() }
    
    val passwordList by passwordFlow.collectAsState(initial = emptyList())
    val encryptionEnabled by settingsRepository.pmEncryptionEnabledFlow.collectAsState(initial = false)
    val realPasswordHash by settingsRepository.pmRealPasswordHashFlow.collectAsState(initial = "")
    val emergencyPasswordHash by settingsRepository.pmEmergencyPasswordHashFlow.collectAsState(initial = "")

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingPassword by remember { mutableStateOf<PasswordEntity?>(null) }
    var selectedPassword by remember { mutableStateOf<PasswordEntity?>(null) }

    // Add/Edit dialog fields
    var inputSite by remember { mutableStateOf("") }
    var inputUsername by remember { mutableStateOf("") }
    var inputPassword by remember { mutableStateOf("") }

    // Settings dialog fields
    var settingsEncryptionEnabled by remember { mutableStateOf(false) }
    var settingsRealPassword by remember { mutableStateOf("") }
    var settingsEmergencyPassword by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPasswords = remember(passwordList, searchQuery) {
        if (searchQuery.isBlank()) {
            passwordList
        } else {
            passwordList.filter { it.siteName.contains(searchQuery, ignoreCase = true) }
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
            if (passwordList.isNotEmpty()) {
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

            if (passwordList.isEmpty()) {
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

    // Detail Dialog
    if (showDetailDialog && selectedPassword != null) {
        val pw = selectedPassword!!
        var showPwText by remember { mutableStateOf(false) }

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
                        value = pw.password,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.pm_password)) },
                        readOnly = true,
                        visualTransformation = if (showPwText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { 
                                showPwText = !showPwText 
                                if (showPwText) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("password", pw.password))
                                    Toast.makeText(context, context.getString(R.string.pm_password_copied), Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(
                                    if (showPwText) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        // Edit
                        editingPassword = pw
                        inputSite = pw.siteName
                        inputUsername = pw.username
                        inputPassword = pw.password
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
                    scope.launch(Dispatchers.IO) {
                        db.passwordDao().deletePassword(passwordToDelete)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.pm_password_deleted), Toast.LENGTH_SHORT).show()
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
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("password", inputPassword))
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
                            text = strengthResult.labelRes,
                            style = MaterialTheme.typography.bodySmall,
                            color = strengthResult.color
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (inputSite.isBlank() || inputUsername.isBlank() || inputPassword.isBlank()) return@TextButton
                    scope.launch(Dispatchers.IO) {
                        if (isEdit) {
                            db.passwordDao().updatePassword(
                                editingPassword!!.copy(
                                    siteName = inputSite.trim(),
                                    username = inputUsername.trim(),
                                    password = inputPassword.trim()
                                )
                            )
                        } else {
                            db.passwordDao().insertPassword(
                                PasswordEntity(
                                    siteName = inputSite.trim(),
                                    username = inputUsername.trim(),
                                    password = inputPassword.trim()
                                )
                            )
                        }
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.pm_password_saved), Toast.LENGTH_SHORT).show()
                        }
                    }
                    showAddEditDialog = false
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
                            settingsRepository.setPmEncryptionEnabled(true)
                            settingsRepository.setPmRealPasswordHash(SettingsRepository.sha256(settingsRealPassword))
                            if (settingsEmergencyPassword.isNotBlank()) {
                                settingsRepository.setPmEmergencyPasswordHash(SettingsRepository.sha256(settingsEmergencyPassword))
                            } else {
                                settingsRepository.setPmEmergencyPasswordHash("")
                            }
                            settingsRepository.resetPmLockout()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.pm_encryption_saved), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            settingsRepository.setPmEncryptionEnabled(false)
                            settingsRepository.setPmRealPasswordHash("")
                            settingsRepository.setPmEmergencyPasswordHash("")
                            settingsRepository.resetPmLockout()
                            withContext(Dispatchers.Main) {
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
