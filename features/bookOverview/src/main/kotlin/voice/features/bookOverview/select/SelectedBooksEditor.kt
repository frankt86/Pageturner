package voice.features.bookOverview.select

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.audiobookshelf.ServerBookEdit
import voice.core.audiobookshelf.ServerEdit
import voice.core.audiobookshelf.ServerLibrary
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.BookMetadata
import voice.core.data.CustomMetadata
import voice.core.data.Ebook
import voice.core.data.isRemote
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
  val failed: Set<BookId>,
  // the account can't change books, so trying again doesn't help
  val notAllowed: Boolean,
  val undo: Map<BookId, String?>,
)

@Inject
class SelectedBooksEditor(
  private val repo: BookRepository,
  private val serverLibrary: ServerLibrary,
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
    val failed = mutableSetOf<BookId>()
    var notAllowed = false
    var unreachable = false
    books.forEach { book ->
      val value = values.getValue(book.id)
      if (book.id.isRemote) {
        // each of the other books would only wait for the timeout as well
        val result =
          if (notAllowed || unreachable) {
            ServerEdit.Failed
          } else {
            serverLibrary.updateBook(book.id, serverEdit(book, field, value))
          }
        when (result) {
          // a local edit from before the server edits can cover what the server has
          ServerEdit.Saved -> undo[book.id] = field.of(book.content.metadata)
          ServerEdit.NotAllowed -> {
            failed += book.id
            notAllowed = true
          }
          ServerEdit.Unreachable -> {
            failed += book.id
            unreachable = true
          }
          ServerEdit.Failed -> failed += book.id
        }
      } else {
        // the undo puts back the edit, or lets the book follow its files again
        undo[book.id] = field.of(book.custom)
        repo.updateCustomMetadata(book.id) { field.set(it, value) }
      }
    }
    val ebooks = ebooksStore.data.first().filter { it.bookId in values }
    ebooks.forEach { undo[it.bookId] = field.ofCustom(it) }
    ebooksStore.update(ebooks.map { it.bookId }) { field.set(it, values.getValue(it.bookId)) }
    return BooksEdited(
      field = field,
      count = undo.size,
      failed = failed,
      notAllowed = notAllowed,
      undo = undo,
    )
  }
}

// the server has no "follow the files", so an empty name takes the series or author off
private fun serverEdit(
  book: Book,
  field: BookField,
  value: String?,
): ServerBookEdit =
  when (field) {
    // the part goes along, or the server would lose it
    BookField.Series -> ServerBookEdit(series = value.orEmpty(), part = book.content.metadata.part)
    BookField.Author -> ServerBookEdit(author = value.orEmpty())
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

private fun BookField.of(metadata: BookMetadata): String? =
  when (this) {
    BookField.Series -> metadata.series
    BookField.Author -> metadata.author
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
