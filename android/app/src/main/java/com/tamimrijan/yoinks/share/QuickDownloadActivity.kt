@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.tamimrijan.yoinks.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamimrijan.yoinks.YoinksApp
import com.tamimrijan.yoinks.engine.QuickOptions
import com.tamimrijan.yoinks.service.DownloadService
import com.tamimrijan.yoinks.ui.screens.ClawLogo
import com.tamimrijan.yoinks.ui.theme.YoinksTheme
import com.tamimrijan.yoinks.util.Platforms
import kotlinx.coroutines.launch

/**
 * Share → Quick download. Shows "Configure before download" (Audio or Video, quality, extras)
 * straight away, without waiting for the video's info, then downloads in the background while
 * you stay in the other app. With "Ask before quick download" off in Settings it skips the sheet.
 */
class QuickDownloadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = YoinksApp.instance
        val url = ShareActivity.sharedUrl(intent)
        if (url == null) {
            Toast.makeText(this, "There's no link in what you shared", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (!app.prefs.state.value.quickAsk) {
            go(url, app.prefs.state.value.quick)
            return
        }
        setContent {
            val settings by app.prefs.state.collectAsState()
            YoinksTheme(settings) {
                ConfigureSheet(
                    url = url,
                    initial = settings.quick,
                    onDownload = { go(url, it) },
                    onPickExact = {
                        // the full quality list: hand the same share over to the Yoinks sheet
                        startActivity(Intent(intent).setClass(this, ShareActivity::class.java))
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun go(url: String, options: QuickOptions) {
        val app = YoinksApp.instance
        app.prefs.update { it.copy(quick = options) } // remembered for next time
        val wasBusy = app.engine.isBusy
        app.engine.quick(url, options)
        // must start while this activity is in front: Android blocks it from the background
        DownloadService.start(this)
        Toast.makeText(
            this,
            if (wasBusy) "Added to the queue (${options.label})" else "Yoinking ${options.label} in the background",
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

@Composable
private fun ConfigureSheet(
    url: String,
    initial: QuickOptions,
    onDownload: (QuickOptions) -> Unit,
    onPickExact: () -> Unit,
    onClose: () -> Unit,
) {
    var o by remember { mutableStateOf(initial) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val hide: (() -> Unit) -> Unit = { then -> scope.launch { state.hide() }.invokeOnCompletion { then() } }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = state,
        containerColor = cs.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp).navigationBarsPadding(),
        ) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClawLogo(44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Configure before download", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${Platforms.detect(url).label} · $url",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Label("Download type")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                Toggle("Audio", Icons.Rounded.MusicNote, o.audio, ButtonGroupDefaults.connectedLeadingButtonShapes(), Modifier.weight(1f)) { o = o.copy(audio = true) }
                Toggle("Video", Icons.Rounded.Videocam, !o.audio, ButtonGroupDefaults.connectedTrailingButtonShapes(), Modifier.weight(1f)) { o = o.copy(audio = false) }
            }

            AnimatedVisibility(!o.audio) {
                Column {
                    Label("Video quality")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickOptions.HEIGHTS.forEach { h ->
                            Chip(if (h == 0) "Best" else "${h}p", o.maxHeight == h, if (h == 0) Icons.Rounded.HighQuality else null) { o = o.copy(maxHeight = h) }
                        }
                    }
                    Text(
                        if (o.maxHeight == 0) "The highest quality the site has. Can be big." else "Up to ${o.maxHeight}p, or the closest below it. Saved as MP4.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
            }
            AnimatedVisibility(o.audio) {
                Column {
                    Label("Audio format")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("MP3", o.mp3, null) { o = o.copy(mp3 = true) }
                        Chip("M4A (original)", !o.mp3, null) { o = o.copy(mp3 = false) }
                    }
                    Text(
                        if (o.mp3) "Plays everywhere. Converted at the best quality." else "The original audio, no conversion. Smaller and faster.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
            }

            Label("Extras")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!o.audio) Chip("Embed subtitles", o.subtitles, Icons.Rounded.ClosedCaption) { o = o.copy(subtitles = !o.subtitles) }
                Chip("Embed thumbnail", o.thumbnail, Icons.Rounded.Image) { o = o.copy(thumbnail = !o.thumbnail) }
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { hide(onClose) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
                Button(onClick = { onDownload(o) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1.4f).heightIn(min = 56.dp)) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download ${o.label}", style = MaterialTheme.typography.titleSmall)
                }
            }
            TextButton(onClick = onPickExact, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("See every quality and size")
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun Toggle(label: String, icon: ImageVector, checked: Boolean, shapes: androidx.compose.material3.ToggleButtonShapes, modifier: Modifier, onClick: () -> Unit) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = shapes,
        modifier = modifier.heightIn(min = 52.dp).semantics { role = Role.RadioButton },
    ) {
        Icon(if (checked) Icons.Rounded.Check else icon, null, Modifier.size(ToggleButtonDefaults.IconSize))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, icon: ImageVector?, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = when {
            selected -> ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) })
            icon != null -> ({ Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) })
            else -> null
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.heightIn(min = 40.dp),
    )
}
