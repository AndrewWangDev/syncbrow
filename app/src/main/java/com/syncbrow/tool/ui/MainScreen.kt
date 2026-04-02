package com.syncbrow.tool.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import com.syncbrow.tool.ui.screens.AboutScreen
import com.syncbrow.tool.ui.screens.BrowserScreen
import com.syncbrow.tool.ui.screens.HistoryScreen
import com.syncbrow.tool.ui.screens.ScanScreen
import com.syncbrow.tool.ui.screens.SettingsScreen
import com.syncbrow.tool.ui.screens.PasswordManagerScreen
import com.syncbrow.tool.data.SettingsRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val titleRes: Int, val icon: ImageVector) {
    object Scan : BottomNavItem("scan", com.syncbrow.tool.R.string.nav_scan, Icons.Default.Search)
    object Browser : BottomNavItem("browser", com.syncbrow.tool.R.string.nav_browser, Icons.Default.Home)
    object History : BottomNavItem("history", com.syncbrow.tool.R.string.nav_history, Icons.Default.List)
    object Settings : BottomNavItem("settings", com.syncbrow.tool.R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen(initialUrl: String? = null, mainViewModel: com.syncbrow.tool.ui.MainViewModel? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }
    val termsAccepted by settingsRepository.termsAcceptedFlow.collectAsState(initial = true)
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val navController = rememberNavController()

    LaunchedEffect(termsAccepted) {
        if (!termsAccepted) {
            showPrivacyDialog = true
        }
    }

    // Helper to handle navigation to a URL
    suspend fun handleNavIntent(url: String) {
        val startTime = System.currentTimeMillis()
        val targetRoutePattern = "${BottomNavItem.Browser.route}?url={url}"
        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
        val targetRoute = "${BottomNavItem.Browser.route}?url=$encodedUrl"

        while (System.currentTimeMillis() - startTime < 5000) {
            try {
                navController.navigate(targetRoute) {
                    navController.graph.findStartDestination().id.let { id ->
                        popUpTo(id) { saveState = true }
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                
                // Once navigate is called, verify we are at least on the Browser tab
                if (navController.currentDestination?.route == targetRoutePattern) {
                    break
                }
                kotlinx.coroutines.delay(100)
            } catch (e: Exception) {
                kotlinx.coroutines.delay(200)
            }
        }
    }

    // 1. Initial startup URL
    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            handleNavIntent(initialUrl)
        }
    }

    // 2. Subsequent new intents while the app is alive
    LaunchedEffect(mainViewModel) {
        mainViewModel?.navEvent?.collect { url ->
            handleNavIntent(url)
        }
    }
    
    val items = listOf(
        BottomNavItem.Scan,
        BottomNavItem.Browser,
        BottomNavItem.History,
        BottomNavItem.Settings
    )

    val startRoute = if (initialUrl != null) {
        val encodedUrl = java.net.URLEncoder.encode(initialUrl, "UTF-8")
        "${BottomNavItem.Browser.route}?url=$encodedUrl"
    } else {
        BottomNavItem.Scan.route
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(androidx.compose.ui.res.stringResource(item.titleRes)) },
                        selected = currentRoute == item.route || (currentRoute?.startsWith(item.route) == true),
                        onClick = {
                            try {
                                navController.navigate(item.route) {
                                    navController.graph.findStartDestination().id.let { id ->
                                        popUpTo(id) { saveState = true }
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) {
                                navController.navigate(item.route)
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Scan.route) {
                ScanScreen(navController)
            }
            composable(
                route = "${BottomNavItem.Browser.route}?url={url}",
                arguments = listOf(androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null })
            ) { backStackEntry ->
                val urlString = backStackEntry.arguments?.getString("url")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                BrowserScreen(navController, urlString)
            }
            composable(BottomNavItem.History.route) {
                HistoryScreen(navController)
            }
            composable(BottomNavItem.Settings.route) {
                SettingsScreen(navController)
            }
            composable("about") {
                AboutScreen(navController)
            }
            composable("password_manager") {
                PasswordManagerScreen(navController)
            }
        }
    }

    if (showPrivacyDialog) {
        var isChecked by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { },
            title = { Text(androidx.compose.ui.res.stringResource(com.syncbrow.tool.R.string.privacy_dialog_title)) },
            text = {
                Column {
                    Text(androidx.compose.ui.res.stringResource(com.syncbrow.tool.R.string.privacy_dialog_content))
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.andrewwangdev.com/terms"))
                            context.startActivity(intent)
                        }) {
                            Text(androidx.compose.ui.res.stringResource(com.syncbrow.tool.R.string.about_terms))
                        }
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.andrewwangdev.com/privacy"))
                            context.startActivity(intent)
                        }) {
                            Text(androidx.compose.ui.res.stringResource(com.syncbrow.tool.R.string.about_privacy))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = isChecked, onCheckedChange = { isChecked = it })
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.syncbrow.tool.R.string.privacy_dialog_check),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = isChecked,
                    onClick = {
                        scope.launch {
                            settingsRepository.setTermsAccepted(true)
                            showPrivacyDialog = false
                        }
                    }
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.syncbrow.tool.R.string.privacy_dialog_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) {
                    Text(androidx.compose.ui.res.stringResource(com.syncbrow.tool.R.string.privacy_dialog_decline))
                }
            }
        )
    }
}
