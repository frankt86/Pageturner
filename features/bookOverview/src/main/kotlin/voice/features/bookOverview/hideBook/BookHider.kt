package voice.features.bookOverview.hideBook

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore
import voice.features.bookOverview.overview.LibraryBook
import voice.features.bookOverview.overview.allLibraryBooks
import voice.features.bookOverview.overview.inSeries
import voice.features.bookOverview.overview.series
import voice.features.bookOverview.overview.update

@SingleIn(AppScope::class)
@Inject
class BookHider(
  private val repo: BookRepository,
  private val ebooksStore: EbooksStore,
) {

  // no replay, so only a library that's on screen while the books leave it offers the undo
  internal val hidden: SharedFlow<HiddenBooks>
    field = MutableSharedFlow<HiddenBooks>(extraBufferCapacity = 1)

  internal suspend fun hide(ids: List<BookId>) {
    val selected = ids.toSet()
    // the undo leaves the books that were hidden before hidden
    val books = allLibraryBooks(repo, ebooksStore).filter { it.id in selected && !it.hidden }
    if (books.isEmpty()) return
    setHidden(books, hidden = true)
    hidden.emit(HiddenBooks(books.map { it.id }, books.singleOrNull()?.name, series = false))
  }

  internal suspend fun hideSeries(name: String) {
    val series = allLibraryBooks(repo, ebooksStore).inSeries(name).series().singleOrNull() ?: return
    // the undo leaves the books that were hidden before hidden
    val books = series.books.filterNot { it.hidden }
    if (books.isEmpty()) return
    setHidden(books, hidden = true)
    hidden.emit(HiddenBooks(books.map { it.id }, series.name, series = true))
  }

  internal suspend fun showSeries(name: String) {
    setHidden(allLibraryBooks(repo, ebooksStore).inSeries(name), hidden = false)
  }

  internal suspend fun show(ids: List<BookId>) {
    val selected = ids.toSet()
    setHidden(allLibraryBooks(repo, ebooksStore).filter { it.id in selected }, hidden = false)
  }

  private suspend fun setHidden(
    books: List<LibraryBook>,
    hidden: Boolean,
  ) {
    books.filterIsInstance<LibraryBook.Audiobook>().forEach { book ->
      repo.updateCustomMetadata(book.id) { it.copy(hidden = hidden) }
    }
    ebooksStore.update(books.filterIsInstance<LibraryBook.Ebook>().map { it.id }) {
      it.copy(hidden = hidden)
    }
  }
}

internal data class HiddenBooks(
  val ids: List<BookId>,
  val name: String?,
  val series: Boolean,
)
