@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.tamimrijan.yoinks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tamimrijan.yoinks.BuildConfig
import com.tamimrijan.yoinks.ui.components.ListRow
import com.tamimrijan.yoinks.ui.components.PageTitle
import com.tamimrijan.yoinks.ui.components.SectionLabel
import com.tamimrijan.yoinks.ui.components.SegmentedGroup
import com.tamimrijan.yoinks.ui.theme.BrandGradient
import com.tamimrijan.yoinks.util.Files

private const val GITHUB = "https://github.com/tamimrijan"
private const val INSTAGRAM = "https://www.instagram.com/tamim_ahmed_rijan/"
private const val REPO = "https://github.com/tamimrijan/yoinks-windows"

@Composable
fun AboutScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = bottomPadding + 16.dp)) {
        PageTitle("About")
        Spacer(Modifier.height(16.dp))

        // app banner: the brand gradient with a frosted card on top
        Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(BrandGradient)) {
            Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(92.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { ClawLogo(72.dp) }
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("Yoinks", style = MaterialTheme.typography.displaySmall, color = Color.White)
                    Text("yoink any video. paste. yoink. done.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Version ${BuildConfig.VERSION_NAME.substringBefore('-')}")
                        Chip("Android")
                    }
                }
            }
        }

        SectionLabel("Made by")
        Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(128.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(BrandGradient), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(118.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(56.dp))
                        AsyncImage(model = "https://github.com/tamimrijan.png?size=240", contentDescription = "Tamim Ahmed Rijan", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Tamim Ahmed Rijan", style = MaterialTheme.typography.headlineSmall)
                Text("Developer of Yoinks", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("made with ", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Icon(Icons.Rounded.Favorite, "love", tint = Color(0xFFFF375F), modifier = Modifier.size(16.dp))
                    Text(" and way too much caffeine", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { Files.openUrl(context, GITHUB) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Code, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("GitHub")
                    }
                    FilledTonalButton(onClick = { Files.openUrl(context, INSTAGRAM) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Instagram")
                    }
                }
            }
        }

        SectionLabel("Find me")
        SegmentedGroup {
            row {
                ListRow("GitHub", "github.com/tamimrijan", Icons.Rounded.Code, MaterialShapes.Cookie6Sided, Color(0xFF24292F), Color.White, onClick = { Files.openUrl(context, GITHUB) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open", tint = cs.onSurfaceVariant)
                }
            }
            row {
                ListRow("Instagram", "@tamim_ahmed_rijan", Icons.Rounded.PhotoCamera, MaterialShapes.Cookie6Sided, Color(0xFFD62976), Color.White, onClick = { Files.openUrl(context, INSTAGRAM) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open", tint = cs.onSurfaceVariant)
                }
            }
            row {
                ListRow("Source code and updates", "github.com/tamimrijan/yoinks-windows", Icons.Rounded.Favorite, MaterialShapes.Heart, cs.primaryContainer, cs.onPrimaryContainer, onClick = { Files.openUrl(context, REPO) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open", tint = cs.onSurfaceVariant)
                }
            }
        }

        SectionLabel("Credits")
        Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Based on yoinks by Pablo Stanley (MIT), the terminal app this is built from. The pixel wordmark is his.", style = MaterialTheme.typography.bodyMedium)
                Text("Downloads by yt-dlp, wrapped for Android by youtubedl-android. FFmpeg for merging and MP3, QuickJS for YouTube.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Yoinks is a personal-archiving tool: only download what you have the right to keep, and be excellent to creators.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
