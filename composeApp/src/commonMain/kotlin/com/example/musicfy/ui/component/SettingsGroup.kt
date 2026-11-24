// SettingsGroup.kt
//
// KMP port: Material Cards are replaced with the shared glassSurface so every
// settings row keeps the liquid-glass look of the rest of the app.

package com.example.musicfy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.info
import kotlinx.coroutines.delay

enum class SettingsGroupStyle {
    Classic,

    Grouped,
}

@Composable
fun SettingsGroup(
    title: String? = null,
    items: List<SettingsItem>,
    style: SettingsGroupStyle = SettingsGroupStyle.Classic,
) {
    val accent = LocalGlassState.current.accent
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
            )
        }

        when (style) {
            SettingsGroupStyle.Classic -> ClassicItems(items)
            SettingsGroupStyle.Grouped -> GroupedItems(items)
        }
    }
}

@Composable
private fun ClassicItems(items: List<SettingsItem>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEach { item ->
            AnimatedVisibility(
                visible = item.isVisible,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(20.dp),
                            style = GlassStyle(
                                containerColor = GlassTokens.Chrome.copy(alpha = 0.8f),
                                cornerRadius = 20.dp,
                                elevation = 10.dp,
                            ),
                        ),
                ) {
                    SettingsItemRow(item = item, style = SettingsGroupStyle.Classic)
                }
            }
        }
    }
}

private class ItemCluster(
    val parent: SettingsItem,
    val subs: MutableList<SettingsItem> = mutableListOf(),
)

private fun clusterItems(items: List<SettingsItem>): List<ItemCluster> {
    val clusters = mutableListOf<ItemCluster>()
    items.forEach { item ->
        if (item.isSubOption && clusters.isNotEmpty()) {
            clusters.last().subs += item
        } else {
            clusters += ItemCluster(item)
        }
    }
    return clusters
}

@Composable
private fun GroupedItems(items: List<SettingsItem>) {
    val clusters = clusterItems(items)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(28.dp),
                style = GlassStyle(
                    containerColor = GlassTokens.Chrome.copy(alpha = 0.72f),
                    cornerRadius = 28.dp,
                    elevation = 12.dp,
                ),
            ),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            clusters.forEach { cluster ->
                AnimatedVisibility(
                    visible = cluster.parent.isVisible,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
                ) {
                    ClusterRows(cluster)
                }
            }
        }
    }
}

@Composable
private fun ClusterRows(cluster: ItemCluster) {
    val accent = LocalGlassState.current.accent
    val hasVisibleSubs = cluster.subs.any { it.isVisible }

    val pillAlpha by animateFloatAsState(
        targetValue = if (hasVisibleSubs) 1f else 0f,
        animationSpec = tween(300),
        label = "pillAlpha",
    )
    val pillInset by animateDpAsState(
        targetValue = if (hasVisibleSubs) 6.dp else 0.dp,
        animationSpec = tween(300),
        label = "pillInset",
    )

    val pillColor = accent.copy(alpha = 0.2f * pillAlpha)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = pillInset, vertical = pillInset / 2)
            .clip(RoundedCornerShape(22.dp))
            .glassSurface(
                shape = RoundedCornerShape(22.dp),
                style = GlassStyle(
                    containerColor = pillColor,
                    borderColor = Color.Transparent,
                    cornerRadius = 22.dp,
                    elevation = 0.dp,
                ),
            ),
    ) {
        Column {
            SettingsItemRow(item = cluster.parent, style = SettingsGroupStyle.Grouped)
            cluster.subs.forEach { sub ->
                AnimatedVisibility(
                    visible = sub.isVisible,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
                ) {
                    SettingsItemRow(item = sub, style = SettingsGroupStyle.Grouped)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsItemRow(
    item: SettingsItem,
    style: SettingsGroupStyle,
) {
    val accent = LocalGlassState.current.accent

    // A row arrived at from settings search claims the request the first time it composes,
    // flashes briefly, then clears it - so the highlight fires once rather than every time.
    val key = item.highlightKey
    LaunchedEffect(key, SettingsHighlight.pending) {
        if (key != null && SettingsHighlight.pending == key) {
            SettingsHighlight.pending = null
            SettingsHighlight.active = key
            delay(2400)
            if (SettingsHighlight.active == key) SettingsHighlight.active = null
        }
    }

    val isFlashing = key != null && SettingsHighlight.active == key
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isFlashing) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "settingsHighlight",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (highlightAlpha > 0.01f) {
                    drawRect(color = accent.copy(alpha = 0.24f * highlightAlpha))
                }
            }
            .combinedClickable(
                enabled = item.enabled && (item.onClick != null || item.descriptionText != null),
                onClick = { item.onClick?.invoke() },
                // holding a row shows its whole description, since the line under it is cut to one
                onLongClick = (item.detail ?: item.descriptionText)?.let { full ->
                    { TopToaster.show(full, icon = Res.drawable.info) }
                },
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.icon?.let { icon ->
            val iconShape = item.iconShape ?: CircleShape
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(iconShape)
                    .glassSurface(
                        shape = iconShape,
                        style = GlassStyle(
                            containerColor = accent.copy(alpha = 0.18f),
                            borderColor = accent.copy(alpha = 0.32f),
                            cornerRadius = 16.dp,
                            elevation = 0.dp,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val drawIcon: @Composable () -> Unit = {
                    if (item.tintIcon) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = if (!item.enabled) {
                                Color.White.copy(alpha = 0.38f)
                            } else {
                                Color.White
                            },
                            modifier = Modifier.size(if (item.showBadge) 20.dp else 16.dp),
                        )
                    } else {
                        Image(
                            painter = icon,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                if (item.showBadge) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        drawIcon()
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .glassSurface(
                                    shape = CircleShape,
                                    style = GlassStyle(
                                        containerColor = accent,
                                        cornerRadius = 5.dp,
                                        elevation = 0.dp,
                                    ),
                                ),
                        )
                    }
                } else {
                    drawIcon()
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            val titleBaseStyle = if (style == SettingsGroupStyle.Grouped) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.titleMedium
            }
            val titleColor = when {
                !item.enabled -> GlassTokens.MutedContent.copy(alpha = 0.38f)
                style == SettingsGroupStyle.Grouped -> GlassTokens.MutedContent
                else -> Color.White
            }
            ProvideTextStyle(titleBaseStyle.copy(color = titleColor)) {
                item.title()
            }

            val descColor = if (!item.enabled) {
                GlassTokens.MutedContent.copy(alpha = 0.38f)
            } else {
                GlassTokens.MutedContent
            }

            if (item.descriptionText != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = item.descriptionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = descColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                item.description?.let { desc ->
                    Spacer(modifier = Modifier.height(2.dp))
                    ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color = descColor)) {
                        desc()
                    }
                }
            }
        }

        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

data class SettingsItem(
    val icon: Painter? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,

    val descriptionText: String? = null,
    /** The whole description, shown when the row is held; [descriptionText] is the short line. */
    val detail: String? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val tintIcon: Boolean = true,
    val iconShape: Shape? = null,
    val enabled: Boolean = true,
    val isVisible: Boolean = true,
    val isSubOption: Boolean = false,
    /** Identifier used by settings search to flash this row when the user arrives from a result. */
    val highlightKey: String? = null,
    val onClick: (() -> Unit)? = null,
)
