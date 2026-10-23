package voice.features.bookOverview.overview

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import voice.core.data.BookId
import voice.core.data.EbookFormat

@Immutable
data class BookOverviewViewState(
  val books: Map<BookOverviewCategory, Map<BookId, State<BookOverviewItemViewState>>>,
  val currentBookId: BookId?,
  val layoutMode: BookOverviewLayoutMode,
  val playButtonState: PlayButtonState?,
  val showEmptyLibrary: Boolean,
  val showSearchIcon: Boolean,
  val isLoading: Boolean,
  val showStoragePermissionBugCard: Boolean,
  val showFolderAccessBadge: Boolean,
  val showFolderPickerIcon: Boolean,
  val dialog: Dialog?,
  val ebooks: List<EbookItemViewState> = emptyList(),
) {

  companion object {
    val Loading = BookOverviewViewState(
      books = mapOf(),
      currentBookId = null,
      layoutMode = BookOverviewLayoutMode.List,
      playButtonState = null,
      showEmptyLibrary = false,
      showSearchIcon = false,
      isLoading = true,
      showStoragePermissionBugCard = false,
      showFolderAccessBadge = false,
      showFolderPickerIcon = true,
      dialog = null,
    )
  }

  enum class PlayButtonState {
    Playing,
    Paused,
  }

  enum class Dialog {
    FolderPickerMovedToSettings,
  }
}

@Immutable
data class EbookItemViewState(
  val uri: Uri,
  val name: String,
  val format: EbookFormat,
  val progress: Float,
)
