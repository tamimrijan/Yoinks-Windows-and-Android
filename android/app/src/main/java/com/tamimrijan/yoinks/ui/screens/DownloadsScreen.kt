@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.tamimrijan.yoinks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tamimrijan.yoinks.YoinksApp
import com.tamimrijan.yoinks.data.HistoryEntry
import com.tamimrijan.yoinks.ui.components.IconShape
import com.tamimrijan.yoinks.util.Files
import com.tamimrijan.yoinks.util.Format

@Composable
fun DownloadsScreen(onYoinkAgain: (String) -> Unit, onGoHome: () -> Unit, bottomPadding: Dp) {
    val app = YoinksApp.instance
    val context = LocalContext.current
    val all by app.history.items.collectAsState()
    val settings by app.prefs.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = all.filter {
        query.isBlank() || it.title.contains(query, true) || it.platform.contains(query, true) || (it.uploader?.contains(query, true) ?: false)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Downloads", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onClick = { Files.openDownloads(context) }) { Icon(Icons.Rounded.FolderOpen, "Open the Downloads folder") }
                if (all.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Clear the list") }
            }
        }
        if (all.isNotEmpty()) {
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search your yoinks") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    title = if (all.isEmpty()) "Nothing yoinked yet" else "No matches",
                    body = when {
                        all.isNotEmpty() -> "Nothing in your downloads matches \"$query\"."
                        settings.saveHistory -> "Videos you download show up here."
                        else -> "Download history is turned off in Settings."
                    },
                    onGoHome = onGoHome,
                )
            }
        }
        items(shown, key = { it.filePath + it.time }) { e -> DownloadCard(e, onYoinkAgain) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Clear the list?") },
            text = { Text("This only clears the list. The videos and music you downloaded stay on your phone.") },
            confirmButton = { TextButton(onClick = { app.history.clear(); confirmClear = false }) { Text("Clear list") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DownloadCard(e: HistoryEntry, onYoinkAgain: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val app = YoinksApp.instance
    var menu by remember { mutableStateOf(false) }
    val exists = remember(e.filePath) { e.exists }
    val cs = MaterialTheme.colorScheme

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).clickable(enabled = exists) { Files.open(context, e.filePath) },
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(if (e.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Videocam, null, tint = cs.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                AsyncImage(model = e.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Surface(shape = CircleShape, color = if (e.isAudio) cs.tertiaryContainer else cs.primaryContainer, modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    Text(e.choice, style = MaterialTheme.typography.labelMedium, color = if (e.isAudio) cs.onTertiaryContainer else cs.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Row(Modifier.padding(start = 8.dp, top = 12.dp, end = 0.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(e.platform, e.uploader, Format.bytes(e.sizeBytes.toDouble()).ifEmpty { null }, Format.ago(e.time)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!exists) Text("File was moved or deleted", style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
                FilledIconButton(onClick = { Files.open(context, e.filePath) }, enabled = exists, shapes = androidx.compose.material3.IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.PlayArrow, "Play")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, enabled = exists, onClick = { menu = false; Files.share(context, e.filePath) })
                        DropdownMenuItem(text = { Text("Yoink again") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { menu = false; onYoinkAgain(e.url) })
                        DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; clipboard.setText(AnnotatedString(e.url)) })
                        DropdownMenuItem(text = { Text("Open original page") }, leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) }, onClick = { menu = false; Files.openUrl(context, e.url) })
                        DropdownMenuItem(text = { Text("Remove from list") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; app.history.remove(e) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, onGoHome: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconShape(Icons.Rounded.Download, MaterialShapes.Cookie12Sided, cs.primaryContainer, cs.onPrimaryContainer, size = 112.dp)
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onGoHome, shapes = ButtonDefaults.shapes()) { Text("Yoink something") }
        }
    }
}
