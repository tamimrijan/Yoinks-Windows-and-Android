@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.tamimrijan.yoinks.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.tamimrijan.yoinks.ui.theme.Blue
import com.tamimrijan.yoinks.ui.theme.Pink
import com.tamimrijan.yoinks.ui.theme.Purple

/**
 * The yoinks pixel wordmark (from the original project's logo), as rounded tiles with the
 * brand gradient running through the whole word. '+' tiles are the shaded half-tone cells.
 */
private val WORDMARK = listOf(
    "+.+.###.###.##.#.#.#.###",
    "+.+.#.#..#..#.##.#.#.#..",
    "###.#.+..+..#..+.+#..##+",
    ".#..#.+..+..#..+.+.#...+",
    ".#..###.###.#..#.#.#.###",
)

@Composable
fun PixelWordmark(modifier: Modifier = Modifier, cell: Dp = 12.dp) {
    val cols = WORDMARK[0].length
    Canvas(modifier.size(cell * cols, cell * WORDMARK.size).semantics { contentDescription = "Yoinks" }) {
        val s = size.width / cols
        val gap = s * 0.14f
        for (r in WORDMARK.indices) for (c in 0 until cols) {
            val ch = WORDMARK[r][c]
            if (ch == '.') continue
            val t = (c + r * 0.6f) / (cols - 1 + WORDMARK.size * 0.6f)
            val color = if (t < 0.5f) lerp(Pink, Purple, t / 0.5f) else lerp(Purple, Blue, (t - 0.5f) / 0.5f)
            drawRoundRect(
                color = color.copy(alpha = if (ch == '+') 0.38f else 1f),
                topLeft = Offset(c * s, r * s),
                size = Size(s - gap, s - gap),
                cornerRadius = CornerRadius(s * 0.24f),
            )
        }
    }
}

/** Content inside one of the Material 3 Expressive shapes (cookie, clover, sunny…). */
@Composable
fun ShapeBadge(
    polygon: RoundedPolygon,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.size(size).clip(polygon.toShape()).background(container), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun IconShape(icon: ImageVector, polygon: RoundedPolygon, container: Color, content: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) =
    ShapeBadge(polygon, container, modifier, size) { Icon(icon, null, tint = content, modifier = Modifier.size(size * 0.48f)) }

/**
 * Expressive segmented list: rows split by a small gap instead of a divider, with big rounded
 * outer corners and tight inner ones (like Android 16 Settings).
 */
@Composable
fun SegmentedGroup(modifier: Modifier = Modifier, content: SegmentScope.() -> Unit) {
    val rows = SegmentScope().apply(content).rows
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEachIndexed { i, row ->
            val top = if (i == 0) 24.dp else 6.dp
            val bottom = if (i == rows.lastIndex) 24.dp else 6.dp
            Surface(
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) { row() }
        }
    }
}

class SegmentScope {
    val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) { rows += content }
}

/** One row of a [SegmentedGroup]: tonal shape icon, title, subtitle, optional trailing control. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    polygon: RoundedPolygon = androidx.compose.material3.MaterialShapes.Cookie9Sided,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconContent: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconShape(icon, polygon, iconContainer, iconContent, size = 42.dp)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp),
    )
}

data class NavItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Material 3 Expressive floating toolbar navigation: a pill floating above the bottom edge.
 * The selected tab grows into a filled pill with its label; the others are icons only.
 */
@Composable
fun FloatingNav(items: List<NavItem>, selected: String, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = cs.surfaceContainerHighest, shadowElevation = 8.dp, tonalElevation = 2.dp) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { item ->
                    val on = item.route == selected
                    val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, label = "navBg")
                    val fg by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, label = "navFg")
                    Row(
                        Modifier.clip(CircleShape).background(bg)
                            .selectable(selected = on, role = Role.Tab) { onSelect(item.route) }
                            .testTag("tab_${item.route}")
                            .height(54.dp)
                            .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
                            .padding(horizontal = if (on) 20.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (on) item.selectedIcon else item.icon, contentDescription = if (on) null else item.label, tint = fg)
                        if (on) {
                            Spacer(Modifier.width(8.dp))
                            Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Page header: big emphasized title, like the Expressive top app bars. */
@Composable
fun ColumnScope.PageTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp))
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp))
    }
}
