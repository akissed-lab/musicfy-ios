// LibraryTabs.kt
//
// The All / Local / Cached / Imported switch at the top of the Library and the
// small pieces the tabs share: the two-wide card grid, the caps section label
// and the "Manage" pill of a tab. The pills and grid cards are glass surfaces
// tinted by the page's [LocalGlassState] accent.

package com.example.musicfy.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.viewmodels.LibraryShelfKind

/**
 * What the Library shows: everything, what plays from the phone, what the player
 * has cached, or what was imported. [shelf] is the slice of the library a tab
 * is built from, if it is one.
 */
enum class LibraryTab(val label: String, val shelf: LibraryShelfKind? = null) {
    All("All"),
    Local("Local", LibraryShelfKind.Local),
    Cached("Cached", LibraryShelfKind.Cached),
    Imported("Imported"),
}

/** The tabs as small pills in one glass track, the picked one tinted with the page accent. */
@Composable
fun LibraryTabChips(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalGlassState.current.accent
    Row(
        modifier = modifier
            .glassSurface(
                shape = RoundedCornerShape(percent = 50),
                style = GlassStyle(
                    containerColor = GlassTokens.Chrome.copy(alpha = 0.72f),
                    elevation = 6.dp,
                    cornerRadius = 20.dp,
                ),
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LibraryTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .then(
                        if (isSelected) {
                            Modifier.glassSurface(
                                shape = RoundedCornerShape(percent = 50),
                                style = GlassStyle(
                                    containerColor = accent.copy(alpha = 0.32f),
                                    borderColor = accent.copy(alpha = 0.45f),
                                    elevation = 0.dp,
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable(onClick = { onSelect(tab) })
                    .heightIn(min = 28.dp)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) Color.White else GlassTokens.MutedContent,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** "ALL MUSIC", "PLAYLISTS": a small caps label over a group of cards. */
@Composable
fun LibraryCapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        modifier = modifier.padding(horizontal = LibraryHorizontalPadding),
    )
}

/** The small centred glass pill at the bottom of a tab that opens its Manage sheet. */
@Composable
fun LibraryManagePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalGlassState.current.accent
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .glassSurface(
                    shape = RoundedCornerShape(percent = 50),
                    style = GlassStyle(
                        containerColor = accent.copy(alpha = 0.18f),
                        borderColor = accent.copy(alpha = 0.32f),
                        elevation = 6.dp,
                        cornerRadius = 20.dp,
                    ),
                )
                .clickable(onClick = onClick)
                .heightIn(min = 30.dp)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** One card in a [LibraryCardGrid]. */
@Immutable
class LibraryCardEntry(
    val title: String,
    val count: Int,
    val covers: List<String?>,
    val onClick: () -> Unit,
    val roundCovers: Boolean = false,
    val icon: (@Composable () -> Unit)? = null,
)

/** Cards two to a row, the last one keeping its half width when it is alone. */
@Composable
fun LibraryCardGrid(entries: List<LibraryCardEntry>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = LibraryHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { entry ->
                    LibraryCategoryCard(
                        title = entry.title,
                        count = entry.count,
                        covers = entry.covers,
                        onClick = entry.onClick,
                        icon = entry.icon,
                        roundCovers = entry.roundCovers,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
