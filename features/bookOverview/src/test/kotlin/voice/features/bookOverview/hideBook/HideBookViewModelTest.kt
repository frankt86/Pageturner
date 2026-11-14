package voice.features.bookOverview.hideBook

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import voice.core.data.CustomMetadata
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore
import voice.features.bookOverview.MemoryDataStore
import voice.features.bookOverview.book
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.ebook
import voice.features.bookOverview.overview.bookId

class HideBookViewModelTest {

  private val book = book(name = "Dune")
  private val hiddenBook = book(hidden = true)
  private val other = book()
  private val books = listOf(book, hiddenBook, other)
  private val ebook = ebook(name = "Emma")
  private val ebooks = EbooksStore(MemoryDataStore(listOf(ebook)))

  private val repo =
    mockk<BookRepository> {
      coEvery { get(book.id) } returns book
      coEvery { get(hiddenBook.id) } returns hiddenBook
      coEvery { all() } answers { books }
      coEvery { updateCustomMetadata(any(), any()) } just runs
    }

  private val bookHider = BookHider(repo, ebooks)

  private val viewModel = HideBookViewModel(repo, bookHider)

  @Test
  fun `a book in the library can be hidden`() = runTest {
    assertEquals(listOf(BottomSheetItem.Hide), viewModel.items(book.id))
  }

  @Test
  fun `a hidden book can be shown again`() = runTest {
    assertEquals(listOf(BottomSheetItem.Show), viewModel.items(hiddenBook.id))
  }

  @Test
  fun `hiding a book marks it and tells the library, so it can offer an undo`() = runTest {
    bookHider.hidden.test {
      viewModel.onItemClick(book.id, BottomSheetItem.Hide)

      assertEquals(HiddenBooks(listOf(book.id), "Dune", series = false), awaitItem())
    }
    val update = slot<(CustomMetadata) -> CustomMetadata>()
    coVerify { repo.updateCustomMetadata(book.id, capture(update)) }
    assertEquals(true, update.captured(book.custom).hidden)
  }

  @Test
  fun `hiding several books leaves out the hidden ones, so the undo keeps them hidden`() = runTest {
    bookHider.hidden.test {
      bookHider.hide(listOf(book.id, hiddenBook.id, other.id))

      assertEquals(HiddenBooks(listOf(book.id, other.id), name = null, series = false), awaitItem())
    }
    coVerify(exactly = 0) { repo.updateCustomMetadata(hiddenBook.id, any()) }
  }

  @Test
  fun `showing a book takes the mark off`() = runTest {
    viewModel.onItemClick(hiddenBook.id, BottomSheetItem.Show)

    val update = slot<(CustomMetadata) -> CustomMetadata>()
    coVerify { repo.updateCustomMetadata(hiddenBook.id, capture(update)) }
    assertEquals(false, update.captured(hiddenBook.custom).hidden)
  }

  @Test
  fun `an ebook is hidden like an audiobook, with an undo, and shown again`() = runTest {
    bookHider.hidden.test {
      bookHider.hide(listOf(ebook.bookId, book.id))

      assertEquals(
        HiddenBooks(listOf(book.id, ebook.bookId), name = null, series = false),
        awaitItem(),
      )
    }
    assertEquals(true, ebooks.data.first().single().hidden)

    bookHider.show(listOf(ebook.bookId))

    assertEquals(false, ebooks.data.first().single().hidden)
  }
}
