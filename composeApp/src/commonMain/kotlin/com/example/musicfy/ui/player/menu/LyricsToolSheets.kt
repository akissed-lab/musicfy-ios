// LyricsToolSheets.kt
//
// KMP port of the three lyrics tools that used to depend on Hilt, MenuSheetSurface,
// AppSwitch and Android resources. Everything here is self-contained Common Compose:
// a translucent scrim plus a glass sheet, driven by PlaybackController / AppContainer.

package com.example.musicfy.ui.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.lyrics.LyricsHelper
import com.example.musicfy.lyrics.LyricsProviderRegistry
import com.example.musicfy.lyrics.LyricsTranslationHelper
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.close
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.utils.stableSystemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private val SheetContainer = Color(0xFF101015).copy(alpha = 0.93f)
private val SheetBorder = Color.White.copy(alpha = 0.14f)
private val SheetText = Color.White
private val SheetMuted = Color.White.copy(alpha = 0.56f)
private val SheetFaint = Color.White.copy(alpha = 0.35f)
private val SheetRow = Color.White.copy(alpha = 0.08f)
private val SheetAvailable = Color(0xFF4CD964)
private val SheetMissing = Color(0xFFFF5A5F)
private val SheetChecking = Color(0xFF6A6A6A)

private const val EditedProvider = "Your edit"

