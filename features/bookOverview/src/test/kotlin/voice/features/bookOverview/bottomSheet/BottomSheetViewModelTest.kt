package voice.features.bookOverview.bottomSheet

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import voice.core.data.Book
import voice.core.data.BookCategory
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.features.bookOverview.book
import voice.features.bookOverview.chapter
import voice.features.bookOverview.select.BookSelectionRequests
import voice.navigation.Destination
import voice.navigation.Navigator

@OptIn(ExperimentalCoroutinesApi::class)
class BottomSheetViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  private val book =
    book(chapters = listOf(chapter(duration = 10_000), chapter(duration = 10_000)), time = 0)
  private val storedBook = MutableStateFlow<Book?>(book)
  private val livePlaybackState = MutableStateFlow<LivePlaybackState?>(null)

  private val repo =
    mockk<BookRepository> {
      every { flow(book.id) } returns storedBook
    }
  private val playerController =
    mockk<PlayerController> {
      every { livePlaybackStateFlow(any()) } returns livePlaybackState
    }
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val bookSelectionRequests = BookSelectionRequests()

  private val itemViewModel =
    mockk<BottomSheetItemViewModel>(relaxUnitFun = true) {
      coEvery { items(any()) } returns listOf(BottomSheetItem.Edit)
    }

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `shows the book with its category and items`() =
    runTest(dispatcher) {
      viewModel().test {
        val state = awaitLoaded()
        assertEquals(book.name, state.book.name)
        assertEquals(BookCategory.NOT_STARTED, state.category)
        assertEquals(listOf(BottomSheetItem.Edit), state.items)
      }
    }

  @Test
  fun `follows the book while it plays`() =
    runTest(dispatcher) {
      viewModel().test {
        assertEquals(0F, awaitLoaded().book.progress)

        storedBook.value = book.update {
          it.copy(currentChapter = book.chapters[1].id, positionInChapter = 0)
        }

        val state = awaitItem()!!
        assertEquals(0.5F, state.book.progress)
        assertEquals(BookCategory.CURRENT, state.category)
      }
    }

  @Test
  fun `follows the live position`() =
    runTest(dispatcher) {
      viewModel().test {
        assertEquals(0F, awaitLoaded().book.progress)

        livePlaybackState.value =
          LivePlaybackState(
            bookId = book.id,
            chapterId = book.chapters[1].id,
            positionMs = 5_000,
            isPlaying = true,
            playbackSpeed = 1F,
          )

        assertEquals(0.75F, awaitItem()!!.book.progress)
      }
    }

  @Test
  fun `a book that is gone closes the menu`() =
    runTest(dispatcher) {
      storedBook.value = null

      val state = viewModel()
      runCurrent()

      assertNull(state.value)
      verify { navigator.remove(Destination.BookActions(book.id)) }
    }

  @Test
  fun `an item click returns once the item is handled`() =
    runTest(dispatcher) {
      var handledItem: BottomSheetItem? = null
      val slowItemViewModel =
        mockk<BottomSheetItemViewModel> {
          coEvery { items(any()) } returns listOf(BottomSheetItem.Edit)
          coEvery { onItemClick(any(), any()) } coAnswers
            {
              delay(1_000)
              handledItem = secondArg()
            }
        }

      bottomSheetViewModel(itemViewModels = setOf(slowItemViewModel))
        .onItemClick(BottomSheetItem.Edit)

      assertEquals(BottomSheetItem.Edit, handledItem)
    }

  @Test
  fun `select only shows while a screen under the sheet can select`() =
    runTest(dispatcher) {
      viewModel().test {
        assertFalse(BottomSheetItem.Select in awaitLoaded().items)

        backgroundScope.launch { bookSelectionRequests.requests.collect {} }

        assertEquals(listOf(BottomSheetItem.Edit, BottomSheetItem.Select), awaitItem()!!.items)
      }
    }

  @Test
  fun `select asks the screen to select the book`() =
    runTest(dispatcher) {
      val requests = mutableListOf<BookId>()
      backgroundScope.launch { bookSelectionRequests.requests.collect { requests += it } }
      runCurrent()

      bottomSheetViewModel().onItemClick(BottomSheetItem.Select)
      runCurrent()

      assertEquals(listOf(book.id), requests)
    }

  @Test
  fun `closing removes the menu of the book`() =
    runTest(dispatcher) {
      bottomSheetViewModel().onClose()

      verify { navigator.remove(Destination.BookActions(book.id)) }
    }

  private fun bottomSheetViewModel(
    itemViewModels: Set<BottomSheetItemViewModel> = setOf(itemViewModel),
    bookId: BookId = book.id,
  ) =
    BottomSheetViewModel(
      bookId = bookId,
      viewModels = itemViewModels,
      repo = repo,
      navigator = navigator,
      playerController = playerController,
      bookSelectionRequests = bookSelectionRequests,
    )

  private fun TestScope.viewModel(
    bookId: BookId = book.id,
  ): StateFlow<EditBookBottomSheetState?> {
    val viewModel =
      bottomSheetViewModel(
        bookId = bookId,
      )
    return backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.state()
    }
  }

  private suspend fun ReceiveTurbine<EditBookBottomSheetState?>.awaitLoaded():
    EditBookBottomSheetState {
    while (true) {
      awaitItem()?.let {
        return it
      }
    }
  }
}
