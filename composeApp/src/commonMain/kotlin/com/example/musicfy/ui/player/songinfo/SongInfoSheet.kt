// SongInfoSheet.kt
//
// KMP port of the "song info" surface from Mochy 8.5 (PR #407). Upstream added the
// about/genres/writers/label facts to its player skin and linked them to the about
// loader. Here the same facts are a plain model, drawn in one self-contained glass
// bottom sheet with no loader, route or settings dependency.

package com.example.musicfy.ui.player.songinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.close
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.player.styles.PlayerStyleKit
import com.example.musicfy.ui.utils.stableSystemBars
import org.jetbrains.compose.resources.painterResource

private val SheetPanel = Color(0xFF101015).copy(alpha = 0.93f)
private val SheetBorder = Color.White.copy(alpha = 0.14f)
private val SheetRow = Color.White.copy(alpha = 0.08f)
private val SheetMuted = Color.White.copy(alpha = 0.56f)
private val SheetFaint = Color.White.copy(alpha = 0.35f)

/**
 * Bottom sheet with the song's identity, its about text, the facts a skin can show and
 * any external links. Tap the scrim or the close button to dismiss it.
 *
 * [onOpenLink] is optional: by default a link is handed to the platform URI handler,
 * which opens the browser. No lookup is started from this composable.
 */
@Composable
fun SongInfoSheet(
    details: SongInfoDetails,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: ((SongInfoLink) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.stableSystemBars)
                .glassSurface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    style = GlassStyle(
                        containerColor = SheetPanel,
                        borderColor = SheetBorder,
                        elevation = 24.dp,
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Song info",
                    color = Color.White,
                    style = PlayerStyleKit.textStyle(16),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .glassSurface(
                            shape = CircleShape,
                            style = GlassStyle(
                                containerColor = SheetRow,
                                borderColor = SheetBorder,
                                elevation = 0.dp,
                            ),
                        )
                        .clickable(onClick = onDismiss),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close",
                        tint = SheetMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = this@BoxWithConstraints.maxHeight * 0.78f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = details.title.ifBlank { "Unknown" },
                    color = Color.White,
                    style = PlayerStyleKit.textStyle(20),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!details.artist.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = details.artist,
                        color = GlassTokens.MutedContent,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val hasAbout = !details.about.isNullOrBlank()
                val hasFacts = !details.album.isNullOrBlank() ||
                    !details.year.isNullOrBlank() ||
                    !details.genre.isNullOrBlank() ||
                    !details.writers.isNullOrBlank() ||
                    !details.label.isNullOrBlank()

                if (hasAbout) {
                    Spacer(Modifier.height(18.dp))
                    SectionLabel("About")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = details.about.orEmpty().trim(),
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }

                if (hasFacts) {
                    Spacer(Modifier.height(18.dp))
                    SectionLabel("Details")
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassSurface(
                                shape = RoundedCornerShape(16.dp),
                                style = GlassStyle(
                                    containerColor = SheetRow,
                                    borderColor = SheetBorder,
                                    elevation = 0.dp,
                                ),
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        FactRow("Album", details.album)
                        FactRow("Released", details.year)
                        FactRow("Genre", details.genre)
                        FactRow("Writers", details.writers)
                        FactRow("Label", details.label)
                    }
                }

                if (details.externalLinks.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    SectionLabel("More")
                    Spacer(Modifier.height(8.dp))
                    details.externalLinks.forEach { link ->
                        LinkRow(
                            link = link,
                            onClick = {
                                if (onOpenLink != null) {
                                    onOpenLink(link)
                                } else {
                                    runCatching { uriHandler.openUri(link.url) }
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (!hasAbout && !hasFacts && details.externalLinks.isEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "No extra information is available for this song yet.",
                        color = SheetFaint,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * Same sheet from plain values, for a player menu that has the facts as separate
 * strings rather than a [SongInfoDetails] instance.
 */
@Composable
fun SongInfoSheet(
    title: String,
    artist: String? = null,
    album: String? = null,
    year: String? = null,
    genre: String? = null,
    writers: String? = null,
    label: String? = null,
    about: String? = null,
    externalLinks: List<SongInfoLink> = emptyList(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: ((SongInfoLink) -> Unit)? = null,
) {
    SongInfoSheet(
        details = SongInfoDetails(
            title = title,
            artist = artist,
            album = album,
            year = year,
            genre = genre,
            writers = writers,
            label = label,
            about = about,
            externalLinks = externalLinks,
        ),
        onDismiss = onDismiss,
        modifier = modifier,
        onOpenLink = onOpenLink,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = SheetMuted,
        style = PlayerStyleKit.textStyle(11, FontWeight.SemiBold),
    )
}

@Composable
private fun FactRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            text = label,
            color = SheetMuted,
            fontSize = 12.sp,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value.trim(),
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LinkRow(link: SongInfoLink, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                style = GlassStyle(
                    containerColor = SheetRow,
                    borderColor = SheetBorder,
                    elevation = 0.dp,
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.label.ifBlank { link.url },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = link.host(),
                color = SheetMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "Open", color = GlassTokens.MutedContent, fontSize = 12.sp)
    }
}

/** The host part of a URL, without pulling in a platform URI type. */
private fun SongInfoLink.host(): String = url
    .substringAfter("://", "")
    .substringBefore("/")
    .substringBefore("?")
    .ifBlank { url }
