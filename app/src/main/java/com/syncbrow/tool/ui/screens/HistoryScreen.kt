package com.syncbrow.tool.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.syncbrow.tool.R
import com.syncbrow.tool.data.AppDatabase
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.syncbrow.tool.data.BookmarkEntity
import com.syncbrow.tool.data.DownloadEntity
import android.content.Intent
import android.net.Uri
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    val historyList by db.historyDao().getAllHistory().collectAsState(initial = emptyList())
    val bookmarkList by db.bookmarkDao().getAllBookmarks().collectAsState(initial = emptyList())
    val downloadList by db.downloadDao().getAllDownloads().collectAsState(initial = emptyList())
    
    var bookmarksExpanded by remember { mutableStateOf(false) }
    var downloadsExpanded by remember { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(true) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var isNavigating by remember { mutableStateOf(false) }
    
    var searchQuery by remember { mutableStateOf("") }
    
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    // Reset navigating flag when we come back to this screen
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNavigating = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Filter history by search query
    val filteredHistoryList = remember(historyList, searchQuery) {
        if (searchQuery.isBlank()) {
            historyList
        } else {
            historyList.filter { 
                it.title.contains(searchQuery, ignoreCase = true) || 
                it.url.contains(searchQuery, ignoreCase = true) 
            }
        }
    }

    // Group by Day (yyyy-MM-dd)
    val groupedHistory = remember(filteredHistoryList) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        filteredHistoryList.groupBy { dateFormat.format(Date(it.timestamp)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                actions = {
                    IconButton(onClick = { scope.launch { db.historyDao().clearAllHistory() } }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_clear_all))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // Bookmarks Section
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.bookmark_title), style = MaterialTheme.typography.titleLarge) },
                        trailingContent = {
                            IconButton(onClick = { bookmarksExpanded = !bookmarksExpanded }) {
                                Icon(
                                    imageVector = if (bookmarksExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (bookmarksExpanded) "Collapse" else "Expand"
                                )
                            }
                        },
                        modifier = Modifier.clickable { bookmarksExpanded = !bookmarksExpanded }
                    )
                }
                
                if (bookmarksExpanded) {
                    if (bookmarkList.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.bookmark_empty),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        items(bookmarkList) { bookmark ->
                            ListItem(
                                headlineContent = { Text(bookmark.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(bookmark.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable {
                                    if (isNavigating) return@clickable
                                    isNavigating = true
                                    val encoded = java.net.URLEncoder.encode(bookmark.url, "UTF-8")
                                    navController.navigate("browser?url=$encoded") {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { editingBookmark = bookmark }) {
                                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.bookmark_edit))
                                        }
                                        IconButton(onClick = { scope.launch { db.bookmarkDao().deleteBookmark(bookmark) } }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.bookmark_delete))
                                        }
                                    }
                                }
                            )
                        }
                    }
                    item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                // Downloads Section
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.download_title), style = MaterialTheme.typography.titleLarge) },
                        trailingContent = {
                            IconButton(onClick = { downloadsExpanded = !downloadsExpanded }) {
                                Icon(
                                    imageVector = if (downloadsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (downloadsExpanded) "Collapse" else "Expand"
                                )
                            }
                        },
                        modifier = Modifier.clickable { downloadsExpanded = !downloadsExpanded }
                    )
                }

                if (downloadsExpanded) {
                    if (downloadList.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.download_empty),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        items(downloadList) { download ->
                            ListItem(
                                headlineContent = { Text(download.filename, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(download.localPath ?: download.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable {
                                    val path = download.localPath
                                    if (path != null) {
                                        val file = java.io.File(path)
                                        if (file.exists()) {
                                            try {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW)
                                                val extension = file.extension.lowercase()
                                                val mimeType = if (extension == "apk") {
                                                    "application/vnd.android.package-archive"
                                                } else {
                                                    context.contentResolver.getType(uri) ?: "*/*"
                                                }
                                                
                                                intent.setDataAndType(uri, mimeType)
                                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                
                                                // If it's an APK, try to launch installer directly
                                                // For other unknown types, use a chooser to avoid browser takeover
                                                val chooserTitle = if (extension == "apk") "Install APK" else "Open File"
                                                context.startActivity(Intent.createChooser(intent, chooserTitle))
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, R.string.download_file_error, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, R.string.download_file_missing, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // Legacy record or no path, try URL
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(download.url))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            android.widget.Toast.makeText(context, R.string.download_file_missing, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = {
                                            try {
                                                val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Cannot open downloads folder", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.Menu, contentDescription = "Show in Folder")
                                        }
                                        IconButton(onClick = { scope.launch { db.downloadDao().deleteDownload(download) } }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.download_delete))
                                        }
                                    }
                                }
                            )
                        }
                    }
                    item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                // History Section
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge) },
                        trailingContent = {
                            IconButton(onClick = { historyExpanded = !historyExpanded }) {
                                Icon(
                                    imageVector = if (historyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (historyExpanded) "Collapse" else "Expand"
                                )
                            }
                        },
                        modifier = Modifier.clickable { historyExpanded = !historyExpanded }
                    )
                }

                if (historyExpanded) {
                    // Search Bar for History
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text(stringResource(R.string.history_search_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    }

                    if (filteredHistoryList.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.history_empty),
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        groupedHistory.forEach { (date, items) ->
                            item {
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            items(items) { history ->
                                ListItem(
                                    headlineContent = { Text(history.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(history.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.clickable {
                                        if (isNavigating) return@clickable
                                        isNavigating = true
                                        val encoded = java.net.URLEncoder.encode(history.url, "UTF-8")
                                        navController.navigate("browser?url=$encoded") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                        }
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { scope.launch { db.historyDao().deleteHistory(history) } }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Single History")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
        }
    }

    // Edit Bookmark Dialog
    editingBookmark?.let { bookmark ->
        var name by remember { mutableStateOf(bookmark.name) }
        var url by remember { mutableStateOf(bookmark.url) }
        
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text(stringResource(R.string.bookmark_edit)) },
            text = {
                Column {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.bookmark_name)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.bookmark_url)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        db.bookmarkDao().updateBookmark(bookmark.copy(name = name, url = url))
                        editingBookmark = null
                    }
                }) {
                    Text(stringResource(R.string.bookmark_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) {
                    Text(stringResource(R.string.bookmark_cancel))
                }
            }
        )
    }
}
