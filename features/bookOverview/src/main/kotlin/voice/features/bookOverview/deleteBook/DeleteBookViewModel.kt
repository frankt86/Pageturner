package voice.features.bookOverview.deleteBook

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.BookId
import voice.core.data.isRemote
import voice.core.data.repo.BookRepository
import voice.core.logging.api.Logger
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.bottomSheet.BottomSheetItemViewModel
import voice.features.bookOverview.di.BookActionsScope

@SingleIn(BookActionsScope::class)
@ContributesIntoSet(BookActionsScope::class)
class DeleteBookViewModel(
  private val repo: BookRepository,
  private val bookDeleter: BookDeleter,
  dispatcherProvider: DispatcherProvider,
) : BottomSheetItemViewModel {

  private val scope = MainScope(dispatcherProvider)

  private val _state = mutableStateOf<DeleteBookViewState?>(null)
  internal val state: State<DeleteBookViewState?>
    get() = _state

  override suspend fun items(bookId: BookId): List<BottomSheetItem> {
    // Voice never deletes files on a server
    if (bookId.isRemote) return emptyList()
    return listOf(BottomSheetItem.DeleteBook)
  }

  override suspend fun onItemClick(
    bookId: BookId,
    item: BottomSheetItem,
  ) {
    if (item != BottomSheetItem.DeleteBook) return
    val book = repo.get(bookId)

    _state.value =
      DeleteBookViewState(
        ids = listOf(bookId),
        name = book?.name,
        cover = book?.coverUrl,
        fileCount = book?.chapters?.size,
        deleteCheckBoxChecked = false,
        fileToDelete = fileToDelete(bookId),
      )
  }

  internal fun onDismiss() {
    _state.value = null
  }

  internal fun onDeleteCheckBoxCheck(checked: Boolean) {
    _state.value = _state.value?.copy(deleteCheckBoxChecked = checked)
  }

  internal fun onConfirmDeletion() {
    val state = _state.value
    if (state != null) {
      check(state.confirmButtonEnabled)
      scope.launch { bookDeleter.delete(state.ids) }
    }
    _state.value = null
  }
}

/** The name of the file or folder of the book, as the listener knows it from their files. */
internal fun fileToDelete(bookId: BookId): String {
  return bookId.toUri().pathSegments.let { segments ->
    val result = segments.lastOrNull()?.removePrefix("primary:")
    if (result.isNullOrEmpty()) {
      Logger.w("Could not determine the file name to show (${segments.size} segments)")
      segments.joinToString(separator = "\"")
    } else {
      result
    }
  }
}

/**
 * The books to delete. One book shows its cover, name and file, several books only show how many
 * they are.
 */
data class DeleteBookViewState(
  val ids: List<BookId>,
  val name: String?,
  val cover: String?,
  val fileCount: Int?,
  val deleteCheckBoxChecked: Boolean,
  val fileToDelete: String?,
  val bookCount: Int? = null,
) {

  val confirmButtonEnabled = deleteCheckBoxChecked
}
