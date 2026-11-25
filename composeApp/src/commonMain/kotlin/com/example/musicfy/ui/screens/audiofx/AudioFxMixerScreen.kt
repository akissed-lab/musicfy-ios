// AudioFxMixerScreen.kt
//
// Portable UI for the upstream "Audio FX Mixer" (PR #407). It uses the shared
// glass scaffold and theme primitives; no Android APIs, native DSP or JNI.

package com.example.musicfy.ui.screens.audiofx

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicfy.player.audiofx.AudioFxBand
import com.example.musicfy.player.audiofx.AudioFxController
import com.example.musicfy.player.audiofx.AudioFxEffect
import com.example.musicfy.player.audiofx.AudioFxMixerState
import com.example.musicfy.player.audiofx.AudioFxPreset
import com.example.musicfy.player.audiofx.AudioFxSend
import com.example.musicfy.player.audiofx.InMemoryAudioFxController
import com.example.musicfy.ui.component.AppSwitch
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.SubSettingsScaffold
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.theme.interFontFamily
import kotlin.math.roundToInt

/**
 * Standalone mixer page. Hosts can keep one controller for the lifetime of the
 * player (and pass an [com.example.musicfy.player.audiofx.AudioFxProcessor] to
 * [InMemoryAudioFxController] later); the default instance keeps previews and
 * early integration self-contained.
 */
@Composable
fun AudioFxMixerScreen(
    onBack: () -> Unit = {},
    controller: AudioFxController = remember { InMemoryAudioFxController() },
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val accent = LocalGlassState.current.accent

    SubSettingsScaffold(
        title = "Audio FX Mixer",
        onBack = onBack,
        backLabel = "Audio Lab",
        modifier = modifier,
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mixer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = if (state.enabled) "On · shaping every song" else "Off · audio passes through",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.enabled) accent else GlassTokens.MutedContent,
                    )
                }
                AppSwitch(
                    checked = state.enabled,
                    onCheckedChange = controller::setEnabled,
                )
            }

            Spacer(Modifier.height(14.dp))
            MixerSectionTitle("Preset", accent)
            PresetSelector(
                selected = state.activePreset,
                onSelect = controller::selectPreset,
                accent = accent,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            ResetButton(accent = accent, onClick = controller::reset)
        }

        Spacer(Modifier.height(14.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            MixerSectionTitle("Ten-band EQ", accent)
            val bands = AudioFxBand.entries
            for (index in bands.indices) {
                BandSlider(
                    band = bands[index],
                    gainDb = state.gain(index),
                    enabled = state.enabled,
                    accent = accent,
                    onValueChange = { gain -> controller.setBandGain(index, gain) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            MixerSectionTitle("Sends", accent)
            for (send in AudioFxSend.entries) {
                SendSlider(
                    label = send.label,
                    level = state.sendLevel(send),
                    enabled = state.enabled,
                    accent = accent,
                    onValueChange = { level -> controller.setSendLevel(send, level) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            MixerSectionTitle("Effects", accent)
            for (effect in AudioFxEffect.entries) {
                EffectRow(
                    label = effect.label,
                    checked = state.isEffectEnabled(effect),
                    enabled = state.enabled,
                    onCheckedChange = { checked -> controller.setEffectEnabled(effect, checked) },
                )
            }
        }
    }
}

@Composable
private fun MixerSectionTitle(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = accent,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun PresetSelector(
    selected: AudioFxPreset?,
    onSelect: (AudioFxPreset) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected == null) {
            PresetChip(label = "Custom", isSelected = true, accent = accent, onClick = {})
        }
        for (preset in AudioFxPreset.entries) {
            PresetChip(
                label = preset.label,
                isSelected = selected == preset,
                accent = accent,
                onClick = { onSelect(preset) },
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = if (isSelected) accent.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.05f),
                    borderColor = if (isSelected) accent else Color.White.copy(alpha = 0.12f),
                    highlightColor = Color.White.copy(alpha = 0.06f),
                    cornerRadius = 18.dp,
                    elevation = 0.dp,
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = interFontFamily(),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) Color.White else GlassTokens.MutedContent,
        )
    }
}

@Composable
private fun BandSlider(
    band: AudioFxBand,
    gainDb: Float,
    enabled: Boolean,
    accent: Color,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${band.label} Hz",
            fontFamily = interFontFamily(),
            fontSize = 13.sp,
            color = Color.White,
            modifier = Modifier.width(58.dp),
        )
        MixerSlider(
            value = gainDb,
            valueRange = AudioFxMixerState.MinGainDb..AudioFxMixerState.MaxGainDb,
            enabled = enabled,
            accent = accent,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = formatGain(gainDb),
            fontFamily = interFontFamily(),
            fontSize = 12.sp,
            color = GlassTokens.MutedContent,
            textAlign = TextAlign.End,
            modifier = Modifier.width(58.dp),
        )
    }
}

@Composable
private fun SendSlider(
    label: String,
    level: Float,
    enabled: Boolean,
    accent: Color,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = interFontFamily(),
            fontSize = 13.sp,
            color = Color.White,
            modifier = Modifier.width(58.dp),
        )
        MixerSlider(
            value = level,
            valueRange = AudioFxMixerState.MinSendLevel..AudioFxMixerState.MaxSendLevel,
            enabled = enabled,
            accent = accent,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${(level * 100).roundToInt()}%",
            fontFamily = interFontFamily(),
            fontSize = 12.sp,
            color = GlassTokens.MutedContent,
            textAlign = TextAlign.End,
            modifier = Modifier.width(58.dp),
        )
    }
}

@Composable
private fun MixerSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    accent: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = Color.White.copy(alpha = 0.16f),
        ),
        modifier = modifier.height(28.dp),
    )
}

@Composable
private fun EffectRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = interFontFamily(),
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ResetButton(
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .glassSurface(
                shape = shape,
                style = GlassStyle(
                    containerColor = Color.White.copy(alpha = 0.06f),
                    borderColor = Color.White.copy(alpha = 0.14f),
                    cornerRadius = 18.dp,
                    elevation = 0.dp,
                ),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "RESET MIX",
            fontFamily = interFontFamily(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

private fun formatGain(gainDb: Float): String {
    val rounded = (gainDb * 10).roundToInt() / 10f
    return if (rounded > 0f) "+$rounded dB" else "$rounded dB"
}
