@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tamimrijan.yoinks.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tamimrijan.yoinks.YoinksApp
import com.tamimrijan.yoinks.engine.DownloadChoice
import com.tamimrijan.yoinks.engine.Phase
import com.tamimrijan.yoinks.service.DownloadService
import com.tamimrijan.yoinks.ui.MainActivity
import com.tamimrijan.yoinks.ui.components.IconShape
import com.tamimrijan.yoinks.ui.components.ListRow
import com.tamimrijan.yoinks.ui.components.SegmentedGroup
import com.tamimrijan.yoinks.ui.screens.ClawLogo
import com.tamimrijan.yoinks.ui.theme.YoinksTheme
import com.tamimrijan.yoinks.util.Format
import com.tamimrijan.yoinks.util.Platforms
import kotlinx.coroutines.launch

/**
 * Share → Yoinks: a bottom sheet over whatever app you were in (YouTube, Facebook, Instagram…).
 * Pick a quality or MP3 and it downloads in the background; you never leave the other app.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val url = sharedUrl(intent)
        if (url == null) {
            Toast.makeText(this, "There's no link in what you shared", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val engine = YoinksApp.instance.engine
        // something is already downloading: this link can only join the queue
        val busyAtStart = engine.isBusy
        if (!busyAtStart && savedInstanceState == null) engine.submit(url)

        setContent {
            val settings by YoinksApp.instance.prefs.state.collectAsState()
            YoinksTheme(settings) {
                Sheet(url, busyAtStart, onClose = { finish() }, onStarted = { started() })
            }
        }
    }

    private fun started() {
        DownloadService.start(this)
        Toast.makeText(this, "Yoinking in the background", Toast.LENGTH_SHORT).show()
        finish()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun sharedUrl(intent: Intent?): String? {
            val text = when (intent?.action) {
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                Intent.ACTION_VIEW -> intent.dataString
                else -> null
            }
            return Platforms.extractUrl(text)?.takeIf { Platforms.isProbablyUrl(it) }
        }
    }
}

@Composable
private fun Sheet(url: String, busyAtStart: Boolean, onClose: () -> Unit, onStarted: () -> Unit) {
    val engine = YoinksApp.instance.engine
    val phase by engine.phase.collectAsState()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onClose() } }

    ModalBottomSheet(
        onDismissRequest = {
            // closing the sheet without choosing: stop fetching the info
            if (!busyAtStart && engine.phase.value is Phase.Probing) engine.cancel()
            onClose()
        },
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
        ) {
            Header(url)
            Spacer(Modifier.height(16.dp))
            if (busyAtStart) {
                QueueContent(onQueued = onStarted)
            } else {
                AnimatedContent(phase, contentKey = { it::class }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "sheet") { p ->
                    when (p) {
                        is Phase.Probing, is Phase.Input -> Loading()
                        is Phase.Picking -> PickContent(p) { choice ->
                            engine.download(choice)
                            onStarted()
                        }
                        is Phase.Error -> ErrorContent(p.message, onRetry = { engine.retry() }, onClose = close)
                        else -> Loading()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    onClose()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Yoinks")
                }
                OutlinedButton(onClick = {
                    if (!busyAtStart && engine.phase.value is Phase.Probing) engine.cancel()
                    close()
                }, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun Header(url: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClawLogo(44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Yoink this", style = MaterialTheme.typography.headlineSmall)
            Text(Platforms.detect(url).label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Loading() {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(Modifier.size(80.dp))
        Spacer(Modifier.height(12.dp))
        Text("Fetching video info…", style = MaterialTheme.typography.titleMedium)
        Text("Pick a quality in a moment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PickContent(p: Phase.Picking, onPick: (DownloadChoice) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(124.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Videocam, null, tint = cs.onSurfaceVariant.copy(alpha = 0.4f))
                AsyncImage(model = p.info.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                p.info.duration?.let { Format.duration(it) }?.takeIf { it.isNotEmpty() }?.let { d ->
                    Surface(shape = CircleShape, color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                        Text(d, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.info.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                p.info.uploader?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Pick a quality", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        SegmentedGroup {
            p.choices.forEach { c ->
                row {
                    Box(Modifier.background(cs.surfaceContainerHigh)) {
                        ListRow(
                            title = c.label,
                            subtitle = c.detail,
                            icon = if (c.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Videocam,
                            polygon = if (c.isAudio) MaterialShapes.Sunny else MaterialShapes.Cookie9Sided,
                            iconContainer = if (c.isAudio) cs.tertiaryContainer else cs.primaryContainer,
                            iconContent = if (c.isAudio) cs.onTertiaryContainer else cs.onPrimaryContainer,
                            onClick = { onPick(c) },
                        ) { Icon(Icons.Rounded.Download, "Download ${c.label}", tint = cs.primary) }
                    }
                }
            }
        }
    }
}

/** A download is already running: offer to queue this link with a quick format. */
@Composable
private fun QueueContent(onQueued: () -> Unit) {
    val engine = YoinksApp.instance.engine
    val activity = androidx.compose.ui.platform.LocalContext.current as ShareActivity
    val url = remember { ShareActivity.sharedUrl(activity.intent) ?: "" }
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        IconShape(Icons.Rounded.Bolt, MaterialShapes.Cookie12Sided, cs.secondaryContainer, cs.onSecondaryContainer, size = 72.dp)
        Spacer(Modifier.height(12.dp))
        Text("Yoinks is busy with another download", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text("Add this one to the queue. It starts as soon as the current one finishes.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { engine.quick(url, YoinksApp.instance.prefs.state.value.quick.copy(audio = false)); onQueued() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Icon(Icons.Rounded.Videocam, null)
                Spacer(Modifier.width(8.dp))
                Text("Video")
            }
            FilledTonalButton(onClick = { engine.quick(url, YoinksApp.instance.prefs.state.value.quick.copy(audio = true)); onQueued() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Icon(Icons.Rounded.MusicNote, null)
                Spacer(Modifier.width(8.dp))
                Text("MP3")
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconShape(Icons.Rounded.ErrorOutline, MaterialShapes.Clover4Leaf, cs.errorContainer, cs.onErrorContainer, size = 72.dp)
        Spacer(Modifier.height(12.dp))
        Text("Couldn't yoink that", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Try again") }
            OutlinedButton(onClick = onClose, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Close") }
        }
    }
}

