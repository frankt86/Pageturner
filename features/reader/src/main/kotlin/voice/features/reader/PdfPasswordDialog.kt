package voice.features.reader

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedSecureTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

/**
 * Asks for the password of a locked PDF. [wrongPassword] is set after the last one didn't open it.
 */
@Composable
internal fun PdfPasswordDialog(
  wrongPassword: Boolean,
  onOpen: (String) -> Unit,
  onCancel: () -> Unit,
) {
  // not saved with the screen, a password has no place in the saved state
  val passwordState = remember { TextFieldState() }
  val password = passwordState.text.toString()
  var showPassword by rememberSaveable { mutableStateOf(false) }
  val focus = remember { FocusRequester() }
  LaunchedEffect(focus) { focus.requestFocus() }
  val open = { if (password.isNotEmpty()) onOpen(password) }
  AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(stringResource(StringsR.string.reader_password)) },
    text = {
      OutlinedSecureTextField(
        state = passwordState,
        modifier = Modifier.focusRequester(focus).semantics { contentType = ContentType.Password },
        label = { Text(stringResource(StringsR.string.reader_password_field)) },
        textObfuscationMode =
          if (showPassword) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        trailingIcon = {
          IconButton(onClick = { showPassword = !showPassword }) {
            Icon(
              painter =
                painterResource(
                  if (showPassword) UiR.drawable.ic_visibility_off else UiR.drawable.ic_visibility,
                ),
              contentDescription =
                stringResource(
                  if (showPassword) {
                    StringsR.string.login_password_hide
                  } else {
                    StringsR.string.login_password_show
                  },
                ),
            )
          }
        },
        isError = wrongPassword,
        supportingText =
          if (wrongPassword) {
            {
              Text(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                text = stringResource(StringsR.string.reader_password_wrong),
              )
            }
          } else {
            null
          },
        keyboardOptions =
          KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
          ),
        onKeyboardAction = { open() },
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
