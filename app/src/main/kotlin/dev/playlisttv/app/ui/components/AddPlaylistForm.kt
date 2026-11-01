package dev.playlisttv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import dev.playlisttv.app.ui.theme.ThemeHighlight
import dev.playlisttv.app.ui.theme.White

/**
 * Matches the original app's empty-state box (`#151e29` background, teal
 * "Add playlist" button — MECHANICS.md "Look").
 */
@Composable
fun AddPlaylistForm(
    busy: Boolean,
    error: String?,
    onSubmit: (name: String, source: String) -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .background(Color(0xFF151E29), RoundedCornerShape(12.dp))
            .padding(32.dp)
            .width(640.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Add a playlist", style = MaterialTheme.typography.titleLarge, color = White)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().dpadMovesFocus(focusManager),
            colors = fieldColors(),
        )
        OutlinedTextField(
            value = source,
            onValueChange = { source = it },
            label = { Text("Playlist URL or file path") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().dpadMovesFocus(focusManager),
            colors = fieldColors(),
        )
        if (error != null) {
            Text(text = error, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onSubmit(name, source) },
                enabled = !busy,
                colors = ButtonDefaults.colors(containerColor = ThemeHighlight, contentColor = White),
            ) {
                Text(if (busy) "Loading…" else "Add playlist")
            }
            if (onCancel != null) {
                OutlinedButton(onClick = onCancel, enabled = !busy) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * In a single-line text field, Android's own key handling treats D-pad
 * Up/Down as "move the cursor to the start/end of the text" and consumes the
 * key before Compose's focus-traversal system ever sees it — so a remote's
 * Down button leaves you stuck in the field instead of reaching the next
 * one. Intercept it in the preview (tunneling) phase and drive focus
 * movement ourselves; Left/Right are untouched so in-field cursor movement
 * still works.
 */
private fun Modifier.dpadMovesFocus(focusManager: FocusManager): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionDown -> {
            focusManager.moveFocus(FocusDirection.Down)
            true
        }
        Key.DirectionUp -> {
            focusManager.moveFocus(FocusDirection.Up)
            true
        }
        else -> false
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    focusedBorderColor = ThemeHighlight,
    cursorColor = ThemeHighlight,
    focusedLabelColor = ThemeHighlight,
)
