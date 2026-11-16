package voice.features.bookOverview.select

import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import voice.core.data.Book
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore
import voice.features.bookOverview.MemoryDataStore
import voice.features.bookOverview.ebook
import voice.features.bookOverview.overview.bookId

class SelectedBooksEditorTest {

  @Test
  fun `an ebook takes the name, and the undo lets it follow its file again`() = runTest {
    val ebook = ebook(series = "Sagaa").copy(customAuthor = "Mine")
    val store = EbooksStore(MemoryDataStore(listOf(ebook)))
    val editor = editor(ebooks = store)

    val series = editor.set(BookField.Series, listOf(ebook.bookId), " Saga ")
    assertEquals(expected = 1, actual = series.count)
    assertEquals(expected = "Saga", actual = store.data.first().single().shownSeries)

    val _ = editor.undo(series)
    assertEquals(expected = null, actual = store.data.first().single().customSeries)

    val author = editor.set(BookField.Author, listOf(ebook.bookId), "Author")
    // the author of the file, so the book follows the file again
    assertEquals(expected = null, actual = store.data.first().single().customAuthor)

    val _ = editor.undo(author)
    assertEquals(expected = "Mine", actual = store.data.first().single().customAuthor)
  }

  private fun editor(
    vararg books: Book,
    ebooks: EbooksStore = EbooksStore(MemoryDataStore(emptyList())),
  ): SelectedBooksEditor {
    val repo = mockk<BookRepository> { coEvery { all() } returns books.toList() }
    return SelectedBooksEditor(repo, ebooks)
  }
}
