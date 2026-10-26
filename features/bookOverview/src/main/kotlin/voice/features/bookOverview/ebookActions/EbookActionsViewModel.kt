package voice.features.bookOverview.ebookActions

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.Ebook
import voice.core.data.EbookPosition
import voice.core.data.store.EbooksStore
import voice.core.logging.api.Logger
import voice.features.bookOverview.editTitle.EditBookTitleState
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.category
import voice.features.bookOverview.overview.toItemViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import java.io.File
import kotlin.uuid.Uuid

@AssistedInject
class EbookActionsViewModel(
  private val store: EbooksStore,
  private val application: Application,
  private val navigator: Navigator,
  private val dispatcherProvider: DispatcherProvider,
  @Assisted
  private val uri: Uri,
) {

  private val scope = MainScope(dispatcherProvider)

  internal var renameState by mutableStateOf<EditBookTitleState?>(null)
    private set

  /** Null until the book is loaded. */
  @Composable
  internal fun state(): EbookActionsState? {
    val ebook = remember { ebook() }.collectAsState(initial = null).value ?: return null
    return EbookActionsState(
      book = ebook.toItemViewState(),
      category = ebook.category,
    )
  }

  private fun ebook(): Flow<Ebook> {
    return store.data
      .map { ebooks -> ebooks.firstOrNull { it.uri == uri } }
      .onEach { ebook ->
        // with no book there is no menu to show, and an invisible one would swallow the next back press
        if (ebook == null) onClose()
      }
      .filterNotNull()
  }

  internal fun onRename(book: BookOverviewItemViewState) {
    renameState = EditBookTitleState(title = book.name, bookId = book.id, cover = book.cover)
  }

  internal fun onRenameChange(title: String) {
    renameState = renameState?.copy(title = title)
  }

  internal fun onRenameDismiss() {
    renameState = null
  }

  internal fun onRenameConfirm() {
    val state = renameState ?: return
    renameState = null
    if (!state.confirmButtonEnabled) return
    val title = state.title.trim()
    update { ebook -> ebook.copy(customName = title.takeIf { it != ebook.name }) }
  }

  internal fun onStatusChange(category: BookOverviewCategory) {
    val now = System.currentTimeMillis()
    update { ebook ->
      when (category) {
        BookOverviewCategory.NOT_STARTED -> ebook.copy(position = EbookPosition(), progress = 0F, lastOpened = 0)
        // a finished book starts over
        BookOverviewCategory.CURRENT -> if (ebook.category == BookOverviewCategory.FINISHED) {
          ebook.copy(position = EbookPosition(), progress = 0F, lastOpened = now)
        } else {
          ebook.copy(lastOpened = now)
        }
        BookOverviewCategory.FINISHED -> ebook.copy(progress = 1F, lastOpened = now)
      }
    }
  }

  internal fun onCoverPicked(image: Uri) {
    scope.launch {
      val coversDir = File(application.filesDir, "ebookCovers")
      val cover = withContext(dispatcherProvider.io) {
        try {
          val file = coversDir.apply { mkdirs() }.resolve(Uuid.random().toString())
          application.contentResolver.openInputStream(image)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
            file
          }
        } catch (e: Exception) {
          Logger.w(e, "Could not save the picked cover")
          null
        }
      } ?: return@launch
      val previous = ebook().first().cover
      store.updateData { ebooks ->
        ebooks.map { if (it.uri == uri) it.copy(cover = cover.absolutePath) else it }
      }
      withContext(dispatcherProvider.io) {
        previous?.let(::File)?.takeIf { it.parentFile == coversDir }?.delete()
      }
    }
  }

  internal fun onInternetCover() {
    navigator.goTo(Destination.EbookCoverFromInternet(uri))
  }

  internal fun onRemove() {
    update { it.copy(hidden = true) }
  }

  internal fun onClose() {
    navigator.remove(Destination.EbookActions(uri))
  }

  private fun update(transform: (Ebook) -> Ebook) {
    scope.launch {
      store.updateData { ebooks ->
        ebooks.map { if (it.uri == uri) transform(it) else it }
      }
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(uri: Uri): EbookActionsViewModel
  }
}

internal data class EbookActionsState(
  val book: BookOverviewItemViewState,
  val category: BookOverviewCategory,
)
