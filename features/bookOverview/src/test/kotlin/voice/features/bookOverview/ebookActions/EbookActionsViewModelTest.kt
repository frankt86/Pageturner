package voice.features.bookOverview.ebookActions

import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.common.DispatcherProvider
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.EbookPosition
import voice.core.data.store.EbooksStore
import voice.features.bookOverview.MemoryDataStore
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.category
import voice.features.bookOverview.overview.toItemViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EbookActionsViewModelTest {

  private val scope = TestScope()
  private val ebook = Ebook(
    uri = "content://books/document/book.epub".toUri(),
    name = "Book",
    format = EbookFormat.Epub,
    position = EbookPosition(section = 3, offset = 120),
    progress = 0.4F,
    lastOpened = 1_000,
  )
  private val store = EbooksStore(MemoryDataStore(listOf(ebook)))
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val viewModel = EbookActionsViewModel(
    store = store,
    application = ApplicationProvider.getApplicationContext(),
    navigator = navigator,
    dispatcherProvider = DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
    uri = ebook.uri,
  )

  private suspend fun stored(): Ebook = store.data.first().single()

  @Test
  fun `marking a book as not started forgets its place`() = scope.runTest {
    viewModel.onStatusChange(BookOverviewCategory.NOT_STARTED)
    testScheduler.advanceUntilIdle()

    val updated = stored()
    assertEquals(BookOverviewCategory.NOT_STARTED, updated.category)
    assertEquals(EbookPosition(), updated.position)
  }

  @Test
  fun `marking a book as finished keeps its place`() = scope.runTest {
    viewModel.onStatusChange(BookOverviewCategory.FINISHED)
    testScheduler.advanceUntilIdle()

    val updated = stored()
    assertEquals(BookOverviewCategory.FINISHED, updated.category)
    assertEquals(ebook.position, updated.position)
  }

  @Test
  fun `an edited book keeps its title and author from the file`() = scope.runTest {
    store.updateData { ebooks -> ebooks.map { it.copy(author = "Writer") } }
    viewModel.onEdit(stored().toItemViewState())
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
    viewModel.onEdit(stored().toItemViewState())
    viewModel.onEditAuthorChange("Writer")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    val updated = stored()
    assertNull(updated.customName)
    assertEquals("Writer", updated.customAuthor)
  }

  @Test
  fun `editing a book back to its own names drops the custom ones`() = scope.runTest {
    store.updateData { ebooks -> ebooks.map { it.copy(author = "Writer", customName = "Other", customAuthor = "Someone") } }
    viewModel.onEdit(stored().toItemViewState())
    viewModel.onEditTitleChange("Book")
    viewModel.onEditAuthorChange("Writer")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    assertNull(stored().customName)
    assertNull(stored().customAuthor)
  }

  @Test
  fun `clearing the author brings back the one from the file`() = scope.runTest {
    store.updateData { ebooks -> ebooks.map { it.copy(author = "Writer", customAuthor = "Someone") } }
    viewModel.onEdit(stored().toItemViewState())
    viewModel.onEditAuthorChange("")
    viewModel.onEditConfirm()
    testScheduler.advanceUntilIdle()

    assertEquals("Writer", stored().shownAuthor)
  }

  @Test
  fun `cover from the internet opens the cover search for the ebook`() {
    viewModel.onInternetCover()

    verify { navigator.goTo(Destination.EbookCoverFromInternet(ebook.uri)) }
  }

  @Test
  fun `removing a book hides it`() = scope.runTest {
    viewModel.onRemove()
    testScheduler.advanceUntilIdle()

    assertTrue(stored().hidden)
  }
}
