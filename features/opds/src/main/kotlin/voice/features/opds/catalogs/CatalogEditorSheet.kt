@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.opds.catalogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.core.opds.CatalogType
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@Composable
internal fun CatalogEditorSheet(
  state: CatalogEditorState,
  onChange: (CatalogEditorState) -> Unit,
  onSave: () -> Unit,
  onRemove: () -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState =
      rememberBottomSheetState(
        initialValue = Hidden,
        enabledValues = setOf(Hidden, Expanded),
      ),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        modifier = Modifier.fillMaxWidth(),
        text =
          stringResource(
            if (state.id == null) StringsR.string.opds_catalog_add
            else StringsR.string.common_action_edit,
          ),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
      )
      // LibriVox has a fixed address and needs no sign in, so only its name changes
      val libriVox = state.type == CatalogType.LibriVox
      if (!libriVox) {
        TypeChoice(
          selected = state.type,
          enabled = !state.busy,
          onSelect = { onChange(state.copy(type = it)) },
        )
        OutlinedTextField(
          modifier = Modifier.fillMaxWidth(),
          value = state.url,
          onValueChange = { onChange(state.copy(url = it)) },
          enabled = !state.busy,
          singleLine = true,
          label = { Text(stringResource(StringsR.string.opds_catalog_url)) },
          placeholder = {
            Text(
              stringResource(
                if (state.type == CatalogType.Komga) {
                  StringsR.string.opds_catalog_url_placeholder_komga
                } else {
                  StringsR.string.opds_catalog_url_placeholder
                },
              ),
            )
          },
          supportingText =
            if (state.type == CatalogType.Komga) {
              { Text(stringResource(StringsR.string.opds_catalog_komga_hint)) }
            } else {
              null
            },
          keyboardOptions =
            KeyboardOptions(
              keyboardType = KeyboardType.Uri,
              imeAction = ImeAction.Next,
              autoCorrectEnabled = false,
              capitalization = KeyboardCapitalization.None,
            ),
        )
      }
      OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.name,
        onValueChange = { onChange(state.copy(name = it)) },
        enabled = !state.busy,
        singleLine = true,
        label = { Text(stringResource(StringsR.string.opds_catalog_name)) },
        keyboardOptions =
          KeyboardOptions(
            imeAction = ImeAction.Next,
            capitalization = KeyboardCapitalization.Words,
          ),
      )
      if (!libriVox) {
        OutlinedTextField(
          modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
          value = state.username,
          onValueChange = { onChange(state.copy(username = it)) },
          enabled = !state.busy,
          singleLine = true,
          label = { Text(stringResource(StringsR.string.login_username)) },
          supportingText = { Text(stringResource(StringsR.string.opds_catalog_credentials_hint)) },
          keyboardOptions =
            KeyboardOptions(
              keyboardType = KeyboardType.Text,
              imeAction = ImeAction.Next,
              autoCorrectEnabled = false,
              capitalization = KeyboardCapitalization.None,
            ),
        )
        PasswordField(
          password = state.password,
          enabled = !state.busy,
          onPasswordChange = { onChange(state.copy(password = it)) },
          onDone = onSave,
        )
      }
      if (state.type == CatalogType.Komga) {
        SyncProgressRow(
          checked = state.syncProgress,
          enabled = !state.busy,
          onCheckedChange = { onChange(state.copy(syncProgress = it)) },
        )
      }
      state.error?.let { error ->
        Text(
          modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
          text =
            stringResource(
              when (error) {
                CatalogError.Unreachable -> StringsR.string.opds_catalog_error_unreachable
                CatalogError.WrongCredentials -> StringsR.string.opds_catalog_error_credentials
                CatalogError.NotACatalog -> StringsR.string.opds_catalog_error_not_catalog
              },
            ),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.error,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (state.id != null) {
          OutlinedButton(
            onClick = onRemove,
            enabled = !state.busy,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
            modifier = Modifier.weight(1F).heightIn(min = ButtonDefaults.MediumContainerHeight),
          ) {
            Text(stringResource(StringsR.string.opds_catalog_remove))
          }
        }
        Button(
          onClick = onSave,
          enabled = state.canSave,
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier.weight(1F).heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) {
          if (state.busy) {
            LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
          } else {
            Text(stringResource(StringsR.string.common_action_save))
          }
        }
      }
    }
  }
}

@Composable
private fun TypeChoice(
  selected: CatalogType,
  enabled: Boolean,
  onSelect: (CatalogType) -> Unit,
) {
  val options = listOf(CatalogType.Opds, CatalogType.Komga)
  Row(
    modifier = Modifier.fillMaxWidth().selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
  ) {
    options.forEachIndexed { index, option ->
      ToggleButton(
        checked = option == selected,
        onCheckedChange = { onSelect(option) },
        enabled = enabled,
        shapes =
          when (index) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
          },
        modifier = Modifier.weight(1F).semantics { role = Role.RadioButton },
      ) {
        Text(
          stringResource(
            when (option) {
              CatalogType.Opds -> StringsR.string.opds_catalog_type_opds
              CatalogType.Komga -> StringsR.string.opds_catalog_type_komga
              CatalogType.LibriVox -> StringsR.string.opds_catalog_type_opds
            },
          ),
        )
      }
    }
  }
}

@Composable
private fun SyncProgressRow(
  checked: Boolean,
  enabled: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(
          value = checked,
          enabled = enabled,
          role = Role.Switch,
          onValueChange = onCheckedChange,
        )
        .heightIn(min = 56.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1F)) {
      Text(
        text = stringResource(StringsR.string.opds_catalog_sync_progress),
        style = MaterialTheme.typography.bodyLarge,
      )
      Text(
        text = stringResource(StringsR.string.opds_catalog_sync_progress_summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.width(12.dp))
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
  }
}

@Composable
private fun PasswordField(
  password: String,
  enabled: Boolean,
  onPasswordChange: (String) -> Unit,
  onDone: () -> Unit,
) {
  var showPassword by rememberSaveable { mutableStateOf(false) }
  val passwordState = rememberTextFieldState(password)
  LaunchedEffect(passwordState) {
    snapshotFlow { passwordState.text.toString() }.collect { onPasswordChange(it) }
  }
  OutlinedSecureTextField(
    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
    state = passwordState,
    enabled = enabled,
    label = { Text(stringResource(StringsR.string.login_password)) },
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
    keyboardOptions =
      KeyboardOptions(
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        autoCorrectEnabled = false,
      ),
    onKeyboardAction = { onDone() },
  )
}
