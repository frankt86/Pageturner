package voice.features.bookOverview.ebookActions

import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.BookCategory
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.EbookPosition
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore
import voice.features.bookOverview.MemoryDataStore
import voice.features.bookOverview.book
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.editBook.NameSuggestion
import voice.features.bookOverview.hideBook.BookHider
import voice.features.bookOverview.hideBook.HiddenBooks
import voice.features.bookOverview.overview.bookId
import voice.features.bookOverview.overview.category
import voice.features.bookOverview.select.BookSelectionRequests
import voice.navigation.Destination
import voice.navigation.Navigator

@RunWith(AndroidJUnit4::class)
class EbookActionsViewModelTest {

  private val scope = TestScope()
  private val ebook =
    Ebook(
      uri = "content://books/document/book.epub".toUri(),
      name = "Book",
      format = EbookFormat.Epub,
      position = EbookPosition(section = 3, offset = 120),
      progress = 0.4F,
      lastOpened = 1_000,
    )
  private val store = EbooksStore(MemoryDataStore(listOf(ebook)))
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val repo =
    mockk<BookRepository> {
      coEvery { all() } returns emptyList()
    }
  private val bookHider = BookHider(repo, store)
  private val bookSelectionRequests = BookSelectionRequests()
  private val viewModel =
    EbookActionsViewModel(
      store = store,
      repo = repo,
      application = ApplicationProvider.getApplicationContext(),
      navigator = navigator,
      dispatcherProvider =
        DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
      bookHider = bookHider,
      bookSelectionRequests = bookSelectionRequests,
      uri = ebook.uri,
    )

  private suspend fun stored(): Ebook = store.data.first().single()

  @Test
  fun `marking a book as not started forgets its place`() = scope.runTest {
    viewModel.onStatusChange(BookCategory.NOT_STARTED)
    testScheduler.advanceUntilIdle()

    val updated = stored()
    assertEquals(BookCategory.NOT_STARTED, updated.category)
    assertEquals(EbookPosition(), updated.position)
  }

  @Test
  fun `marking a book as finished keeps its place`() = scope.runTest {
    viewModel.onStatusChange(BookCategory.FINISHED)
    testScheduler.advanceUntilIdle()

    val updated = stored()
    assertEquals(BookCategory.FINISHED, updated.category)
    assertEquals(ebook.position, updated.position)
  }

  @Test
  fun `an edited book keeps its title and author from the file`() = scope.runTest {
    store.updateData { ebooks -> ebooks.map { it.copy(author = "Writer") } }
    viewModel.onEdit()
    viewModel.onEditTitleChange("  New name ")
    viewModel.onEditAuthorChange(" Other writer ")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    val updated = stored()
    assertEquals("New name", updated.title)
    assertEquals("Book", updated.name)
    assertEquals("Other writer", updated.shownAuthor)
    assertEquals("Writer", updated.author)
    assertNull(viewModel.editState)
  }

  @Test
  fun `editing only the author keeps the title following the file`() = scope.runTest {
    viewModel.onEdit()
    viewModel.onEditAuthorChange("Writer")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    val updated = stored()
    assertNull(updated.customName)
    assertEquals("Writer", updated.customAuthor)
  }

  @Test
  fun `editing a book back to its own names drops the custom ones`() = scope.runTest {
    store.updateData { ebooks ->
      ebooks.map { it.copy(author = "Writer", customName = "Other", customAuthor = "Someone") }
    }
    viewModel.onEdit()
    viewModel.onEditTitleChange("Book")
    viewModel.onEditAuthorChange("Writer")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    assertNull(stored().customName)
    assertNull(stored().customAuthor)
  }

  @Test
  fun `clearing the author brings back the one from the file`() = scope.runTest {
    store.updateData { ebooks ->
      ebooks.map { it.copy(author = "Writer", customAuthor = "Someone") }
    }
    viewModel.onEdit()
    viewModel.onEditAuthorChange("")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    assertEquals("Writer", stored().shownAuthor)
  }

  @Test
  fun `the sheet starts with the series and part of the book`() = scope.runTest {
    store.updateData { ebooks ->
      ebooks.map { it.copy(series = "Dune", seriesPart = "1", customPart = "2") }
    }
    viewModel.onEdit()

    assertEquals("Dune", viewModel.editState?.series)
    assertEquals("2", viewModel.editState?.part)
  }

