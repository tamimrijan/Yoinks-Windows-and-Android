@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.tamimrijan.yoinks.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tamimrijan.yoinks.YoinksApp
import com.tamimrijan.yoinks.engine.Phase
import com.tamimrijan.yoinks.ui.components.FloatingNav
import com.tamimrijan.yoinks.ui.components.NavItem
import com.tamimrijan.yoinks.ui.screens.AboutScreen
import com.tamimrijan.yoinks.ui.screens.DownloadsScreen
import com.tamimrijan.yoinks.ui.screens.HomeScreen
import com.tamimrijan.yoinks.ui.screens.SettingsScreen
import com.tamimrijan.yoinks.ui.theme.YoinksTheme
import com.tamimrijan.yoinks.util.Platforms
import kotlinx.coroutines.flow.MutableStateFlow

val tabs = listOf(
    NavItem("home", "Yoink", Icons.Outlined.Download, Icons.Rounded.Download),
    NavItem("downloads", "Downloads", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
    NavItem("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
    NavItem("about", "About", Icons.Outlined.Info, Icons.Rounded.Info),
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by YoinksApp.instance.prefs.state.collectAsState()
            YoinksTheme(settings) { App() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() { super.onStart(); isVisible = true }
    override fun onStop() { super.onStop(); isVisible = false }

    /** Links shared from other apps (Share → Yoinks) or yoinks: links land on the Yoink tab. */
    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val url = Platforms.extractUrl(text) ?: return
        val engine = YoinksApp.instance.engine
        if (engine.isBusy) return // don't interrupt a running yoink
        engine.submit(url)
        openTab.value = "home"
    }

    companion object {
        @Volatile var isVisible = false
        val openTab = MutableStateFlow<String?>(null)
    }
}

@Composable
private fun App() {
    var tab by rememberSaveable { mutableStateOf("home") }
    val requested by MainActivity.openTab.collectAsState()
    LaunchedEffect(requested) {
        requested?.let { tab = it; MainActivity.openTab.value = null }
    }
    Permissions()
    BackHandler(enabled = tab != "home") { tab = "home" }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        val navSpace = 96.dp
        AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
            when (t) {
                "downloads" -> DownloadsScreen(
                    onYoinkAgain = { url -> YoinksApp.instance.engine.submit(url); tab = "home" },
                    onGoHome = { tab = "home" },
                    bottomPadding = navSpace,
                )
                "settings" -> SettingsScreen(navSpace)
                "about" -> AboutScreen(navSpace)
                else -> HomeScreen(onSeeAll = { tab = "downloads" }, bottomPadding = navSpace)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            FloatingNav(tabs, tab) { tab = it }
        }
    }
}

/** Asks for notifications the first time a download starts (and storage on Android 9 and older). */
@Composable
private fun Permissions() {
    val context = LocalContext.current
    val phase by YoinksApp.instance.engine.phase.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(phase is Phase.Picking || phase is Phase.Downloading) {
        if (asked || (phase !is Phase.Picking && phase !is Phase.Downloading)) return@LaunchedEffect
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        asked = true
        if (wanted.isNotEmpty() && !com.tamimrijan.yoinks.YoinksApp.isRobolectric) launcher.launch(wanted.toTypedArray())
    }
}
