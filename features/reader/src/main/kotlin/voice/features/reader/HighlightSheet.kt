@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import voice.core.data.Highlight
import voice.core.data.HighlightColor
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

/** Changes to the color and note are kept when the sheet is swiped away, like with Done. */
@Composable
internal fun HighlightSheet(
  highlight: Highlight,
  text: String,
  onDone: (Highlight) -> Unit,
  onDelete: () -> Unit,
) {
  var color by rememberSaveable(highlight) { mutableStateOf(highlight.color) }
  var note by rememberSaveable(highlight) { mutableStateOf(highlight.note.orEmpty()) }
  val done = { onDone(highlight.copy(color = color, note = note.trim().ifEmpty { null })) }
  ModalBottomSheet(
    onDismissRequest = done,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
      Text(
        text = buildAnnotatedString {
          withStyle(SpanStyle(background = color.background())) { append(text) }
        },
        style = MaterialTheme.typography.bodyLarge.bookText(),
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(16.dp))
      ColorPicker(
        selected = color,
        onSelect = { color = it },
      )
      Spacer(Modifier.height(16.dp))
      OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text(stringResource(StringsR.string.reader_highlight_note)) },
        textStyle = MaterialTheme.typography.bodyLarge.bookText(),
        maxLines = 5,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(24.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
          onClick = onDelete,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Icon(VoiceIcons.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
          Spacer(Modifier.width(ButtonDefaults.IconSpacing))
          Text(stringResource(StringsR.string.reader_highlight_delete))
        }
        Spacer(Modifier.weight(1F))
        Button(onClick = done, shapes = ButtonDefaults.shapes()) {
          Icon(VoiceIcons.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
          Spacer(Modifier.width(ButtonDefaults.IconSpacing))
          Text(stringResource(StringsR.string.common_action_done))
        }
      }
    }
  }
}

@Composable
private fun ColorPicker(
  selected: HighlightColor,
  onSelect: (HighlightColor) -> Unit,
) {
  Row(
    modifier = Modifier.selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    HighlightColor.entries.forEach { color ->
      val name = highlightColorName(color)
      val isSelected = color == selected
      Box(
        modifier = Modifier
          .size(48.dp)
          .selectable(selected = isSelected, onClick = { onSelect(color) }, role = Role.RadioButton)
          .semantics { contentDescription = name }
          .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
          .padding(6.dp)
          .background(color.ink(), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        if (isSelected) {
          // the check mark, not only the ring, tells which one is picked
          Icon(VoiceIcons.Check, contentDescription = null, tint = Color.Black.copy(alpha = 0.7F))
        }
      }
    }
  }
}
