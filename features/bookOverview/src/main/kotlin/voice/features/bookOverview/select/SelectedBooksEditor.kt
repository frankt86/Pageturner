package voice.features.bookOverview.select

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.CustomMetadata
import voice.core.data.Ebook
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore
import voice.features.bookOverview.overview.LibraryBook
import voice.features.bookOverview.overview.bookId
import voice.features.bookOverview.overview.update

enum class BookField {
  Series,
  Author,
}

internal data class BooksEdited(
  val field: BookField,
  val count: Int,
  val undo: Map<BookId, String?>,
)

@Inject
class SelectedBooksEditor(
  private val repo: BookRepository,
  private val ebooksStore: EbooksStore,
) {

  internal suspend fun set(
    field: BookField,
    ids: Collection<BookId>,
    value: String,
  ): BooksEdited {
    return edit(field, ids.associateWith { value.trim() })
  }

  internal suspend fun undo(edited: BooksEdited): BooksEdited {
    return edit(edited.field, edited.undo)
  }

  private suspend fun edit(
    field: BookField,
    values: Map<BookId, String?>,
  ): BooksEdited {
    val books = repo.all().filter { it.id in values }
    val undo = mutableMapOf<BookId, String?>()
    books.forEach { book ->
      // the undo puts back the edit, or lets the book follow its files again
      undo[book.id] = field.of(book.custom)
      repo.updateCustomMetadata(book.id) { field.set(it, values.getValue(book.id)) }
    }
    val ebooks = ebooksStore.data.first().filter { it.bookId in values }
    ebooks.forEach { undo[it.bookId] = field.ofCustom(it) }
    ebooksStore.update(ebooks.map { it.bookId }) { field.set(it, values.getValue(it.bookId)) }
    return BooksEdited(
      field = field,
      count = undo.size,
      undo = undo,
    )
  }
}

internal fun BookField.of(book: LibraryBook): String? =
  when (this) {
    BookField.Series -> book.seriesName
    BookField.Author -> book.author
  }

private fun BookField.ofCustom(ebook: Ebook): String? =
  when (this) {
    BookField.Series -> ebook.customSeries
    BookField.Author -> ebook.customAuthor
  }

// like the edit sheet, a name that's the one from the file follows the file again
private fun BookField.set(
  ebook: Ebook,
  value: String?,
): Ebook {
  val custom = value?.takeIf { it.isNotEmpty() }
  return when (this) {
    BookField.Series -> ebook.copy(customSeries = custom?.takeIf { it != ebook.series })
    BookField.Author -> ebook.copy(customAuthor = custom?.takeIf { it != ebook.author })
  }
}

private fun BookField.of(custom: CustomMetadata): String? =
  when (this) {
    BookField.Series -> custom.series
    BookField.Author -> custom.author
  }

private fun BookField.set(
  custom: CustomMetadata,
  value: String?,
): CustomMetadata =
  when (this) {
    BookField.Series -> custom.copy(series = value)
    BookField.Author -> custom.copy(author = value)
  }
