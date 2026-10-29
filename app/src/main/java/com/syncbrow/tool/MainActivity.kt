package com.syncbrow.tool

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.syncbrow.tool.data.SettingsRepository
import com.syncbrow.tool.net.LocalDohProxy
import com.syncbrow.tool.ui.theme.MySimpleScannerTheme
import com.syncbrow.tool.ui.MainScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import com.syncbrow.tool.ui.MainViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private var isIncognito: Boolean = false
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(this)
        
        // Handle initial URL from intent
        val initialUrl = intent.dataString ?: intent.getStringExtra("url")
        
        // Observe Theme settings
        lifecycleScope.launch {
            settingsRepository.themeModeFlow.collect { mode ->
                when (mode) {
                    1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
        }
        
        // Track incognito mode state without nuking standard cookies
        lifecycleScope.launch {
            settingsRepository.incognitoEnabledFlow.collect {
                isIncognito = it
            }
        }

        // Manage Local DoH Proxy lifecycle (Option A)
        lifecycleScope.launch {
            combine(settingsRepository.dohEnabledFlow, settingsRepository.dohProviderFlow) { enabled, provider ->
                Pair(enabled, provider)
            }.collect { (enabled, provider) ->
                if (enabled) {
                    LocalDohProxy.start(this@MainActivity, provider)
                } else {
                    LocalDohProxy.stop()
                }
            }
        }

        setContent {
            MySimpleScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(initialUrl = initialUrl, mainViewModel = mainViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = intent?.dataString ?: intent?.getStringExtra("url")
        if (newUrl != null) {
            mainViewModel.onNewIntent(newUrl)
        }
    }

    override fun onDestroy() {
        LocalDohProxy.stop()
        if (isIncognito) {
            // Clean up cookies upon process termination if incognito was enabled
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
        super.onDestroy()
    }
}
