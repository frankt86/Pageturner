@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.opds.catalogs

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.opds.CatalogType
import voice.core.ui.CenteredContent
import voice.core.ui.ShapedIcon
import voice.core.ui.VoiceTheme
import voice.core.ui.icons.VoiceIcons
import voice.core.ui.plus
import voice.core.ui.segmentedShape
import voice.features.opds.folder.DownloadFolderSheet
import voice.features.opds.folder.DownloadFolderSheetState
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface OpdsCatalogsGraph {
  val opdsCatalogsViewModel: OpdsCatalogsViewModel
}

@BindingContainer
@ContributesTo(AppScope::class)
object OpdsCatalogsProvider {

  @Provides
  @IntoSet
  fun opdsCatalogsNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.OpdsCatalogs> { key ->
    NavEntry(key) {
      OpdsCatalogsScreen()
    }
  }
}

@Composable
private fun OpdsCatalogsScreen() {
  val viewModel = retain { rootGraphAs<OpdsCatalogsGraph>().opdsCatalogsViewModel }
  OpdsCatalogsScreen(
    catalogs = viewModel.catalogs(),
    onBack = viewModel::onBack,
    onAdd = viewModel::onAdd,
    onOpen = viewModel::onOpen,
    onEdit = viewModel::onEdit,
    editor = viewModel.editor,
    onEditorChange = viewModel::onEditorChange,
    onSave = viewModel::onSave,
    onRemove = viewModel::onRemove,
    onEditorDismiss = viewModel::onEditorDismiss,
    downloadFolder = viewModel.downloadFolder(),
    onDownloadFolderClick = viewModel::onDownloadFolderClick,
    downloadFolderSheet = viewModel.downloadFolderSheet(),
    onDownloadFolderChoose = viewModel::onDownloadFolderChoose,
    onDownloadFolderPick = viewModel::onDownloadFolderPick,
    onDownloadFolderDismiss = viewModel::onDownloadFolderDismiss,
  )
}

@Composable
private fun OpdsCatalogsScreen(
  catalogs: List<CatalogViewState>?,
  onBack: () -> Unit,
  onAdd: () -> Unit,
  onOpen: (String) -> Unit,
  onEdit: (String) -> Unit,
  editor: CatalogEditorState?,
  onEditorChange: (CatalogEditorState) -> Unit,
  onSave: () -> Unit,
  onRemove: () -> Unit,
  onEditorDismiss: () -> Unit,
  downloadFolder: String?,
  onDownloadFolderClick: () -> Unit,
  downloadFolderSheet: DownloadFolderSheetState?,
  onDownloadFolderChoose: (Uri) -> Unit,
  onDownloadFolderPick: (Uri) -> Unit,
  onDownloadFolderDismiss: () -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      LargeFlexibleTopAppBar(
        title = { Text(stringResource(StringsR.string.opds_title)) },
        navigationIcon = {
          val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
          IconButton(onClick = onBack) {
            Icon(
              modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1F else 1F },
              imageVector = VoiceIcons.ArrowBack,
              contentDescription = stringResource(StringsR.string.common_action_back),
            )
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
    floatingActionButton = {
      val text = stringResource(StringsR.string.opds_catalog_add)
      ExtendedFloatingActionButton(
        text = { Text(text) },
        // the text alone doesn't reach accessibility services
        icon = { Icon(VoiceIcons.Add, contentDescription = text) },
        onClick = onAdd,
      )
    },
  ) { contentPadding ->
    when {
      catalogs == null -> Unit
      catalogs.isEmpty() -> EmptyCatalogs(Modifier.padding(contentPadding))
      else -> CenteredContent(Modifier.fillMaxSize()) { horizontalPadding ->
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          // leaves room to scroll the last catalog out from under the add button
          contentPadding = contentPadding + PaddingValues(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          item(key = "downloadFolder") {
            DownloadFolderRow(
              name = downloadFolder,
              onClick = onDownloadFolderClick,
              modifier = Modifier.padding(bottom = 14.dp),
            )
          }
          itemsIndexed(catalogs, key = { _, catalog -> catalog.id }) { index, catalog ->
            CatalogRow(
              catalog = catalog,
              index = index,
              count = catalogs.size,
              onOpen = { onOpen(catalog.id) },
              onEdit = { onEdit(catalog.id) },
            )
          }
        }
      }
    }
    if (editor != null) {
      CatalogEditorSheet(
        state = editor,
        onChange = onEditorChange,
        onSave = onSave,
        onRemove = onRemove,
        onDismiss = onEditorDismiss,
      )
    }
    if (downloadFolderSheet != null) {
      DownloadFolderSheet(
        state = downloadFolderSheet,
        onChoose = onDownloadFolderChoose,
        onPick = onDownloadFolderPick,
        onDismiss = onDownloadFolderDismiss,
      )
    }
  }
}

// where the books of every catalog download to
@Composable
private fun DownloadFolderRow(
  name: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val shape = segmentedShape(0, 1)
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .background(colors.surfaceContainer, shape)
      .clickable(onClick = onClick)
      .heightIn(min = 72.dp)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ShapedIcon(
      icon = VoiceIcons.Folder,
      shape = MaterialShapes.Cookie6Sided,
      containerColor = colors.primaryContainer,
      contentColor = colors.onPrimaryContainer,
    )
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1F)) {
      Text(
        text = stringResource(StringsR.string.opds_download_folder_title),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = name ?: stringResource(StringsR.string.opds_download_folder_none),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.width(8.dp))
    Icon(
      modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1F else 1F },
      imageVector = VoiceIcons.ChevronRight,
      contentDescription = null,
      tint = colors.onSurfaceVariant,
    )
  }
}

