package com.syncbrow.tool.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.util.Log
import android.util.Patterns
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation.NavController
import com.syncbrow.tool.MainActivity
import com.syncbrow.tool.R
import com.syncbrow.tool.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.io.ByteArrayInputStream
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private object BrowserSettingsHolder {
    @Volatile
    var client: OkHttpClient? = null

    @Volatile
    var dohEnabled: Boolean = false
    
    @Volatile
    var adBlockEnabled: Boolean = false
    
    @Volatile
    var trackerBlockEnabled: Boolean = false
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(navController: NavController, initialUrl: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val settingsRepository = remember { SettingsRepository(context) }
    val dohEnabled by settingsRepository.dohEnabledFlow.collectAsState(initial = false)
    val adBlockEnabled by settingsRepository.adBlockEnabledFlow.collectAsState(initial = false)
    val trackerBlockEnabled by settingsRepository.trackerBlockEnabledFlow.collectAsState(initial = false)
    val incognitoEnabled by settingsRepository.incognitoEnabledFlow.collectAsState(initial = false)
    val safeBrowsingEnabled by settingsRepository.safeBrowsingEnabledFlow.collectAsState(initial = true)
    val forceEnableCopyPaste by settingsRepository.forceEnableCopyPasteFlow.collectAsState(initial = false)
    val removeRedirectPromptEnabled by settingsRepository.removeRedirectPromptEnabledFlow.collectAsState(initial = false)
    val quicEnabled by settingsRepository.quicEnabledFlow.collectAsState(initial = false)
    val searchEngine by settingsRepository.searchEngineFlow.collectAsState(initial = 0)
    
    val googleUrl = "https://www.google.com"
    val bingUrl = "https://www.bing.com"
    val yahooUrl = "https://www.yahoo.com"

    val defaultUrl = when(searchEngine) {
        1 -> bingUrl
        2 -> yahooUrl
        else -> googleUrl
    }

    var urlInput by remember { mutableStateOf(initialUrl ?: defaultUrl) }
    var currentDisplayUrl by remember { mutableStateOf(urlInput) }
    var isEditing by remember { mutableStateOf(false) }
    var lastDownloadInfo by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var lastLoadedInitialUrl by remember { mutableStateOf<String?>(null) }
    
    var showShortcutDialog by remember { mutableStateOf(false) }
    var shortcutName by remember { mutableStateOf("") }
    
    // Stable WebView instance that persists across recompositions
    val webView = remember { 
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setSupportMultipleWindows(false) // Keep it simple
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Modern UA
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
            settings.safeBrowsingEnabled = safeBrowsingEnabled
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    // Navigation logic: handle initialUrl
    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            if (initialUrl != lastLoadedInitialUrl) {
                lastLoadedInitialUrl = initialUrl
                Log.d("BrowserScreen", "Initial navigation: $initialUrl")
                webView.loadUrl(initialUrl)
                urlInput = initialUrl
                currentDisplayUrl = initialUrl
            }
        } else {
            if (lastLoadedInitialUrl == null) {
                lastLoadedInitialUrl = defaultUrl
                Log.d("BrowserScreen", "Default navigation: $defaultUrl")
                webView.loadUrl(defaultUrl)
                urlInput = defaultUrl
                currentDisplayUrl = defaultUrl
            }
        }
    }
    val bookmarkList by db.bookmarkDao().getAllBookmarks().collectAsState(initial = emptyList())
    val isBookmarked = remember(currentDisplayUrl, bookmarkList) {
        bookmarkList.any { it.url == currentDisplayUrl }
    }

    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNameInput by remember { mutableStateOf("") }

    var progress by remember { mutableFloatStateOf(0f) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(dohEnabled, adBlockEnabled, trackerBlockEnabled) {
        BrowserSettingsHolder.dohEnabled = dohEnabled
        BrowserSettingsHolder.adBlockEnabled = adBlockEnabled
        BrowserSettingsHolder.trackerBlockEnabled = trackerBlockEnabled
        
        if (dohEnabled) {
            try {
                val bootstrapClient = OkHttpClient.Builder().build()
                val dns = DnsOverHttps.Builder().client(bootstrapClient)
                    .url(
                        okhttp3.HttpUrl.Builder()
                            .scheme("https")
                            .host("146.112.41.2")
                            .addPathSegment("dns-query")
                            .build()
                    )
                    .bootstrapDnsHosts(InetAddress.getByName("146.112.41.2"))
                    .build()
                BrowserSettingsHolder.client = OkHttpClient.Builder()
                    .dns(dns)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
            } catch (e: Exception) {
                Log.e("DoH_Setup", "Failed to initialize DoH client: ${e.message}")
                BrowserSettingsHolder.client = null
                BrowserSettingsHolder.dohEnabled = false
            }
        } else {
            BrowserSettingsHolder.client = null
        }
    }

    fun handleSearchOrNav(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val isUrl = Patterns.WEB_URL.matcher(trimmed).matches()
        val loadUrl = if (isUrl) {
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
        } else {
            val query = URLEncoder.encode(trimmed, "UTF-8")
            when(searchEngine) {
                1 -> "https://www.bing.com/search?q=$query"
                2 -> "https://search.yahoo.com/search?p=$query"
                else -> "https://www.google.com/search?q=$query"
            }
        }
        Log.d("BrowserScreen", "Stable handleSearchOrNav: $loadUrl")
        urlInput = loadUrl
        currentDisplayUrl = loadUrl
        webView.loadUrl(loadUrl)
        webView.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { webView?.goBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { webView?.goForward() }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                }

                TextField(
                    value = if (isEditing) urlInput else currentDisplayUrl,
                    onValueChange = {
                        urlInput = it
                        if (!isEditing) currentDisplayUrl = it
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .onFocusChanged { state ->
                            isEditing = state.isFocused
                            val normalizedUrl = urlInput.trimEnd('/')
                            val isDefault = normalizedUrl == googleUrl || normalizedUrl == bingUrl || normalizedUrl == yahooUrl
                            if (state.isFocused && isDefault) {
                                urlInput = ""
                            }
                        },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.browser_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        if (currentDisplayUrl.startsWith("https://") && !isEditing) {
                            Icon(Icons.Default.Lock, contentDescription = "Secure", modifier = Modifier.size(16.dp))
                        }
                    },
                    trailingIcon = {
                        if (isEditing && urlInput.isNotEmpty()) {
                            IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        } else if (!isEditing) {
                        IconButton(onClick = { webView.reload() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload")
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        isEditing = false
                        focusManager.clearFocus()
                        handleSearchOrNav(urlInput)
                    })
                )

                IconButton(onClick = { 
                    webView.loadUrl(defaultUrl)
                    urlInput = defaultUrl
                    currentDisplayUrl = defaultUrl
                }) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }

                IconButton(onClick = { 
                    bookmarkNameInput = webView.title ?: currentDisplayUrl
                    showBookmarkDialog = true 
                }) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Star else Icons.Outlined.Star,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
        }

        if (progress > 0f && progress < 1f) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { _ ->
                    // Wrap existing stable webView in SwipeRefreshLayout
                    val swipeRefreshLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(context)
                    
                    // Cleanup from any previous parent if necessary
                    (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                    
                    if (quicEnabled) {
                        try {
                            val clClass = Class.forName("org.chromium.base.CommandLine")
                            val getInstance = clClass.getMethod("getInstance")
                            val commandLine = getInstance.invoke(null)
                            val appendSwitch = clClass.getMethod("appendSwitch", String::class.java)
                            appendSwitch.invoke(commandLine, "enable-quic")
                        } catch (_: Exception) {}
                    }

                    swipeRefreshLayout.addView(webView)
                    swipeRefreshLayout.setOnRefreshListener {
                        webView.reload()
                    }

                    webView.webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                        }
                    }

                    webView.setDownloadListener { downloadUrl, _, contentDisposition, mimeType, _ ->
                        val now = System.currentTimeMillis()
                        // De-bounce: prevent duplicate records for the same URL within 2 seconds
                        if (lastDownloadInfo?.first == downloadUrl && now - (lastDownloadInfo?.second ?: 0) < 2000) {
                            Log.d("BrowserScreen", "DownloadListener de-bounced: $downloadUrl")
                            return@setDownloadListener
                        }
                        
                        val filename = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType) ?: "download_file"
                        
                        // Filter out temporary UUID filenames often used by some sites/WebView internals
                        val uuidPattern = "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}.*$".toRegex(RegexOption.IGNORE_CASE)
                        if (uuidPattern.matches(filename) && (mimeType == "application/octet-stream" || mimeType == null)) {
                            Log.d("BrowserScreen", "Ignoring potential garbage UUID filename: $filename")
                            return@setDownloadListener
                        }
                        
                        lastDownloadInfo = downloadUrl to now
                        Log.d("BrowserScreen", "DownloadListener Triggered: $downloadUrl")
                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val localFile = java.io.File(downloadsDir, filename)
                        val localPath = localFile.absolutePath

                        // Proactively save record in a separate scope to survive UI changes
                        scope.launch(Dispatchers.IO) {
                            try {
                                val download = DownloadEntity(
                                    url = downloadUrl,
                                    filename = filename,
                                    timestamp = System.currentTimeMillis(),
                                    localPath = localPath
                                )
                                db.downloadDao().insertDownload(download)
                                Log.d("BrowserScreen", "Download record saved successfully: $filename")
                            } catch (e: Exception) {
                                Log.e("BrowserScreen", "Failed to save download record in DB", e)
                            }
                        }
                        
                        try {
                            val request = android.app.DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                                setTitle(filename)
                                setDescription("Downloading via SyncBrow...")
                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
                                
                                // Crucial: Copy session headers to system downloader
                                val ua = webView.settings.userAgentString
                                addRequestHeader("User-Agent", ua)
                                CookieManager.getInstance().getCookie(downloadUrl)?.let {
                                    addRequestHeader("Cookie", it)
                                }
                                Log.d("BrowserScreen", "Request headers set for DownloadManager")

                                setAllowedOverMetered(true)
                                setAllowedOverRoaming(true)
                            }
                            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            downloadManager.enqueue(request)
                            Toast.makeText(context, "Download started: $filename", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("Download", "DownloadManager failed", e)
                            // Do NOT fallback to ACTION_VIEW for http/https to avoid loops/stray browser opens
                        }
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url ?: return false
                            val urlString = url.toString()
                            val scheme = url.scheme
                            
                            // Proactive download check: if URL ends with common extension, log it but don't record in DB here 
                            // recording should happen ONLY in DownloadListener to avoid duplicates
                            if (isDownloadable(urlString)) {
                                Log.d("BrowserScreen", "Direct file link detected: $urlString")
                            }

                            if (scheme == "http" || scheme == "https") {
                                return false // Handle within WebView
                            }
                            // For other schemes (intent, tel, etc.), return true to block or handle manually
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, url)
                                context.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                true // We "handled" it by failing quietly
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            Log.d("BrowserScreen", "onPageStarted: $url")
                            url?.let {
                                urlInput = it
                                currentDisplayUrl = it
                            }
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            url?.let {
                                urlInput = it
                                currentDisplayUrl = it
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d("BrowserScreen", "onPageFinished: $url")
                            swipeRefreshLayout.isRefreshing = false
                            
                            // Inject addons
                            if (forceEnableCopyPaste) {
                                try {
                                    val inputStream = context.assets.open("addon.user.js")
                                    val jsText = inputStream.bufferedReader().use { it.readText() }
                                    view?.evaluateJavascript(jsText, null)
                                } catch (e: Exception) {
                                    Log.e("BrowserScreen", "Addon failed", e)
                                }
                            }

                            if (removeRedirectPromptEnabled) {
                                try {
                                    val inputStream = context.assets.open("remove.user.js")
                                    val jsText = inputStream.bufferedReader().use { it.readText() }
                                    view?.evaluateJavascript(jsText, null)
                                } catch (e: Exception) {
                                    Log.e("BrowserScreen", "Redirect removal failed", e)
                                }
                            }
                            
                            // Save History
                            url?.let {
                                val title = view?.title ?: it
                                if (!incognitoEnabled) {
                                    scope.launch(Dispatchers.IO) {
                                        db.historyDao().insertHistory(
                                            HistoryEntity(url = it, title = title, timestamp = System.currentTimeMillis())
                                        )
                                    }
                                }
                            }
                            CookieManager.getInstance().flush()
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                Log.e("BrowserScreen", "Load Error: ${error?.errorCode} - ${error?.description}")
                            }
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            if (request == null) return null
                            val urlString = request.url.toString()
                            val host = request.url.host ?: ""

                            val isExempt = host.endsWith("google.com") || host.endsWith("bing.com") || 
                                           host.endsWith("microsoft.com") || host.endsWith("apple.com") ||
                                           host.endsWith("github.com")

                            if (!isExempt && BrowserSettingsHolder.adBlockEnabled && BlockList.shouldBlockAd(host)) {
                                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                            }
                            if (!isExempt && BrowserSettingsHolder.trackerBlockEnabled && BlockList.shouldBlockTracker(host)) {
                                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                            }

                            val isDoHEnabled = BrowserSettingsHolder.dohEnabled
                            val httpClient = BrowserSettingsHolder.client

                            if (!isDoHEnabled || httpClient == null || request.method.lowercase() != "get" || !request.isForMainFrame || !urlString.startsWith("http")) {
                                return super.shouldInterceptRequest(view, request)
                            }

                            try {
                                val okRequestBuilder = Request.Builder().url(urlString)
                                request.requestHeaders?.forEach { (key, value) ->
                                    try { okRequestBuilder.addHeader(key, value) } catch (_: Exception) {}
                                }
                                CookieManager.getInstance().getCookie(urlString)?.let {
                                    okRequestBuilder.addHeader("Cookie", it)
                                }
                                
                                val response = httpClient.newBuilder()
                                    .followRedirects(false)
                                    .followSslRedirects(false)
                                    .build()
                                    .newCall(okRequestBuilder.build()).execute()

                                if (response.isRedirect) {
                                    val location = response.header("Location")
                                    if (!location.isNullOrEmpty()) {
                                        val absoluteUrl = response.request.url.resolve(location)?.toString() ?: location
                                        val headers = response.headers.toMap().toMutableMap()
                                        headers["Location"] = absoluteUrl
                                        val resp = WebResourceResponse("text/html", "UTF-8", response.code, response.message, headers, ByteArrayInputStream(ByteArray(0)))
                                        response.close()
                                        return resp
                                    }
                                }

                                val responseBody = response.body ?: return super.shouldInterceptRequest(view, request)
                                val contentType = responseBody.contentType()
                                val mimeType = contentType?.let { "${it.type}/${it.subtype}" } ?: "text/html"
                                val encoding = contentType?.charset()?.name() ?: "UTF-8"

                                val responseHeaders = response.headers.toMap().filterKeys { 
                                    val k = it.lowercase()
                                    k != "content-type" && k != "content-encoding" && k != "content-length" && k != "transfer-encoding"
                                }.toMutableMap()

                                response.headers("Set-Cookie").forEach { 
                                    CookieManager.getInstance().setCookie(urlString, it)
                                }
                                return WebResourceResponse(mimeType, encoding, response.code, response.message.ifEmpty { "OK" }, responseHeaders, responseBody.byteStream())
                            } catch (e: Exception) {
                                Log.w("DoH_WebView", "Interception failure: ${e.message}")
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    swipeRefreshLayout
                },
                update = { _ ->
                    // Logic here only if UI attributes of Srl/WebView need to change on recomposition
                }
            )

        Box(modifier = Modifier.fillMaxSize()) {
            // Sharing and Shortcut FABs in a Column
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Shortcut FAB
                FloatingActionButton(
                    onClick = {
                        shortcutName = webView.title ?: currentDisplayUrl
                        showShortcutDialog = true
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Shortcut")
                }

                // Share FAB
                FloatingActionButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("URL", currentDisplayUrl)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.share_link_copied), Toast.LENGTH_SHORT).show()

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, currentDisplayUrl)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_title)))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
            }
        }
    }
}

    if (showShortcutDialog) {
        AlertDialog(
            onDismissRequest = { showShortcutDialog = false },
            title = { Text(stringResource(R.string.shortcut_add_title)) },
            text = {
                Column {
                    TextField(
                        value = shortcutName,
                        onValueChange = { shortcutName = it },
                        label = { Text(stringResource(R.string.shortcut_name_label)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    Text(currentDisplayUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    createShortcut(context, currentDisplayUrl, shortcutName)
                    showShortcutDialog = false
                }) {
                    Text(stringResource(R.string.shortcut_add_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showShortcutDialog = false }) {
                    Text(stringResource(R.string.bookmark_cancel))
                }
            }
        )
    }

    if (showBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text(stringResource(R.string.bookmark_add)) },
            text = {
                Column {
                    TextField(
                        value = bookmarkNameInput,
                        onValueChange = { bookmarkNameInput = it },
                        label = { Text(stringResource(R.string.bookmark_name)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    Text(currentDisplayUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        db.bookmarkDao().insertBookmark(BookmarkEntity(name = bookmarkNameInput, url = currentDisplayUrl))
                        showBookmarkDialog = false
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.bookmark_added), Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text(stringResource(R.string.bookmark_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) {
                    Text(stringResource(R.string.bookmark_cancel))
                }
            }
        )
    }
}

fun createShortcut(context: Context, url: String, name: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
    intent.putExtra("url", url)
    
    val shortcut = ShortcutInfoCompat.Builder(context, url)
        .setShortLabel(name)
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()
    
    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        Toast.makeText(context, context.getString(R.string.shortcut_added_toast), Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Shortcut not supported by launcher", Toast.LENGTH_SHORT).show()
    }
}

private fun isDownloadable(url: String): Boolean {
    val extensions = listOf(".apk", ".zip", ".rar", ".7z", ".exe", ".pdf", ".dmg", ".pkg", ".iso", ".bin", ".tar", ".gz")
    val lowerUrl = url.lowercase()
    return extensions.any { lowerUrl.endsWith(it) || lowerUrl.contains("$it?") }
}
