@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.opds.folder

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voice.core.data.folders.FolderType
import voice.core.logging.api.Logger
import voice.core.ui.ShapedIcon
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

internal data class DownloadFolderSheetState(
  val folders: List<DownloadFolderItem>,
  /** The picked folder can't take downloads. */
  val failed: Boolean = false,
  /** A picked folder is being added to the library. */
  val busy: Boolean = false,
)

internal data class DownloadFolderItem(
  val uri: Uri,
  val name: String,
  val type: FolderType,
  val selected: Boolean,
)

/** Chooses the folder of the library that downloads from the catalogs go into, or adds a new one. */
@Composable
internal fun DownloadFolderSheet(
  state: DownloadFolderSheetState,
  onChoose: (Uri) -> Unit,
  onPick: (Uri) -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) onPick(uri)
  }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberBottomSheetState(
      initialValue = Hidden,
      enabledValues = setOf(Hidden, Expanded),
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)
        .padding(bottom = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      ShapedIcon(
        icon = VoiceIcons.Folder,
        shape = MaterialShapes.Cookie6Sided,
        containerColor = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        size = 64.dp,
      )
      Spacer(Modifier.height(16.dp))
      Text(
        text = stringResource(StringsR.string.opds_download_folder_title),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = stringResource(StringsR.string.opds_download_folder_message),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(24.dp))
      if (state.folders.isNotEmpty()) {
        Column(
          modifier = Modifier.selectableGroup(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          state.folders.forEach { folder ->
            FolderOption(
              folder = folder,
              onClick = { onChoose(folder.uri) },
            )
          }
        }
        Spacer(Modifier.height(16.dp))
      }
      if (state.failed) {
        Text(
          modifier = Modifier.padding(horizontal = 8.dp),
          text = stringResource(StringsR.string.opds_download_folder_error),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.error,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
      }
      if (state.busy) {
        LoadingIndicator(Modifier.size(48.dp))
      } else {
        val onClick = {
          try {
            treeLauncher.launch(null)
          } catch (e: ActivityNotFoundException) {
            Logger.w(e, "Could not pick a download folder")
          }
        }
        val modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = ButtonDefaults.MediumContainerHeight)
        // without a folder of books in the library, picking one is the way
        if (state.folders.isEmpty()) {
          Button(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier) {
            Text(stringResource(StringsR.string.folder_add_action_folder))
          }
        } else {
          FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier) {
            Text(stringResource(StringsR.string.opds_download_folder_new))
          }
        }
      }
    }
  }
}

@Composable
private fun FolderOption(
  folder: DownloadFolderItem,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .selectable(selected = folder.selected, onClick = onClick, role = Role.RadioButton),
    shape = RoundedCornerShape(24.dp),
    color = if (folder.selected) colors.secondaryContainer else colors.surfaceContainerHigh,
    contentColor = if (folder.selected) colors.onSecondaryContainer else colors.onSurface,
    border = if (folder.selected) BorderStroke(2.dp, colors.primary) else null,
  ) {
    Row(
      modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RadioButton(selected = folder.selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
      Column(Modifier.weight(1F)) {
        Text(
          text = folder.name,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (folder.type == FolderType.Author) VoiceIcons.Person else VoiceIcons.LibraryBooks,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.size(6.dp))
          Text(
            text = stringResource(
              if (folder.type == FolderType.Author) {
                StringsR.string.folder_mode_author_label
              } else {
                StringsR.string.folder_mode_root_label
              },
            ),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
  }
}
