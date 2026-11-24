package com.example.musicfy.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.cancel
import com.example.musicfy.resources.done
import com.example.musicfy.resources.info
import com.example.musicfy.resources.reset
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Common port of upstream `Dialog.kt`. Material `Surface` is replaced by the
 * shared glass surface so dialogs keep the same frosted language as the nav
 * pill, cards and sheets.
 */

@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
    ) {
        GlassCard(
            modifier = modifier
                .padding(24.dp)
                .fillMaxWidth()
                .imePadding(),
            style = GlassStyle(cornerRadius = 28.dp, blurRadius = 24.dp),
        ) {
            if (icon != null) {
                Box(Modifier.align(Alignment.CenterHorizontally)) { icon() }
                Spacer(Modifier.height(16.dp))
            }
            if (title != null) {
                Box(Modifier.fillMaxWidth()) { title() }
                Spacer(Modifier.height(16.dp))
            }
            Column(
                horizontalAlignment = horizontalAlignment,
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
            if (buttons != null) {
                Spacer(Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                    content = buttons,
                )
            }
        }
    }
}

@Composable
fun ActionPromptDialog(
    title: String? = null,
    titleBar: (@Composable RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = if (titleBar != null) {
            { Row { titleBar() } }
        } else if (title != null) {
            {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
            }
        } else {
            null
        },
        buttons = {
            if (onReset != null) {
                GlassTextButton(onClick = { onReset() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.reset))
                }
            }
            Spacer(Modifier.width(8.dp))
            GlassTextButton(onClick = { onCancel?.invoke(); onDismiss() }) {
                Text(stringResource(Res.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            GlassTextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(stringResource(Res.string.done))
            }
        },
        content = content,
    )
}

@Composable
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
    ) {
        GlassCard(
            modifier = modifier
                .padding(24.dp)
                .fillMaxWidth()
                .imePadding(),
            style = GlassStyle(cornerRadius = 28.dp),
        ) {
            LazyColumn(content = content)
        }
    }
}

@Composable
fun InfoLabel(text: String) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = 8.dp),
) {
    Icon(
        painter = painterResource(Res.drawable.info),
        contentDescription = null,
        tint = GlassTokens.MutedContent,
        modifier = Modifier.padding(end = 8.dp),
    )
    Text(text, style = MaterialTheme.typography.bodySmall, color = GlassTokens.MutedContent)
}

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(),
    placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    autoFocus: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (String) -> Unit = {},
    textFields: List<Pair<String, TextFieldValue>>? = null,
    onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
    onDoneMultiple: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    autoDismiss: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
) {
    var legacyField by remember { mutableStateOf(initialTextFieldValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    DefaultDialog(
        modifier = modifier,
        onDismiss = onDismiss,
        icon = icon,
        title = title,
        buttons = {
            GlassTextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            Spacer(Modifier.width(8.dp))
            GlassTextButton(
                enabled = isInputValid(legacyField.text),
                onClick = {
                    onDone(legacyField.text)
                    if (autoDismiss) onDismiss()
                },
            ) { Text(stringResource(Res.string.done)) }
        },
    ) {
        if (textFields == null) {
            OutlinedTextField(
                value = legacyField,
                onValueChange = { legacyField = it },
                placeholder = placeholder ?: { Text("") },
                singleLine = singleLine,
                maxLines = maxLines,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        } else {
            textFields.forEachIndexed { index, (_, value) ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { onTextFieldsChange?.invoke(index, it) },
                    singleLine = singleLine,
                    maxLines = maxLines,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            GlassTextButton(
                onClick = {
                    onDoneMultiple?.invoke(textFields.map { it.second.text })
                    if (autoDismiss) onDismiss()
                },
            ) { Text(stringResource(Res.string.done)) }
        }
        if (extraContent != null) {
            Spacer(Modifier.height(12.dp))
            extraContent()
        }
    }
}

@Composable
private fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = LocalGlassState.current.accent
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.glassSurface(
            shape = RoundedCornerShape(20.dp),
            style = GlassStyle(
                containerColor = accent.copy(alpha = 0.18f),
                cornerRadius = 20.dp,
                elevation = 0.dp,
            ),
        ),
        content = content,
    )
}
