package voice.features.bookOverview.select

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import voice.core.audiobookshelf.ServerEdit
import voice.core.audiobookshelf.ServerLibrary
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore
import voice.features.bookOverview.MemoryDataStore
import voice.features.bookOverview.book
import voice.features.bookOverview.ebook
import voice.features.bookOverview.overview.bookId

class SelectedBooksEditorTest {

  private val serverLibrary = mockk<ServerLibrary>()

  @Test
  fun `one book that fails on the server doesn't stop the others`() = runTest {
    val failing = serverBook()
    val other = serverBook()
    coEvery { serverLibrary.updateBook(failing.id, any()) } returns ServerEdit.Failed
    coEvery { serverLibrary.updateBook(other.id, any()) } returns ServerEdit.Saved

    val edited = editor(failing, other).set(BookField.Author, listOf(failing.id, other.id), "New")

    assertEquals(expected = setOf(failing.id), actual = edited.failed)
    assertEquals(expected = 1, actual = edited.count)
  }

  @Test
  fun `once the server can't be reached, the other server books are not sent`() = runTest {
    val books = List(3) { serverBook() }
    coEvery { serverLibrary.updateBook(any(), any()) } returns ServerEdit.Unreachable

    val edited = editor(*books.toTypedArray()).set(BookField.Author, books.map { it.id }, "New")

    assertEquals(expected = books.map { it.id }.toSet(), actual = edited.failed)
    assertEquals(expected = false, actual = edited.notAllowed)
    coVerify(exactly = 1) {
      val _ = serverLibrary.updateBook(any(), any())
    }
  }

  @Test
  fun `an ebook takes the name, and the undo lets it follow its file again`() = runTest {
    val ebook = ebook(series = "Sagaa").copy(customAuthor = "Mine")
    val store = EbooksStore(MemoryDataStore(listOf(ebook)))
    val editor = editor(ebooks = store)

    val series = editor.set(BookField.Series, listOf(ebook.bookId), " Saga ")
    assertEquals(expected = 1, actual = series.count)
    assertEquals(expected = "Saga", actual = store.data.first().single().shownSeries)

    editor.undo(series)
    assertEquals(expected = null, actual = store.data.first().single().customSeries)

    val author = editor.set(BookField.Author, listOf(ebook.bookId), "Author")
    // the author of the file, so the book follows the file again
    assertEquals(expected = null, actual = store.data.first().single().customAuthor)

    editor.undo(author)
    assertEquals(expected = "Mine", actual = store.data.first().single().customAuthor)
  }

  private fun editor(
    vararg books: Book,
    ebooks: EbooksStore = EbooksStore(MemoryDataStore(emptyList())),
  ): SelectedBooksEditor {
    val repo = mockk<BookRepository> { coEvery { all() } returns books.toList() }
    return SelectedBooksEditor(repo, serverLibrary, ebooks)
  }

  private fun serverBook(): Book = book(id = BookId("abs://item/${Uuid.random()}"))
}
