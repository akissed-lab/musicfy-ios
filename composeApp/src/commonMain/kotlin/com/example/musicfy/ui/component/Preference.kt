// Preference.kt
//
// KMP port of upstream `ui/component/Preference.kt`. Dialogs come from the
// ported Dialog.kt and resources are the generated typed ones. AppSwitch is
// included here because upstream settings screens use it alongside these rows.

package com.example.musicfy.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.check
import com.example.musicfy.resources.close
import com.example.musicfy.resources.history_duration
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun PreferenceEntry(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    content: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = isEnabled && onClick != null,
                onClick = onClick ?: {},
            )
            .alpha(if (isEnabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        if (icon != null) {
            Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                icon()
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(color = Color.White)) {
                title()
            }

            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.titleSmall,
                    color = GlassTokens.MutedContent,
                )
            }

            content?.invoke()
        }

        if (trailingContent != null) {
            Spacer(Modifier.width(12.dp))
            trailingContent()
        }
    }
}

@Composable
fun <T> ListPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        ListDialog(
            onDismiss = { showDialog = false },
        ) {
            items(values) { value ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDialog = false
                            onValueSelected(value)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(
                        selected = value == selectedValue,
                        onClick = null,
                    )
                    Text(
                        text = valueText(value),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = valueText(selectedValue),
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
inline fun <reified T : Enum<T>> EnumListPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    noinline icon: (@Composable () -> Unit)?,
    selectedValue: T,
    noinline valueText: @Composable (T) -> String,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    ListPreference(
        modifier = modifier,
        title = title,
        icon = icon,
        selectedValue = selectedValue,
        values = enumValues<T>().toList(),
        valueText = valueText,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
    )
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean = true,
) {
    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        trailingContent = {
            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = isEnabled,
                thumbContent = {
                    Icon(
                        painter = painterResource(if (checked) Res.drawable.check else Res.drawable.close),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        },
        onClick = { onCheckedChange(!checked) },
        isEnabled = isEnabled,
    )
}

@Composable
fun EditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        TextFieldDialog(
            initialTextFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
            singleLine = singleLine,
            isInputValid = isInputValid,
            onDone = onValueChange,
            onDismiss = { showDialog = false },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value,
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(value) }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.history_duration),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                showDialog = false
                onValueChange.invoke(sliderValue)
            },
            onCancel = {
                sliderValue = value
                showDialog = false
            },
            onReset = {
                sliderValue = 30f
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val seconds = sliderValue.roundToInt()
                    Text(
                        text = if (seconds == 1) "1 second" else "$seconds seconds",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )

                    Spacer(Modifier.height(16.dp))

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 15f..60f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value.roundToInt().toString(),
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
fun PreferenceGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = LocalGlassState.current.accent,
        modifier = modifier.padding(16.dp),
    )
}

/**
 * Common replacement for upstream `AppSwitch.kt`: same call signature, but the
 * track and thumb use the shared glass primitives and the active page accent.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null,
) {
    val accent = LocalGlassState.current.accent
    val alpha = if (enabled) 1f else 0.38f

    val trackColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.85f * alpha) else GlassTokens.Chrome.copy(alpha = alpha),
        animationSpec = tween(150),
        label = "switchTrack",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color.Transparent else GlassTokens.Stroke.copy(alpha = alpha),
        animationSpec = tween(150),
        label = "switchBorder",
    )
    val thumbColor by animateColorAsState(
        targetValue = Color.White.copy(alpha = alpha),
        animationSpec = tween(150),
        label = "switchThumb",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = tween(150),
        label = "switchOffset",
    )

    Box(
        modifier = modifier
            .size(52.dp, 32.dp)
            .toggleable(
                value = checked,
                enabled = enabled && onCheckedChange != null,
                role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) },
            )
            .glassSurface(
                shape = RoundedCornerShape(50),
                style = GlassStyle(
                    containerColor = trackColor,
                    borderColor = borderColor,
                    cornerRadius = 16.dp,
                    elevation = 0.dp,
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .glassSurface(
                    shape = CircleShape,
                    style = GlassStyle(
                        containerColor = thumbColor,
                        cornerRadius = 12.dp,
                        elevation = 0.dp,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            thumbContent?.invoke()
        }
    }
}
