// ModuleSheetHost.kt
//
// Common Compose host for module sheets. Upstream's version uses the Android
// PopupSheet handle, BackHandler and a RenderNode-backed backdrop; this port
// uses the shared PopupSheet/BackHandler primitives from commonMain and the
// glass surface used by every other screen. No module code is executed here.

package com.example.musicfy.ui.screens.modules

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.musicfy.modules.ModuleRegistry
import com.example.musicfy.modules.ModuleSheet
import com.example.musicfy.modules.ModuleSheetScope
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.glassSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One ask for a sheet; a new ask is a new object so an old exit cannot close it. */
@Immutable
data class ModuleSheetRequest(val moduleId: String, val sheetId: String)

/** Callers only ask; the host decides whether a module is still able to show the sheet. */
object ModuleSheetRequests {
    private val _request = MutableStateFlow<ModuleSheetRequest?>(null)
    val request: StateFlow<ModuleSheetRequest?> get() = _request

    fun open(moduleId: String, sheetId: String) {
        _request.value = ModuleSheetRequest(moduleId, sheetId)
    }

    fun close() {
        _request.value = null
    }

    fun close(request: ModuleSheetRequest) {
        if (_request.value === request) close()
    }
}

/** Place next to the other overlays in the app root, above the player. */
@Composable
fun ModuleSheetHost(modifier: Modifier = Modifier) {
    val request by ModuleSheetRequests.request.collectAsState()
    val registrations by ModuleRegistry.entries.collectAsState()

    val resolved = remember(request, registrations) {
        val asked = request ?: null
        if (asked == null) {
            null
        } else {
            val entry = registrations.firstOrNull { it.enabled && it.moduleId == asked.moduleId }
            val sheet = entry?.sheets?.firstOrNull { it.id == asked.sheetId }
            if (entry != null && sheet != null) asked to sheet else null
        }
    }

    if (resolved == null) {
        // A module can be disabled (or the sheet renamed) while a sheet is up.
        request?.let { stale ->
            LaunchedEffect(stale) { ModuleSheetRequests.close(stale) }
        }
        return
    }

    ModuleSheetPanel(request = resolved.first, sheet = resolved.second, modifier = modifier)
}

@Composable
private fun ModuleSheetPanel(
    request: ModuleSheetRequest,
    sheet: ModuleSheet,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val presence = remember(request) { Animatable(0f) }
    var dragged by remember(request) { mutableFloatStateOf(0f) }
    var closing by remember(request) { mutableStateOf(false) }

    val dismiss = remember(request) {
        {
            if (!closing) {
                closing = true
                scope.launch {
                    presence.animateTo(0f, spring(dampingRatio = 1f, stiffness = 520f))
                    ModuleSheetRequests.close(request)
                }
            }
        }
    }

    LaunchedEffect(request) {
        presence.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 320f))
    }
    BackHandler(onBack = dismiss)

    val sheetScope = remember(request, dismiss) {
        ModuleSheetScope(request.moduleId, request.sheetId, dismiss)
    }
    val shape = remember { RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = presence.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = sheet.dim.coerceIn(0f, 0.85f)))
                .clickable(onClick = dismiss),
        )

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (sheet.heightFraction != null) {
                            Modifier.height(maxHeight * sheet.heightFraction.coerceIn(0.2f, 1f))
                        } else {
                            Modifier.heightIn(max = maxHeight * 0.86f)
                        },
                    )
                    .graphicsLayer {
                        translationY = (1f - presence.value.coerceIn(0f, 1f)) * size.height + dragged
                    }
                    .clip(shape)
                    .glassSurface(
                        shape = shape,
                        style = GlassStyle(
                            containerColor = Color(0xF2141418),
                            cornerRadius = 24.dp,
                            elevation = 24.dp,
                        ),
                    )
                    .draggable(
                        state = rememberDraggableState { delta ->
                            dragged = (dragged + delta).coerceAtLeast(0f)
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (dragged > 96f || velocity > 1_200f) {
                                dismiss()
                            } else {
                                scope.launch {
                                    Animatable(dragged).animateTo(
                                        0f,
                                        spring(dampingRatio = 0.9f, stiffness = 500f),
                                    ) { dragged = value }
                                }
                            }
                        },
                    ),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    if (sheet.showHandle) {
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 10.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.32f)),
                        )
                    }
                    Text(
                        text = sheet.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (sheet.content != null) {
                        sheet.content.invoke(sheetScope)
                    } else {
                        Text(
                            text = "This module advertises a sheet but has no portable content on iOS.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0x8CFFFFFF),
                        )
                    }
                }
            }
        }
    }
}
