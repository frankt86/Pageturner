package voice.features.opds.catalogs

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.opds.CatalogType
import voice.core.opds.FeedResult
import voice.core.opds.OpdsCatalog
import voice.core.opds.OpdsCatalogs
import voice.core.opds.download.OpdsDownloadFolders
import voice.features.opds.folder.DownloadFolderChooser
import voice.features.opds.folder.DownloadFolderSheetState
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.uuid.Uuid

@Inject
class OpdsCatalogsViewModel(
  private val catalogs: OpdsCatalogs,
  private val navigator: Navigator,
  downloadFolders: OpdsDownloadFolders,
  dispatcherProvider: DispatcherProvider,
) {

  private val scope = MainScope(dispatcherProvider)
  private val folderChooser = DownloadFolderChooser(downloadFolders, scope)

  internal var editor by mutableStateOf<CatalogEditorState?>(null)
    private set

  /** Null until the catalogs are read. */
  @Composable
  internal fun catalogs(): List<CatalogViewState>? {
    val all = remember { catalogs.all }.collectAsState(initial = null).value ?: return null
    return all.map { catalog ->
      CatalogViewState(
        id = catalog.id,
        name = catalog.name,
        address = catalog.url.substringAfter("://").trimEnd('/'),
        type = catalog.type,
      )
    }
  }

  /** The name of the folder downloads go to, or null while there's none. */
  @Composable
  internal fun downloadFolder(): String? = folderChooser.current()?.name

  @Composable
  internal fun downloadFolderSheet(): DownloadFolderSheetState? = folderChooser.sheet()

  internal fun onDownloadFolderClick() {
    folderChooser.show()
  }

  internal fun onDownloadFolderChoose(uri: Uri) {
    folderChooser.onChoose(uri)
  }

  internal fun onDownloadFolderPick(uri: Uri) {
    folderChooser.onPick(uri)
  }

  internal fun onDownloadFolderDismiss() {
    folderChooser.onDismiss()
  }

  internal fun onAdd() {
    editor = CatalogEditorState()
  }

  internal fun onEdit(id: String) {
    scope.launch {
      val catalog = catalogs.catalog(id) ?: return@launch
      editor = CatalogEditorState(
        id = catalog.id,
        name = catalog.name,
        url = catalog.url,
        type = catalog.type,
        username = catalog.username.orEmpty(),
        password = catalog.password.orEmpty(),
        syncProgress = catalog.syncProgress,
      )
    }
  }

  internal fun onEditorChange(state: CatalogEditorState) {
    if (editor?.busy == true) return
    editor = state.copy(error = null)
  }

  internal fun onEditorDismiss() {
    editor = null
  }

  internal fun onSave() {
    val state = editor ?: return
    if (state.busy || !state.canSave) return
    editor = state.copy(busy = true, error = null)
    scope.launch {
      val result = catalogs.save(
        OpdsCatalog(
          id = state.id ?: Uuid.random().toString(),
          name = state.name,
          url = state.url,
          type = state.type,
          username = state.username,
          password = state.password,
          syncProgress = state.syncProgress,
        ),
      )
      // closed while the catalog was checked
      val current = editor ?: return@launch
      editor = when (result) {
        is FeedResult.Success -> null
        FeedResult.WrongCredentials -> current.copy(busy = false, error = CatalogError.WrongCredentials)
        FeedResult.Unreachable -> current.copy(busy = false, error = CatalogError.Unreachable)
        FeedResult.NotACatalog -> current.copy(busy = false, error = CatalogError.NotACatalog)
      }
    }
  }

  internal fun onRemove() {
    val id = editor?.id ?: return
    editor = null
    scope.launch {
      catalogs.remove(id)
    }
  }

  internal fun onOpen(id: String) {
    navigator.goTo(Destination.OpdsFeed(catalogId = id))
  }

  internal fun onBack() {
    navigator.goBack()
  }
}

internal data class CatalogViewState(
  val id: String,
  val name: String,
  val address: String,
  val type: CatalogType,
)

internal data class CatalogEditorState(
  /** Null for a new catalog. */
  val id: String? = null,
  val name: String = "",
  val url: String = "",
  val type: CatalogType = CatalogType.Opds,
  val username: String = "",
  val password: String = "",
  val syncProgress: Boolean = true,
  val busy: Boolean = false,
  val error: CatalogError? = null,
) {
  val canSave: Boolean get() = url.isNotBlank()
}

internal enum class CatalogError {
  Unreachable,
  WrongCredentials,
  NotACatalog,
}
