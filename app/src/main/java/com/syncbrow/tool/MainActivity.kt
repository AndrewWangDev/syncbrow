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
import com.syncbrow.tool.ui.theme.MySimpleScannerTheme
import com.syncbrow.tool.ui.MainScreen
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.syncbrow.tool.ui.MainViewModel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private var isIncognito: Boolean = false
    private var initialUrlState by mutableStateOf<String?>(null)
    private val mainViewModel = MainViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(this)
        
        // Handle initial URL from intent
        initialUrlState = intent.dataString ?: intent.getStringExtra("url")
        
        // Observe Theme and Incognito settings
        lifecycleScope.launch {
            settingsRepository.themeModeFlow.collect { mode ->
                when (mode) {
                    1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
        }
        
        lifecycleScope.launch {
            settingsRepository.incognitoEnabledFlow.collect {
                isIncognito = it
            }
        }

        setContent {
            MySimpleScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(initialUrl = initialUrlState, mainViewModel = mainViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = intent?.dataString ?: intent?.getStringExtra("url")
        if (newUrl != null) {
            initialUrlState = newUrl
            mainViewModel.onNewIntent(newUrl)
        }
    }

    override fun onDestroy() {
        if (isIncognito) {
            // "Exit before clear cookies" - this runs when activity is destroyed.
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
        super.onDestroy()
    }
}
