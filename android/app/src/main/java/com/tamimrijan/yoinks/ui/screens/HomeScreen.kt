@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.tamimrijan.yoinks.ui.screens

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.tamimrijan.yoinks.R
import com.tamimrijan.yoinks.YoinksApp
import com.tamimrijan.yoinks.data.HistoryEntry
import com.tamimrijan.yoinks.engine.DownloadChoice
import com.tamimrijan.yoinks.engine.EngineStatus
import com.tamimrijan.yoinks.engine.Phase
import com.tamimrijan.yoinks.ui.components.IconShape
import com.tamimrijan.yoinks.ui.components.ListRow
import com.tamimrijan.yoinks.ui.components.PixelWordmark
import com.tamimrijan.yoinks.ui.components.SegmentedGroup
import com.tamimrijan.yoinks.ui.components.ShapeBadge
import com.tamimrijan.yoinks.ui.theme.BigNumberStyle
import com.tamimrijan.yoinks.ui.theme.BrandGradient
import com.tamimrijan.yoinks.util.Files
import com.tamimrijan.yoinks.util.Format

@Composable
fun HomeScreen(onSeeAll: () -> Unit, bottomPadding: androidx.compose.ui.unit.Dp) {
    val app = YoinksApp.instance
    val phase by app.engine.phase.collectAsState()
    val status by app.engine.status.collectAsState()
    val recent by app.history.items.collectAsState()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = bottomPadding),
    ) {
        Hero()

        if (status is EngineStatus.Starting && phase is Phase.Input) EngineWarmup()
        (status as? EngineStatus.Failed)?.let { EngineFailed(it.message) }

        // the sheet everything happens in; content morphs between phases
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AnimatedContent(
                targetState = phase,
                contentKey = { it::class },
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith fadeOut() },
                label = "phase",
            ) { p ->
                Box(Modifier.padding(20.dp)) {
                    when (p) {
                        is Phase.Input -> InputPanel(p)
                        is Phase.Probing -> ProbingPanel(p)
                        is Phase.Picking -> PickingPanel(p)
                        is Phase.Downloading -> DownloadingPanel(p)
                        is Phase.Done -> DonePanel(p.entry)
                        is Phase.Error -> ErrorPanel(p)
                    }
                }
            }
        }

        if (phase is Phase.Input) {
            if (recent.isNotEmpty()) RecentList(recent.take(3), onSeeAll)
            ShareTip()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Hero() {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PixelWordmark(cell = 13.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "yoink any video.\npaste. yoink. done.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("YouTube", "X", "Instagram", "Threads", "TikTok", "+1800 more").forEach {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 1. paste a link

private fun clipboardHasText(context: Context): Boolean {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
    // reading only the description doesn't trigger Android's "pasted from clipboard" toast
    val d = cm.primaryClipDescription ?: return false
    return cm.hasPrimaryClip() && (d.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || d.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML))
}

private fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
    return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}

@Composable
private fun InputPanel(p: Phase.Input) {
    val app = YoinksApp.instance
    val context = LocalContext.current
    val settings by app.prefs.state.collectAsState()
    var text by rememberSaveable { mutableStateOf(app.engine.lastUrl) }
    var hasClip by remember { mutableStateOf(false) }

    // check the clipboard each time the app comes back to the front
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner, settings.watchClipboard) {
        hasClip = settings.watchClipboard && clipboardHasText(context)
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) hasClip = settings.watchClipboard && clipboardHasText(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Paste a link", style = MaterialTheme.typography.titleLarge)
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("https://youtube.com/watch?v=…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Link, null) },
            trailingIcon = {
                if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, "Clear") }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { app.engine.submit(text) }),
            modifier = Modifier.fillMaxWidth().testTag("url"),
        )

        if (p.warning != null) {
            Text(p.warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasClip) {
                FilledTonalButton(
                    onClick = {
                        val clip = readClipboard(context)
                        if (!clip.isNullOrBlank()) {
                            text = clip.trim()
                            app.engine.submit(clip)
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 64.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp),
                ) {
                    Icon(Icons.Rounded.ContentPaste, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Paste")
                }
            }
            Button(
                onClick = { app.engine.submit(text) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("yoink"),
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(10.dp))
                Text("Yoink", style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            "Saves to Download/Yoinks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

// ------------------------------------------------------------------ 2. probing

@Composable
private fun ProbingPanel(p: Phase.Probing) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        PlatformChip(p.platform.label)
        Spacer(Modifier.height(10.dp))
        Text(p.status, style = MaterialTheme.typography.titleLarge)
        Text(p.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { YoinksApp.instance.engine.cancel() }, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
    }
}

@Composable
private fun PlatformChip(label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

// ------------------------------------------------------------------ 3. pick a quality

@Composable
private fun Thumbnail(url: String?, duration: Double?, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Videocam, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val d = duration?.let { Format.duration(it) }.orEmpty()
        if (d.isNotEmpty()) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                Text(d, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun PickingPanel(p: Phase.Picking) {
    val engine = YoinksApp.instance.engine
    Column {
        Thumbnail(p.info.thumbnail, p.info.duration)
        Spacer(Modifier.height(14.dp))
        Text(p.info.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlatformChip(p.platform.label)
            p.info.uploader?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pick a quality", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { engine.reset() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }
        }
        Spacer(Modifier.height(8.dp))
        SegmentedGroup {
            p.choices.forEach { c -> row { ChoiceRow(c) { engine.download(c) } } }
        }
    }
}

@Composable
private fun ChoiceRow(c: DownloadChoice, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.background(cs.surfaceContainerHigh)) {
        ListRow(
            title = c.label,
            subtitle = c.detail,
            icon = if (c.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Videocam,
            polygon = if (c.isAudio) MaterialShapes.Sunny else MaterialShapes.Cookie9Sided,
            iconContainer = if (c.isAudio) cs.tertiaryContainer else cs.primaryContainer,
            iconContent = if (c.isAudio) cs.onTertiaryContainer else cs.onPrimaryContainer,
            onClick = onClick,
        ) {
            Icon(Icons.Rounded.Download, "Download ${c.label}", tint = cs.primary)
        }
    }
}

// ------------------------------------------------------------------ 4. downloading

@Composable
private fun DownloadingPanel(p: Phase.Downloading) {
    val engine = YoinksApp.instance.engine
    val pr = p.progress
    val overall = pr?.fraction?.let { (pr.part + it) / pr.totalParts }
    val shown by animateFloatAsState(overall ?: 0f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "progress")
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
            if (overall != null && !p.processing) {
                CircularWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxSize(), color = cs.primary, trackColor = cs.primaryContainer)
            } else {
                CircularWavyProgressIndicator(modifier = Modifier.fillMaxSize(), color = cs.tertiary, trackColor = cs.tertiaryContainer)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        p.processing -> "100%"
                        overall != null -> "${(shown * 100).toInt()}%"
                        else -> "…"
                    },
                    style = BigNumberStyle,
                )
                if (pr != null && pr.totalParts > 1 && !p.processing) {
                    Text("part ${pr.part + 1} of ${pr.totalParts}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(p.info.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${p.choice.label} · ${p.choice.detail}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        val meta = when {
            p.note != null -> p.note
            p.processing -> if (p.choice.isAudio) "converting to MP3…" else "merging video and audio…"
            p.refreshing -> "link expired, grabbing a fresh one…"
            pr == null -> "starting download…"
            else -> listOfNotNull(
                pr.totalBytes?.let { "${Format.bytes(pr.downloadedBytes)} of ${Format.bytes(it)}" },
                pr.speed?.let { Format.speed(it) }?.takeIf { it.isNotEmpty() },
                pr.etaSeconds?.takeIf { it > 0 }?.let { "${Format.duration(it.toDouble())} left" },
            ).joinToString("  ·  ")
        }
        Surface(shape = CircleShape, color = cs.surfaceContainerHighest) {
            Text(meta, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(18.dp))
        FilledTonalButton(onClick = { engine.cancel() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.Rounded.Close, null)
            Spacer(Modifier.width(8.dp))
            Text("Cancel")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "You can leave the app. The download keeps going in your notifications.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ------------------------------------------------------------------ 5. done

@Composable
private fun DonePanel(e: HistoryEntry) {
    val context = LocalContext.current
    val engine = YoinksApp.instance.engine
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "angle")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }.clip(MaterialShapes.Sunny.toShape()).background(BrandGradient))
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Yoinked!", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(6.dp))
        Text(java.io.File(e.filePath).name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(Format.bytes(e.sizeBytes.toDouble()), "Download/Yoinks").filter { it.isNotEmpty() }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { Files.open(context, e.filePath) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Play", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { Files.share(context, e.filePath) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Icon(Icons.Rounded.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
            OutlinedButton(onClick = { engine.reset() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text("Yoink another")
            }
        }
    }
}

// ------------------------------------------------------------------ 6. error

@Composable
private fun ErrorPanel(p: Phase.Error) {
    val engine = YoinksApp.instance.engine
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        IconShape(Icons.Rounded.ErrorOutline, MaterialShapes.Clover4Leaf, cs.errorContainer, cs.onErrorContainer, size = 96.dp)
        Spacer(Modifier.height(16.dp))
        Text("Couldn't yoink that", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(p.message, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { engine.retry() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Try again")
            }
            OutlinedButton(
                onClick = { if (p.canRetryDownload) engine.backToPicker() else engine.reset() },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text("Back") }
        }
    }
}

// ------------------------------------------------------------------ extras

@Composable
private fun EngineWarmup() {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text("Getting the download engine ready. This only takes long the first time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun EngineFailed(message: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("The download engine didn't start", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun RecentList(items: List<HistoryEntry>, onSeeAll: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Recent yoinks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onSeeAll) { Text("See all") }
    }
    SegmentedGroup {
        items.forEach { e ->
            row {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        AsyncImage(model = e.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${e.platform} · ${e.choice}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { Files.open(context, e.filePath) }) { Icon(Icons.Rounded.PlayArrow, "Play ${e.title}") }
                }
            }
        }
    }
}

@Composable
private fun ShareTip() {
    val cs = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.tertiaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(MaterialShapes.Cookie6Sided, cs.tertiary, size = 52.dp) {
                Icon(Icons.Rounded.Share, null, tint = cs.onTertiary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Even faster: share to Yoinks", style = MaterialTheme.typography.titleMedium, color = cs.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
                Text(
                    "In YouTube, Facebook, Instagram or TikTok tap Share → Yoinks to pick a quality, or hold Yoinks → Quick download.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
fun ClawLogo(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = modifier.size(size))
}
