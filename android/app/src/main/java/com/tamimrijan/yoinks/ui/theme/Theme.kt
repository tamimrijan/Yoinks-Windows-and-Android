@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.tamimrijan.yoinks.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.tamimrijan.yoinks.data.Settings
import com.tamimrijan.yoinks.data.ThemeMode

/*
 * Material 3 Expressive, tuned to the Claw logo:
 * - purple primary, hot-pink tertiary, blue secondary (the play button's gradient)
 * - lavender-tinted surfaces so cards and groups feel warm, never grey
 * - big, round shape scale; spring-based motion (MotionScheme.expressive)
 * - bold "emphasized" type for titles and big numbers
 */

val Pink = Color(0xFFFF375F)
val Purple = Color(0xFFAF52DE)
val Blue = Color(0xFF0A84FF)
val Ink = Color(0xFF17171D)
val BrandGradient = Brush.linearGradient(listOf(Pink, Purple, Blue))

private val Light = lightColorScheme(
    primary = Color(0xFF7A3FD1), onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDCFF), onPrimaryContainer = Color(0xFF29005F),
    secondary = Color(0xFF2D5BB8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E2FF), onSecondaryContainer = Color(0xFF001A43),
    tertiary = Color(0xFFB4235A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E1), onTertiaryContainer = Color(0xFF3F0019),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFEF7FF), onBackground = Color(0xFF1D1A22),
    surface = Color(0xFFFEF7FF), onSurface = Color(0xFF1D1A22),
    surfaceVariant = Color(0xFFE8E0EE), onSurfaceVariant = Color(0xFF4A4552),
    outline = Color(0xFF7B7583), outlineVariant = Color(0xFFCBC4D2),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F1FF),
    surfaceContainer = Color(0xFFF2EAFC), surfaceContainerHigh = Color(0xFFECE4F6),
    surfaceContainerHighest = Color(0xFFE6DEF0),
    inverseSurface = Color(0xFF322F37), inverseOnSurface = Color(0xFFF6EEFA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD5BBFF), onPrimary = Color(0xFF42008D),
    primaryContainer = Color(0xFF5E22B6), onPrimaryContainer = Color(0xFFEBDCFF),
    secondary = Color(0xFFB0C6FF), onSecondary = Color(0xFF002D6C),
    secondaryContainer = Color(0xFF13429E), onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Color(0xFFFFB1C4), onTertiary = Color(0xFF65002D),
    tertiaryContainer = Color(0xFF8F0D44), onTertiaryContainer = Color(0xFFFFD9E1),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF15121A), onBackground = Color(0xFFE8E0EB),
    surface = Color(0xFF15121A), onSurface = Color(0xFFE8E0EB),
    surfaceVariant = Color(0xFF4A4552), onSurfaceVariant = Color(0xFFCBC4D2),
    outline = Color(0xFF958E9C), outlineVariant = Color(0xFF4A4552),
    surfaceContainerLowest = Color(0xFF100D15), surfaceContainerLow = Color(0xFF1D1A23),
    surfaceContainer = Color(0xFF221E27), surfaceContainerHigh = Color(0xFF2C2832),
    surfaceContainerHighest = Color(0xFF37333D),
    inverseSurface = Color(0xFFE8E0EB), inverseOnSurface = Color(0xFF322F37),
)

private val base = Typography()
private val AppTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Big percentage on the download screen: tabular so it doesn't jiggle as it ticks. */
val BigNumberStyle = TextStyle(fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
    largeIncreased = RoundedCornerShape(32.dp),
    extraLargeIncreased = RoundedCornerShape(44.dp),
    extraExtraLarge = RoundedCornerShape(52.dp),
)

@Composable
fun YoinksTheme(settings: Settings = Settings(), content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppTypography,
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
