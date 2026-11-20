@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.tamimrijan.yoinks.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tamimrijan.yoinks.YoinksApp
import com.tamimrijan.yoinks.data.ThemeMode
import com.tamimrijan.yoinks.engine.EngineStatus
import com.tamimrijan.yoinks.ui.components.ListRow
import com.tamimrijan.yoinks.ui.components.PageTitle
import com.tamimrijan.yoinks.ui.components.SectionLabel
import com.tamimrijan.yoinks.ui.components.SegmentedGroup
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(bottomPadding: Dp) {
    val app = YoinksApp.instance
    val s by app.prefs.state.collectAsState()
    val version by app.engine.version.collectAsState()
    val status by app.engine.status.collectAsState()
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = bottomPadding + 16.dp)) {
        PageTitle("Settings")

        SectionLabel("Appearance")
        SegmentedGroup {
            row {
                Column(Modifier.padding(16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    // Expressive connected button group
                    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        val options = listOf(
                            Triple(ThemeMode.SYSTEM, "Auto", Icons.Rounded.BrightnessAuto),
                            Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
                            Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
                        )
                        options.forEachIndexed { i, (mode, label, icon) ->
                            ToggleButton(
                                checked = s.theme == mode,
                                onCheckedChange = { app.prefs.update { it.copy(theme = mode) } },
                                shapes = when (i) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                            ) {
                                Icon(icon, null, Modifier.size(ToggleButtonDefaults.IconSize))
                                Spacer(Modifier.width(6.dp))
                                Text(label)
                            }
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                row {
                    ListRow(
                        title = "Material You colours",
                        subtitle = "Match your wallpaper instead of the Yoinks purple",
                        icon = Icons.Rounded.Palette,
                        polygon = MaterialShapes.Flower,
                        onClick = { app.prefs.update { it.copy(dynamicColor = !it.dynamicColor) } },
                    ) { Switch(checked = s.dynamicColor, onCheckedChange = { v -> app.prefs.update { it.copy(dynamicColor = v) } }) }
                }
            }
        }

        SectionLabel("Downloads")
        SegmentedGroup {
            row {
                ListRow(
                    title = "Save to",
                    subtitle = "Download/Yoinks on your phone. Videos show up in Gallery, songs in your music app.",
                    icon = Icons.Rounded.Folder,
                    polygon = MaterialShapes.Cookie7Sided,
                )
            }
            row {
                ListRow(
                    title = "Offer links from the clipboard",
                    subtitle = "Shows a Paste button when you've copied something",
                    icon = Icons.Rounded.ContentPaste,
                    polygon = MaterialShapes.Cookie9Sided,
                    onClick = { app.prefs.update { it.copy(watchClipboard = !it.watchClipboard) } },
                ) { Switch(checked = s.watchClipboard, onCheckedChange = { v -> app.prefs.update { it.copy(watchClipboard = v) } }) }
            }
            row {
                ListRow(
                    title = "Wi-Fi only",
                    subtitle = "Don't download over mobile data",
                    icon = Icons.Rounded.Wifi,
                    polygon = MaterialShapes.Pill,
                    onClick = { app.prefs.update { it.copy(wifiOnly = !it.wifiOnly) } },
                ) { Switch(checked = s.wifiOnly, onCheckedChange = { v -> app.prefs.update { it.copy(wifiOnly = v) } }) }
            }
            row {
                ListRow(
                    title = "Keep a download history",
                    subtitle = "Lists what you yoinked on the Downloads tab. Stays on this phone.",
                    icon = Icons.Rounded.History,
                    polygon = MaterialShapes.Clover8Leaf,
                    onClick = { app.prefs.update { it.copy(saveHistory = !it.saveHistory) } },
                ) { Switch(checked = s.saveHistory, onCheckedChange = { v -> app.prefs.update { it.copy(saveHistory = v) } }) }
            }
        }

        SectionLabel("Sharing")
        SegmentedGroup {
            row {
                ListRow(
                    title = "Quick download",
                    subtitle = "In any app tap Share, hold Yoinks and pick Quick download. Choose Audio or Video, a quality and extras, then keep watching while it downloads.",
                    icon = Icons.Rounded.Bolt,
                    polygon = MaterialShapes.Cookie6Sided,
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContent = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            row {
                ListRow(
                    title = "Ask before quick download",
                    subtitle = if (s.quickAsk) "Shows the Configure before download sheet"
                    else "Downloads straight away as ${s.quick.label}${if (s.quick.audio) "" else " video"} (your last choice)",
                    icon = Icons.Rounded.Tune,
                    polygon = MaterialShapes.Pill,
                    onClick = { app.prefs.update { it.copy(quickAsk = !it.quickAsk) } },
                ) { Switch(checked = s.quickAsk, onCheckedChange = { v -> app.prefs.update { it.copy(quickAsk = v) } }) }
            }
            row {
                ListRow(
                    title = "See every quality",
                    subtitle = "Tap Share, then Yoinks: the full list of qualities with sizes pops up over the app you're in.",
                    icon = Icons.Rounded.Share,
                    polygon = MaterialShapes.Cookie9Sided,
                )
            }
        }

        SectionLabel("Download engine")
        SegmentedGroup {
            row {
                ListRow(
                    title = "yt-dlp",
                    subtitle = when {
                        updateMessage != null -> updateMessage
                        status is EngineStatus.Starting -> "Getting ready…"
                        status is EngineStatus.Failed -> "Didn't start: ${(status as EngineStatus.Failed).message}"
                        else -> "Version ${version ?: "unknown"}. Update it when a site stops working."
                    },
                    icon = Icons.Rounded.SystemUpdate,
                    polygon = MaterialShapes.SoftBurst,
                    iconContainer = cs.tertiaryContainer,
                    iconContent = cs.onTertiaryContainer,
                ) {
                    if (updating) {
                        LoadingIndicator(Modifier.size(40.dp))
                    } else {
                        FilledTonalButton(
                            onClick = {
                                updating = true
                                updateMessage = "Checking for a newer yt-dlp…"
                                scope.launch {
                                    updateMessage = runCatching { app.engine.updateYtDlp() }.getOrElse { "Update failed: ${it.message}" }
                                    updating = false
                                }
                            },
                            enabled = status is EngineStatus.Ready && !app.engine.isBusy,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Update") }
                    }
                }
            }
            row {
                ListRow(
                    title = "Built in",
                    subtitle = "Python, FFmpeg for HD merges and MP3, and QuickJS for YouTube. Nothing else to install.",
                    icon = Icons.Rounded.AutoAwesome,
                    polygon = MaterialShapes.Sunny,
                    iconContainer = cs.secondaryContainer,
                    iconContent = cs.onSecondaryContainer,
                )
            }
        }
    }
}