@Composable
private fun CatalogRow(
  catalog: CatalogViewState,
  index: Int,
  count: Int,
  onOpen: () -> Unit,
  onEdit: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val shape = segmentedShape(index, count)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(colors.surfaceContainer, shape)
      .clickable(onClick = onOpen)
      .heightIn(min = 72.dp)
      .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CatalogIcon(catalog.type)
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1F)) {
      Text(
        text = catalog.name,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = catalog.address,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    IconButton(onClick = onEdit) {
      Icon(VoiceIcons.MoreVert, contentDescription = stringResource(StringsR.string.common_action_edit))
    }
  }
}

@Composable
private fun CatalogIcon(
  type: CatalogType,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  when (type) {
    CatalogType.Opds -> ShapedIcon(
      modifier = modifier,
      icon = VoiceIcons.LibraryBooks,
      shape = MaterialShapes.Pentagon,
      containerColor = colors.secondary,
      contentColor = colors.onSecondary,
    )
    CatalogType.Komga -> ShapedIcon(
      modifier = modifier,
      icon = VoiceIcons.Dns,
      shape = MaterialShapes.Cookie9Sided,
      containerColor = colors.tertiary,
      contentColor = colors.onTertiary,
    )
  }
}

@Composable
private fun EmptyCatalogs(modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 32.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 420.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      ShapedIcon(
        icon = VoiceIcons.LibraryBooks,
        shape = MaterialShapes.Pentagon,
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        size = 96.dp,
      )
      Spacer(Modifier.height(24.dp))
      Text(
        text = stringResource(StringsR.string.opds_catalogs_empty_title),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = stringResource(StringsR.string.opds_catalogs_empty_message),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      // keeps the text clear of the add button
      Spacer(Modifier.height(72.dp))
    }
  }
}

@Composable
@Preview
private fun OpdsCatalogsPreview() {
  VoiceTheme {
    OpdsCatalogsScreen(
      catalogs = listOf(
        CatalogViewState(id = "1", name = "Calibre-Web", address = "books.example.com/opds", type = CatalogType.Opds),
        CatalogViewState(id = "2", name = "Comics", address = "komga.example.com", type = CatalogType.Komga),
      ),
      onBack = {},
      onAdd = {},
      onOpen = {},
      onEdit = {},
      editor = null,
      onEditorChange = {},
      onSave = {},
      onRemove = {},
      onEditorDismiss = {},
      downloadFolder = "Books",
      onDownloadFolderClick = {},
      downloadFolderSheet = null,
      onDownloadFolderChoose = {},
      onDownloadFolderPick = {},
      onDownloadFolderDismiss = {},
    )
  }
}

@Composable
@Preview
private fun OpdsCatalogsEmptyPreview() {
  VoiceTheme {
    OpdsCatalogsScreen(
      catalogs = emptyList(),
      onBack = {},
      onAdd = {},
      onOpen = {},
      onEdit = {},
      editor = null,
      onEditorChange = {},
      onSave = {},
      onRemove = {},
      onEditorDismiss = {},
      downloadFolder = "Books",
      onDownloadFolderClick = {},
      downloadFolderSheet = null,
      onDownloadFolderChoose = {},
      onDownloadFolderPick = {},
      onDownloadFolderDismiss = {},
    )
  }
}