  @Test
  fun `an edited series and part win over the file and keep it`() = scope.runTest {
    store.updateData { ebooks -> ebooks.map { it.copy(series = "Dune", seriesPart = "1") } }
    viewModel.onEdit()
    viewModel.onEditSeriesChange(" Dune Chronicles ")
    viewModel.onEditPartChange("2")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    val updated = stored()
    assertEquals("Dune Chronicles", updated.shownSeries)
    assertEquals("2", updated.shownPart)
    assertEquals("Dune", updated.series)
    assertEquals("1", updated.seriesPart)
  }

  @Test
  fun `editing only the part keeps the series following the file`() = scope.runTest {
    store.updateData { ebooks -> ebooks.map { it.copy(series = "Dune", seriesPart = "1") } }
    viewModel.onEdit()
    viewModel.onEditPartChange("2")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    assertNull(stored().customSeries)
    assertEquals("2", stored().customPart)
  }

  @Test
  fun `editing the series back to the one of the file drops the custom one`() = scope.runTest {
    store.updateData { ebooks ->
      ebooks.map {
        it.copy(series = "Dune", seriesPart = "1", customSeries = "Other", customPart = "3")
      }
    }
    viewModel.onEdit()
    viewModel.onEditSeriesChange("Dune")
    viewModel.onEditPartChange("1")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    assertNull(stored().customSeries)
    assertNull(stored().customPart)
  }

  @Test
  fun `clearing the series and part brings back the ones from the file`() = scope.runTest {
    store.updateData { ebooks ->
      ebooks.map {
        it.copy(series = "Dune", seriesPart = "1", customSeries = "Other", customPart = "3")
      }
    }
    viewModel.onEdit()
    viewModel.onEditSeriesChange("")
    viewModel.onEditPartChange(" ")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    assertEquals("Dune", stored().shownSeries)
    assertEquals("1", stored().shownPart)
  }

  @Test
  fun `the series of audiobooks and other ebooks are suggested`() = scope.runTest {
    coEvery { repo.all() } returns listOf(book(series = "The Expanse"))
    val other =
      ebook.copy(
        uri = "content://books/document/other.epub".toUri(),
        series = "the expanse",
        customSeries = "The Expanse",
      )
    store.updateData { ebooks -> ebooks.map { it.copy(series = "Dune") } + other }
    viewModel.onEdit()
    viewModel.onEditSeriesChange("exp")

    assertEquals(
      listOf(NameSuggestion("The Expanse", bookCount = 2)),
      viewModel.editState?.suggestions,
    )
  }

  @Test
  fun `cover from the internet opens the cover search for the ebook`() {
    viewModel.onInternetCover()

    verify { navigator.goTo(Destination.EbookCoverFromInternet(ebook.uri)) }
  }

  @Test
  fun `hiding a book hides it with an undo, and showing brings it back`() = scope.runTest {
    bookHider.hidden.test {
      viewModel.onHide()
      testScheduler.advanceUntilIdle()

      assertEquals(HiddenBooks(listOf(ebook.bookId), "Book", series = false), awaitItem())
    }
    assertTrue(stored().hidden)

    viewModel.onShow()
    testScheduler.advanceUntilIdle()

    assertFalse(stored().hidden)
  }

  @Test
  fun `the sheet offers to hide a book, and to show a hidden one`() = scope.runTest {
    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()?.items
      }
      .test {
        assertEquals(expected = null, actual = awaitItem())
        assertEquals(
          expected =
            listOf(
              BottomSheetItem.Edit,
              BottomSheetItem.InternetCover,
              BottomSheetItem.FileCover,
              BottomSheetItem.Hide,
            ),
          actual = awaitItem(),
        )

        store.updateData { ebooks -> ebooks.map { it.copy(hidden = true) } }

        assertEquals(expected = BottomSheetItem.Show, actual = awaitItem()?.last())
      }
  }

  @Test
  fun `select asks the library to select the book`() = scope.runTest {
    val requests = mutableListOf<List<BookId>>()
    backgroundScope.launch { bookSelectionRequests.requests.collect { requests += it } }
    runCurrent()

    viewModel.onSelect()
    runCurrent()

    assertEquals(listOf(listOf(ebook.bookId)), requests)
  }
}