/** Shared glass sheet frame: a dim backdrop and a bottom-anchored frosted panel. */
@Composable
internal fun LyricsSheetScaffold(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
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
                        containerColor = SheetContainer,
                        borderColor = SheetBorder,
                        elevation = 24.dp,
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = SheetText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
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
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
internal fun SheetPill(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .glassSurface(
                shape = RoundedCornerShape(50.dp),
                style = GlassStyle(
                    containerColor = if (highlighted) Color.White.copy(alpha = 0.20f) else SheetRow,
                    borderColor = SheetBorder,
                    elevation = 0.dp,
                ),
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (enabled) SheetText else SheetFaint,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------------------------

/** Line-by-line editor. Saving writes plain per-line LRC under the "Your edit" provider. */
@Composable
fun LyricsEditorSheet(onDismiss: () -> Unit) {
    val controller = LocalPlaybackController.current ?: return
    val database = LocalAppContainer.current?.database
    val scope = rememberCoroutineScope()

    val metadata by controller.currentMetadata.collectAsState()
    val lyricsEntity by controller.currentLyrics.collectAsState()

    val parsed = remember(lyricsEntity?.lyrics) {
        val raw = lyricsEntity?.lyrics
        if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) {
            emptyList()
        } else {
            runCatching { LyricsUtils.parseLyrics(raw) }.getOrDefault(emptyList())
        }
    }
    val drafts = remember(parsed) {
        mutableStateListOf<String>().apply { addAll(parsed.map { it.text }) }
    }

    LyricsSheetScaffold(onDismiss = onDismiss, title = "Edit lyrics") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${parsed.size} lines",
                color = SheetMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Save",
                color = if (parsed.isEmpty()) SheetFaint else SheetText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(enabled = parsed.isNotEmpty()) {
                        val songId = metadata?.id ?: lyricsEntity?.id
                        if (songId != null && database != null) {
                            val edited = (lyricsEntity ?: LyricsEntity(id = songId, lyrics = "")).copy(
                                id = songId,
                                lyrics = buildLrc(parsed.map { it.time }, drafts),
                                provider = EditedProvider,
                                translatedLyrics = "",
                                translationLanguage = "",
                                translationMode = "",
                            )
                            scope.launch(Dispatchers.Default) { database.upsert(edited) }
                        }
                        onDismiss()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (parsed.isEmpty()) {
            Text(
                text = "No lyrics to edit for this song.",
                color = SheetMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .glassSurface(
                        shape = RoundedCornerShape(18.dp),
                        style = GlassStyle(
                            containerColor = SheetRow,
                            borderColor = SheetBorder,
                            elevation = 0.dp,
                        ),
                    )
                    .padding(14.dp),
            ) {
                itemsIndexed(parsed, key = { index, _ -> index }) { index, entry ->
                    Column(modifier = Modifier.padding(bottom = 14.dp)) {
                        Text(
                            text = formatTimestamp(entry.time),
                            color = SheetMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        BasicTextField(
                            value = drafts.getOrElse(index) { entry.text },
                            onValueChange = { value ->
                                if (index < drafts.size) drafts[index] = value
                            },
                            textStyle = TextStyle(
                                color = SheetText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            cursorBrush = SolidColor(SheetText),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** `83_400` becomes `"1:23"`; the stored bracketed LRC tag is a serialisation detail. */
internal fun formatTimestamp(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** Reassembles edited text against its original timings as plain per-line LRC. */
internal fun buildLrc(times: List<Long>, texts: List<String>): String = buildString {
    for (index in times.indices) {
        val text = texts.getOrNull(index).orEmpty()
        if (text.isBlank()) continue
        val ms = times[index].coerceAtLeast(0L)
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1000
        val centis = (ms % 1000) / 10
        append('[')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
        append('.')
        append(centis.toString().padStart(2, '0'))
        append(']')
        append(text.trim())
        append('\n')
    }
}

// ---------------------------------------------------------------------------------------------
// Provider picker
// ---------------------------------------------------------------------------------------------

/** Lists every known source; sources with a fetched candidate are offered first. */
@Composable
fun LyricsProviderSheet(onDismiss: () -> Unit) {
    val controller = LocalPlaybackController.current ?: return
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    val metadata by controller.currentMetadata.collectAsState()
    val lyricsEntity by controller.currentLyrics.collectAsState()

    val candidates = remember { mutableStateMapOf<String, LyricsHelper.ProviderCandidate>() }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(metadata?.id, container) {
        candidates.clear()
        val meta = metadata
        if (meta == null || container == null) {
            checking = false
            return@LaunchedEffect
        }
        checking = true
        val found = container.lyricsHelper.fetchCandidates(meta)
        candidates.putAll(found.associateBy { it.providerName })
        checking = false
    }

    val active = lyricsEntity?.provider
    val names = remember(candidates, checking, active) {
        val rank = { name: String ->
            when {
                name == active -> 0
                candidates.containsKey(name) -> 1
                checking -> 2
                else -> 3
            }
        }
        LyricsProviderRegistry.providerNames.sortedWith(
            compareBy<String> { rank(it) }
                .thenBy { candidates[it]?.order ?: Int.MAX_VALUE },
        )
    }

    LyricsSheetScaffold(onDismiss = onDismiss, title = "Lyrics provider") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            names.forEach { name ->
                val candidate = candidates[name]
                val isActive = name == active
                val ported = LyricsProviderRegistry.getProviderByName(name) != null
                val status = when {
                    isActive -> "In use"
                    candidate != null -> when (candidate.sync) {
                        LyricsUtils.SyncKind.WORD -> "Word synced"
                        LyricsUtils.SyncKind.LINE -> "Line synced"
                        LyricsUtils.SyncKind.PLAIN -> "Plain"
                        else -> "Not found"
                    } + if (candidate.hasDuet) " · duet" else ""
                    !ported -> "Not in this port"
                    checking -> "Checking…"
                    else -> "Not found"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(16.dp),
                            style = GlassStyle(
                                containerColor = SheetRow,
                                borderColor = SheetBorder,
                                elevation = 0.dp,
                            ),
                        )
                        .clickable(enabled = candidate != null && !isActive && container != null) {
                            val chosen = candidate ?: return@clickable
                            val songId = metadata?.id ?: return@clickable
                            val next = (lyricsEntity ?: LyricsEntity(id = songId, lyrics = "")).copy(
                                id = songId,
                                lyrics = chosen.lyrics,
                                provider = chosen.providerName,
                                translatedLyrics = "",
                                translationLanguage = "",
                                translationMode = "",
                            )
                            scope.launch(Dispatchers.Default) { container?.database?.upsert(next) }
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isActive -> SheetAvailable
                                    candidate != null -> SheetAvailable
                                    !ported -> SheetChecking
                                    checking -> SheetChecking
                                    else -> SheetMissing
                                },
                            ),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = LyricsProviderRegistry.getDisplayName(name),
                        color = if (candidate != null || isActive) SheetText else SheetMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = status,
                        color = if (isActive) SheetText else SheetMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Translation settings
// ---------------------------------------------------------------------------------------------

private val TranslationTargets = listOf(
    "af" to "Afrikaans", "sq" to "Albanian", "am" to "Amharic", "ar" to "Arabic",
    "hy" to "Armenian", "az" to "Azerbaijani", "eu" to "Basque", "be" to "Belarusian",
    "bn" to "Bengali", "bs" to "Bosnian", "bg" to "Bulgarian", "ca" to "Catalan",
    "ceb" to "Cebuano", "ny" to "Chichewa", "zh-CN" to "Chinese (Simplified)",
    "zh-TW" to "Chinese (Traditional)", "co" to "Corsican", "hr" to "Croatian",
    "cs" to "Czech", "da" to "Danish", "nl" to "Dutch", "en" to "English",
    "eo" to "Esperanto", "et" to "Estonian", "tl" to "Filipino", "fi" to "Finnish",
    "fr" to "French", "fy" to "Frisian", "gl" to "Galician", "ka" to "Georgian",
    "de" to "German", "el" to "Greek", "gu" to "Gujarati", "ht" to "Haitian Creole",
    "ha" to "Hausa", "haw" to "Hawaiian", "iw" to "Hebrew", "hi" to "Hindi",
    "hmn" to "Hmong", "hu" to "Hungarian", "is" to "Icelandic", "ig" to "Igbo",
    "id" to "Indonesian", "ga" to "Irish", "it" to "Italian", "ja" to "Japanese",
    "jw" to "Javanese", "kn" to "Kannada", "kk" to "Kazakh", "km" to "Khmer",
    "ko" to "Korean", "ku" to "Kurdish", "ky" to "Kyrgyz", "lo" to "Lao",
    "la" to "Latin", "lv" to "Latvian", "lt" to "Lithuanian", "lb" to "Luxembourgish",
    "mk" to "Macedonian", "mg" to "Malagasy", "ms" to "Malay", "ml" to "Malayalam",
    "mt" to "Maltese", "mi" to "Maori", "mr" to "Marathi", "mn" to "Mongolian",
    "my" to "Myanmar", "ne" to "Nepali", "no" to "Norwegian", "ps" to "Pashto",
    "fa" to "Persian", "pl" to "Polish", "pt" to "Portuguese", "pa" to "Punjabi",
    "ro" to "Romanian", "ru" to "Russian", "sm" to "Samoan", "gd" to "Scots Gaelic",
    "sr" to "Serbian", "st" to "Sesotho", "sn" to "Shona", "sd" to "Sindhi",
    "si" to "Sinhala", "sk" to "Slovak", "sl" to "Slovenian", "so" to "Somali",
    "es" to "Spanish", "su" to "Sundanese", "sw" to "Swahili", "sv" to "Swedish",
    "tg" to "Tajik", "ta" to "Tamil", "te" to "Telugu", "th" to "Thai",
    "tr" to "Turkish", "uk" to "Ukrainian", "ur" to "Urdu", "uz" to "Uzbek",
    "vi" to "Vietnamese", "cy" to "Welsh", "xh" to "Xhosa", "yi" to "Yiddish",
    "yo" to "Yoruba", "zu" to "Zulu",
)

@Composable
fun LyricsTranslationSheet(onDismiss: () -> Unit) {
    val (target, setTarget) = rememberPreference(SettingsKeys.TranslateLanguageKey, defaultValue = "en")
    val (source, setSource) = rememberPreference(SettingsKeys.TranslateSourceLanguageKey, defaultValue = "auto")
    val (mode, setMode) = rememberPreference(SettingsKeys.TranslateModeKey, defaultValue = "line")
    val (sessionWide, setSessionWide) = rememberPreference(SettingsKeys.TranslateLyricsKey, defaultValue = false)

    var pickingTarget by remember { mutableStateOf(false) }
    var pickingSource by remember { mutableStateOf(false) }

    val status by LyricsTranslationHelper.status.collectAsState()
    val translationOn by LyricsTranslationHelper.translationOn.collectAsState()
    val isTranslating = status is LyricsTranslationHelper.TranslationStatus.Translating

    LyricsSheetScaffold(onDismiss = onDismiss, title = "Lyrics translation") {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(text = "Translate from", color = SheetText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            SheetPill(
                label = languageLabel(source, fallback = "Detect automatically", includeAuto = true),
                onClick = { pickingSource = true },
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "To", color = SheetText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            SheetPill(label = languageLabel(target, fallback = "Pick a language"), onClick = { pickingTarget = true })

            Spacer(modifier = Modifier.height(14.dp))
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Keep translation on for this session",
                    color = SheetText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = sessionWide, onCheckedChange = setSessionWide)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Apply to", color = SheetMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetModeButton(
                    label = "Each line",
                    selected = mode != "whole",
                    onClick = { setMode("line") },
                    modifier = Modifier.weight(1f),
                )
                SheetModeButton(
                    label = "Whole screen",
                    selected = mode == "whole",
                    onClick = { setMode("whole") },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            SheetPill(
                label = when {
                    isTranslating -> "Translating… (tap to stop)"
                    translationOn -> "Show original lyrics"
                    target.isBlank() -> "Pick a language first"
                    else -> "Translate lyrics"
                },
                enabled = target.isNotBlank(),
                highlighted = !translationOn,
                onClick = {
                    if (translationOn) {
                        LyricsTranslationHelper.triggerClearTranslations()
                    } else {
                        LyricsTranslationHelper.triggerManualTranslation()
                    }
                    onDismiss()
                },
            )

            (status as? LyricsTranslationHelper.TranslationStatus.Error)?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Couldn't translate: ${error.message}",
                    color = Color(0xFFFF8A8A),
                    fontSize = 12.sp,
                )
            }
        }
    }

    if (pickingTarget) {
        LanguagePickerSheet(
            title = "Translate to",
            selected = target,
            options = TranslationTargets,
            onPick = { setTarget(it); pickingTarget = false },
            onDismiss = { pickingTarget = false },
        )
    }
    if (pickingSource) {
        LanguagePickerSheet(
            title = "Translate from",
            selected = source.ifBlank { "auto" },
            options = listOf("auto" to "Detect automatically") + TranslationTargets,
            onPick = { setSource(it); pickingSource = false },
            onDismiss = { pickingSource = false },
        )
    }
}

@Composable
private fun SheetModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(42.dp)
            .glassSurface(
                shape = RoundedCornerShape(50.dp),
                style = GlassStyle(
                    containerColor = if (selected) Color.White.copy(alpha = 0.20f) else SheetRow,
                    borderColor = SheetBorder,
                    elevation = 0.dp,
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = if (selected) SheetText else SheetMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun languageLabel(code: String, fallback: String, includeAuto: Boolean = false): String = when {
    code.isBlank() || (includeAuto && code == "auto") -> fallback
    else -> TranslationTargets.firstOrNull { it.first == code }?.second ?: code
}

@Composable
private fun LanguagePickerSheet(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        if (query.isBlank()) {
            options
        } else {
            options.filter { it.second.contains(query, ignoreCase = true) }
        }
    }

    LyricsSheetScaffold(onDismiss = onDismiss, title = title) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .glassSurface(
                    shape = RoundedCornerShape(50.dp),
                    style = GlassStyle(
                        containerColor = SheetRow,
                        borderColor = SheetBorder,
                        elevation = 0.dp,
                    ),
                )
                .padding(horizontal = 16.dp),
        ) {
            if (query.isEmpty()) {
                Text(text = "Search language", color = SheetFaint, fontSize = 13.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = SheetText, fontSize = 13.sp),
                cursorBrush = SolidColor(SheetText),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
            itemsIndexed(filtered, key = { _, item -> item.first }) { _, (code, label) ->
                val isSelected = code == selected
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(50.dp),
                            style = GlassStyle(
                                containerColor = if (isSelected) Color.White.copy(alpha = 0.20f) else SheetRow,
                                borderColor = SheetBorder,
                                elevation = 0.dp,
                            ),
                        )
                        .clickable(onClick = { onPick(code) })
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = label,
                        color = SheetText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
