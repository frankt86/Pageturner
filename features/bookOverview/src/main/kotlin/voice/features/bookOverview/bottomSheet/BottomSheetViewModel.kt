package voice.features.bookOverview.bottomSheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.category
import voice.core.data.repo.BookRepository
import voice.core.playback.PlayerController
import voice.core.playback.overlay
import voice.features.bookOverview.di.BookActionsScope
import voice.features.bookOverview.overview.toItemViewState
import voice.features.bookOverview.select.BookSelectionRequests
import voice.navigation.Destination
import voice.navigation.Navigator

@SingleIn(BookActionsScope::class)
@Inject
class BottomSheetViewModel(
  private val bookId: BookId,
  private val viewModels: Set<@JvmSuppressWildcards BottomSheetItemViewModel>,
  private val repo: BookRepository,
  private val navigator: Navigator,
  private val playerController: PlayerController,
  private val bookSelectionRequests: BookSelectionRequests,
) {

  private val scope = MainScope()

  /**
   * Null until the book is loaded. Follows the book from then on, e.g. its progress while it plays.
   */
  @Composable
  internal fun state(): EditBookBottomSheetState? {
    val book = remember { book() }.collectAsState(initial = null).value
    val items =
      produceState<List<BottomSheetItem>?>(initialValue = null) {
          value = viewModels.flatMap { it.items(bookId) }.toSet().sorted()
        }
        .value
    val canSelect = remember { bookSelectionRequests.canSelect }.collectAsState(null).value
    if (book == null || items == null || canSelect == null) {
      return null
    }
    val select = listOfNotNull(BottomSheetItem.Select.takeIf { canSelect })
    return EditBookBottomSheetState(
      book = book.toItemViewState(),
      category = book.category,
      items = (items + select).toSet().sorted(),
    )
  }

  private fun book(): Flow<Book> {
    val book =
      repo
        .flow(bookId)
        .onEach { book ->
          // with no book there is no menu to show, and an invisible one would swallow the next back
          // press
          if (book == null) onClose()
        }
        .filterNotNull()
    // the stored position of the playing book lags behind
    return book.combine(playerController.livePlaybackStateFlow(bookId)) { book, livePlaybackState ->
      livePlaybackState?.let(book::overlay) ?: book
    }
  }

  /**
   * Returns once the item is handled, e.g. when the sheet it opens has its state. The work itself
   * runs in this scope, so it completes even if the caller goes away.
   */
  internal suspend fun onItemClick(item: BottomSheetItem) {
    scope
      .launch {
        when (item) {
          BottomSheetItem.Select -> bookSelectionRequests.select(listOf(bookId))
          else ->
            viewModels.forEach {
              it.onItemClick(bookId, item)
            }
        }
      }
      .join()
  }

  internal fun onClose() {
    navigator.remove(Destination.BookActions(bookId))
  }
}
