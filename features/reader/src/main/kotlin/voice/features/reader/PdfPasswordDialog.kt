package voice.features.reader

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

/** Asks for the password of a locked PDF. [wrongPassword] is set after the last one didn't open it. */
@Composable
internal fun PdfPasswordDialog(
  wrongPassword: Boolean,
  onOpen: (String) -> Unit,
  onCancel: () -> Unit,
) {
  // not saved with the screen, a password has no place in the saved state
  var password by remember { mutableStateOf("") }
  var showPassword by rememberSaveable { mutableStateOf(false) }
  val focus = remember { FocusRequester() }
  LaunchedEffect(focus) { focus.requestFocus() }
  val open = { if (password.isNotEmpty()) onOpen(password) }
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(stringResource(StringsR.string.reader_password)) },
    text = {
      OutlinedTextField(
        modifier = Modifier
          .focusRequester(focus)
          .semantics { contentType = ContentType.Password },
        value = password,
        onValueChange = { password = it },
        singleLine = true,
        label = { Text(stringResource(StringsR.string.reader_password_field)) },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
          IconButton(onClick = { showPassword = !showPassword }) {
            Icon(
              imageVector = if (showPassword) VoiceIcons.VisibilityOff else VoiceIcons.Visibility,
              contentDescription = stringResource(
                if (showPassword) {
                  StringsR.string.audiobookshelf_login_password_hide
                } else {
                  StringsR.string.audiobookshelf_login_password_show
                },
              ),
            )
          }
        },
        isError = wrongPassword,
        supportingText = if (wrongPassword) {
          {
            Text(
              modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
              text = stringResource(StringsR.string.reader_password_wrong),
            )
          }
        } else {
          null
        },
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Password,
          imeAction = ImeAction.Done,
          autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { open() }),
      )
    },
    confirmButton = {
      TextButton(onClick = open, enabled = password.isNotEmpty()) {
        Text(stringResource(StringsR.string.reader_password_open))
      }
    },
    dismissButton = {
      TextButton(onClick = onCancel) {
        Text(stringResource(StringsR.string.common_dialog_cancel))
      }
    },
  )
}
