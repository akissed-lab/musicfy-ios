package com.example.musicfy.ui.screens.settings.importsync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.importer.LinkImporter
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.theme.interFontFamily
import com.example.musicfy.viewmodels.ImportSyncViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory

/**
 * Public-link importer: paste a Spotify or Apple Music playlist/album link, see what was read,
 * then confirm. Matching maps metadata to YouTube song ids; no audio is downloaded from either
 * service and no account or WebView is involved.
 */
@Composable
fun ImportSyncScreen(
    navController: NavController,
    pureBlack: Boolean = false,
) {
    val container = LocalAppContainer.current
    if (container == null) {
        SubSettingsScaffold(title = "Import from a link", onBack = { navController.navigateUp() }) {
            Text("The app container isn't ready yet.", color = Color.White)
        }
        return
    }

    val viewModel = appViewModel<ImportSyncViewModel>(
        factory = containerViewModelFactory(container) { ImportSyncViewModel(it) },
    )
    val pending by viewModel.pending.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val notice by viewModel.notice.collectAsState()

    var link by remember { mutableStateOf("") }

    val accent = LocalGlassState.current.accent
    val cardStyle = GlassStyle(
        containerColor = if (pureBlack) Color(0xFF000000) else accent.copy(alpha = 0.12f),
        borderColor = Color.White.copy(alpha = 0.14f),
        cornerRadius = 22.dp,
        elevation = 12.dp,
    )

    val detectedLabel = pending?.provider?.label
        ?: LinkImporter.detect(link)?.label
        ?: "Not detected yet"
    val fraction = if (progress.totalTracks > 0) {
        (progress.processedTracks.toFloat() / progress.totalTracks).coerceIn(0f, 1f)
    } else {
        0f
    }

    SubSettingsScaffold(
        title = "Import from a link",
        backLabel = "Settings",
        onBack = { navController.navigateUp() },
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), style = cardStyle) {
            Text(
                text = "Paste a public link",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Spotify or Apple Music playlist/album links work without signing in.",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTokens.MutedContent,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = link,
                    onValueChange = { link = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = interFontFamily(),
                        fontSize = 15.sp,
                        color = Color.White,
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (link.isEmpty()) {
                            Text(
                                text = "https://open.spotify.com/playlist/…",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 15.sp,
                                fontFamily = interFontFamily(),
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.loadLink(link) },
                    enabled = link.isNotBlank() && !progress.isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text("Read link")
                }
                TextButton(
                    onClick = { viewModel.cancel() },
                    enabled = pending != null || progress.isRunning,
                ) {
                    Text(if (progress.isRunning) "Cancel import" else "Clear link", color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Detected: $detectedLabel",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTokens.MutedContent,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), style = cardStyle) {
            Text(
                text = "What was found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (pending != null) {
                val parsed = pending!!
                Text(
                    text = "${parsed.provider?.label ?: "Link"} · ${parsed.totalSongs} songs · " +
                        "${parsed.totalPlaylists} playlist(s)",
                    color = Color.White,
                )
                parsed.warnings.forEach { warning ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "• $warning", color = Color(0xFFFFC857), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.confirm() },
                    enabled = !progress.isRunning && parsed.totalSongs > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text("Import ${parsed.totalSongs} songs")
                }
            } else {
                Text(
                    text = "No link has been read yet. Read one to see its track count before importing.",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTokens.MutedContent,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Limits: Spotify link import shows only the first 100 songs of a playlist. " +
                    "Apple Music pages may be truncated. Private playlists aren't reachable. " +
                    "Importing matches metadata to YouTube songs; it does not import DRM audio.",
                style = MaterialTheme.typography.labelSmall,
                color = GlassTokens.MutedContent,
            )
        }

        val message = progress.error ?: notice
        if (message != null) {
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), style = cardStyle) {
                Text(text = message, color = Color(0xFFFFC857))
                TextButton(onClick = { viewModel.clearNotice() }) {
                    Text("Dismiss", color = Color.White)
                }
            }
        }

        if (progress.isRunning || progress.isDone) {
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), style = cardStyle) {
                Text(
                    text = when {
                        progress.cancelled -> "Import cancelled"
                        progress.error != null -> "Import stopped"
                        progress.isRunning -> "Importing from ${progress.source?.label ?: "link"}"
                        else -> "Import finished"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .background(accent),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${progress.processedTracks} of ${progress.totalTracks} tracks · " +
                        "${progress.matchedTracks} matched · ${progress.unmatched.size} unmatched",
                    color = Color.White,
                )
                if (progress.unmatched.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Unmatched: " + progress.unmatched.take(5).joinToString(", ") { entry ->
                            entry.track.title + entry.playlistName?.let { name -> " ($name)" }.orEmpty()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTokens.MutedContent,
                    )
                }
                if (progress.currentLabel.isNotBlank()) {
                    Text(
                        text = progress.currentLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTokens.MutedContent,
                    )
                }
                if (progress.playlistsCreated > 0) {
                    Text(
                        text = "${progress.playlistsCreated} playlist(s) created",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTokens.MutedContent,
                    )
                }
                if (!progress.isRunning) {
                    TextButton(onClick = { viewModel.clearNotice() }) {
                        Text("Clear summary", color = Color.White)
                    }
                }
            }
        }
    }
}
