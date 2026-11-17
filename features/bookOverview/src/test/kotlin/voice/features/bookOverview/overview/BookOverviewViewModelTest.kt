package voice.features.bookOverview.overview

import android.net.Uri
import androidx.datastore.core.DataStore
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import app.cash.turbine.test
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import voice.core.common.AppInfoProvider
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.BookCategory
import voice.core.data.BookId
import voice.core.data.CustomMetadata
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.EbooksStore
import voice.core.featureflag.MemoryFeatureFlag
import voice.core.kioskmode.KioskModeDemoData
import voice.core.kioskmode.KioskModeFeatureFlag
import voice.core.playback.ExperimentalPlaybackPersistenceFeatureFlag
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.scanner.DeviceHasStoragePermissionBug
import voice.core.scanner.MediaScanTrigger
import voice.core.ui.GridCount
import voice.core.ui.GridMode
import voice.core.ui.GridModeStore
import voice.features.bookOverview.MemoryDataStore
import voice.features.bookOverview.book
import voice.features.bookOverview.deleteBook.BookDeleter
import voice.features.bookOverview.hideBook.BookHider
import voice.features.bookOverview.hideBook.HiddenBooks
import voice.features.bookOverview.select.BookField
import voice.features.bookOverview.select.BookSelectionRequests
import voice.features.bookOverview.select.BooksEdited
import voice.features.bookOverview.select.SelectedBooksEditor
import voice.navigation.Destination
import voice.navigation.Navigator

class BookOverviewViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  // combine yields, and the test dispatcher's scheduler isn't the one runTest advances
  private val dispatcherProvider =
    DispatcherProvider(
      main = testDispatcher,
      io = Dispatchers.Unconfined,
      mainImmediate = testDispatcher,
    )
  private val audiobookFolders =
    mockk<AudiobookFolders> {
      every { all() } returns flowOf(emptyMap())
      every { withoutAccess() } returns flowOf(emptyMap())
    }
  private val emptyLibraryTracker = mockk<EmptyLibraryTracker>(relaxUnitFun = true)
  private val emptyEbooks = EbooksStore(MemoryDataStore(emptyList()))

  @Test
  fun `state updates the current book item from live playback`() = runTest {
    val currentBook = book(name = "Current", time = 1_000)
    val otherBook = book(name = "Other", time = 2_000)
    val livePlaybackFlow = MutableStateFlow<LivePlaybackState?>(null)
    val viewModel =
      BookOverviewViewModel(
        repo =
          mockk<BookRepository> {
            every { flow() } returns MutableStateFlow(listOf(currentBook, otherBook))
          },
        mediaScanner =
          mockk<MediaScanTrigger> {
            every { scannerActive } returns MutableStateFlow(false)
            every { scan(any()) } just Runs
          },
        playStateManager = PlayStateManager(),
        playerController =
          mockk<PlayerController> {
            every { livePlaybackStateFlow(currentBook.id) } returns livePlaybackFlow
          },
        currentBookStoreDataStore = CurrentBookStore(MemoryDataStore(currentBook.id)),
        folderPickerMovedDialogShownStore =
          FolderPickerMovedDialogShownStore(MemoryDataStore(false)),
        gridModeStore = GridModeStore(MemoryDataStore(GridMode.LIST)),
        ebooksStore = emptyEbooks,
        gridCount =
          mockk<GridCount> {
            every { useGridAsDefault() } returns false
          },
        navigator = mockk<Navigator>(),
        appInfoProvider = appInfoProvider(),
        deviceHasStoragePermissionBug =
          mockk<DeviceHasStoragePermissionBug> {
            every { hasBug } returns MutableStateFlow(false)
          },
        folderPickerInSettingsFeatureFlag =
          FolderPickerInSettingsFeatureFlag(MemoryFeatureFlag(false)),
        experimentalPlaybackPersistenceFeatureFlag =
          ExperimentalPlaybackPersistenceFeatureFlag(MemoryFeatureFlag(true)),
        kioskModeFeatureFlag = KioskModeFeatureFlag(MemoryFeatureFlag(false)),
        dispatcherProvider = dispatcherProvider,
        audiobookFolders = audiobookFolders,
        emptyLibraryTracker = emptyLibraryTracker,
        showHiddenBooksStore = ShowHiddenBooksStore(MemoryDataStore(false)),
        libraryGroupingStore = LibraryGroupingStore(MemoryDataStore(LibraryGrouping.Series)),
        hiddenSourcesStore = HiddenSourcesStore(MemoryDataStore(emptySet())),
        bookHider = BookHider(mockk(), emptyEbooks),
        bookSelectionRequests = BookSelectionRequests(),
        selectedBooksEditor = SelectedBooksEditor(mockk(), emptyEbooks),
        bookDeleter = mockk(),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        val initial = awaitItem()
        val initialOtherItem = initial.book(otherBook.id)

        assertEquals(expected = currentBook.toItemViewState(), actual = initial.hero?.value)
        assertEquals(expected = otherBook.toItemViewState(), actual = initialOtherItem)
        assertEquals(
          expected = listOf(otherBook.id.value),
          actual = initial.keys(BookCategory.CURRENT),
        )
        assertEquals(expected = currentBook.id, actual = initial.currentBookId)

        val livePlaybackState =
          LivePlaybackState(
            bookId = currentBook.id,
            chapterId = currentBook.chapters.first().id,
            positionMs = 6_000,
            isPlaying = true,
            playbackSpeed = 1F,
          )
        livePlaybackFlow.value = livePlaybackState
        yield()

        assertEquals(
          expected = currentBook.overlay(livePlaybackState).toItemViewState(),
          actual = initial.hero?.value,
        )
        assertEquals(expected = initialOtherItem, actual = initial.book(otherBook.id))
        expectNoEvents()
      }
  }

  @Test
  fun `state uses demo books in kiosk mode`() = runTest {
    val viewModel =
      BookOverviewViewModel(
        repo =
          mockk<BookRepository> {
            every { flow() } returns MutableStateFlow(emptyList())
          },
        mediaScanner =
          mockk<MediaScanTrigger> {
            every { scannerActive } returns MutableStateFlow(false)
            every { scan(any()) } just Runs
          },
        playStateManager = PlayStateManager(),
        playerController = mockk(),
        currentBookStoreDataStore = CurrentBookStore(MemoryDataStore(null)),
        folderPickerMovedDialogShownStore =
          FolderPickerMovedDialogShownStore(MemoryDataStore(false)),
        gridModeStore = GridModeStore(MemoryDataStore(GridMode.LIST)),
        ebooksStore = emptyEbooks,
        gridCount =
          mockk<GridCount> {
            every { useGridAsDefault() } returns false
          },
        navigator = mockk<Navigator>(),
        appInfoProvider = appInfoProvider(),
        deviceHasStoragePermissionBug =
          mockk<DeviceHasStoragePermissionBug> {
            every { hasBug } returns MutableStateFlow(false)
          },
        folderPickerInSettingsFeatureFlag =
          FolderPickerInSettingsFeatureFlag(MemoryFeatureFlag(false)),
        experimentalPlaybackPersistenceFeatureFlag =
          ExperimentalPlaybackPersistenceFeatureFlag(MemoryFeatureFlag(false)),
        kioskModeFeatureFlag = KioskModeFeatureFlag(MemoryFeatureFlag(true)),
        dispatcherProvider = dispatcherProvider,
        audiobookFolders = audiobookFolders,
        emptyLibraryTracker = emptyLibraryTracker,
        showHiddenBooksStore = ShowHiddenBooksStore(MemoryDataStore(false)),
        libraryGroupingStore = LibraryGroupingStore(MemoryDataStore(LibraryGrouping.Series)),
        hiddenSourcesStore = HiddenSourcesStore(MemoryDataStore(emptySet())),
        bookHider = BookHider(mockk(), emptyEbooks),
        bookSelectionRequests = BookSelectionRequests(),
        selectedBooksEditor = SelectedBooksEditor(mockk(), emptyEbooks),
        bookDeleter = mockk(),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        val state = awaitItem()
        assertEquals(
          expected =
            KioskModeDemoData.demoAudiobooks.map { it.id.value } -
              KioskModeDemoData.currentlyPlaying.id.value,
          actual = state.keys(BookCategory.CURRENT),
        )
        assertEquals(expected = "Echoes of Tomorrow", actual = state.hero?.value?.name)
        assertEquals(expected = KioskModeDemoData.currentlyPlaying.id, actual = state.currentBookId)
        assertEquals(expected = false, actual = state.showFolderAccessBadge)
        assertEquals(expected = false, actual = state.showFilterIcon)
      }
  }

  @Test
  fun `the books of a series are one stack, which keeps the book that is continued`() = runTest {
    val first =
      book(
        name = "First",
        series = "Saga",
        part = "1",
        time = 20_000,
        lastPlayedAt = Instant.fromEpochSeconds(1),
      )
    val second =
      book(
        name = "Second",
        series = "Saga",
        part = "2",
        time = 1_000,
        lastPlayedAt = Instant.fromEpochSeconds(3),
      )
    val single = book(name = "Single", time = 1_000, lastPlayedAt = Instant.fromEpochSeconds(2))
    val viewModel = viewModel(books = listOf(first, second, single), currentBookId = second.id)

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        val state = awaitItem()
        assertEquals(expected = "Second", actual = state.hero?.value?.name)
        assertEquals(expected = "Saga 2", actual = state.hero?.value?.series)
        assertEquals(
          expected = listOf("series:saga", single.id.value),
          actual = state.keys(BookCategory.CURRENT),
        )
        val stack = state.sections.getValue(BookCategory.CURRENT).first() as LibraryItem.Series
        assertEquals(expected = 2, actual = stack.series.bookCount)
        assertEquals(expected = 1, actual = stack.series.finishedCount)
        assertEquals(expected = 2, actual = state.inProgressCount)
      }
  }

  @Test
  fun `hidden books leave the library and still count for the filter`() = runTest {
    val hidden = book(name = "Hidden", time = 1_000, hidden = true)
    val visible = book(name = "Visible", time = 1_000)
    val viewModel = viewModel(books = listOf(hidden, visible), currentBookId = hidden.id)

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        val state = awaitItem()
        assertEquals(
          expected = listOf(visible.id.value),
          actual = state.keys(BookCategory.CURRENT),
        )
        assertEquals(expected = null, actual = state.hero)
        assertEquals(expected = null, actual = state.playButtonState)
        assertEquals(expected = 1, actual = state.inProgressCount)
        assertEquals(expected = 1, actual = state.hiddenBookCount)
      }
  }

  @Test
  fun `a library with only hidden books isn't empty`() = runTest {
    val viewModel = viewModel(books = listOf(book(hidden = true)))

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        val state = awaitItem()
        assertEquals(expected = false, actual = state.showEmptyLibrary)
        assertEquals(expected = true, actual = state.showSearchIcon)
        assertEquals(expected = emptyMap(), actual = state.sections)
      }
  }

  @Test
  fun `showing hidden books brings them back with a label`() = runTest {
    val hidden = book(name = "Hidden", time = 1_000, hidden = true)
    val store = MemoryDataStore(false)
    val viewModel = viewModel(books = listOf(hidden), showHiddenBooksStore = store)

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = emptyMap(), actual = awaitItem().sections)

        viewModel.onShowHiddenBooksChange(true)

        val state = awaitItem()
        assertEquals(expected = true, actual = state.showHiddenBooks)
        assertEquals(expected = true, actual = state.book(hidden.id).hidden)
        assertEquals(expected = true, actual = store.data.first())
      }
  }

  @Test
  fun `undo shows the hidden book again`() = runTest {
    val book = book(hidden = true)
    val repo =
      mockk<BookRepository> {
        every { flow() } returns MutableStateFlow(emptyList())
        coEvery { all() } returns listOf(book)
        coEvery { updateCustomMetadata(book.id, any()) } just Runs
      }
    val viewModel = viewModel(repo = repo)

    viewModel.onUndoHide(listOf(book.id))

    val update = slot<(CustomMetadata) -> CustomMetadata>()
    coVerify { repo.updateCustomMetadata(book.id, capture(update)) }
    assertEquals(expected = false, actual = update.captured(book.custom).hidden)
  }

  @Test
  fun `with hidden books shown, a stack whose books are all hidden is marked hidden`() = runTest {
    val hiddenSeries =
      listOf(
        book(series = "Saga", time = 0, hidden = true),
        book(series = "Saga", time = 0, hidden = true),
      )
    val partlyHidden =
      listOf(book(series = "Other", time = 0, hidden = true), book(series = "Other", time = 0))
    val viewModel =
      viewModel(
        books = hiddenSeries + partlyHidden,
        showHiddenBooksStore = MemoryDataStore(true),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        val stacks =
          awaitItem()
            .sections
            .getValue(BookCategory.NOT_STARTED)
            .filterIsInstance<LibraryItem.Series>()
            .associate { it.series.name to it.series.hidden }
        assertEquals(expected = mapOf("Other" to false, "Saga" to true), actual = stacks)
      }
  }

  @Test
  fun `a long press on a stack offers its actions`() = runTest {
    val navigator = mockk<Navigator>(relaxed = true)
    val viewModel = viewModel(navigator = navigator)

    viewModel.onSeriesLongClick("Saga")

    verify {
      navigator.goTo(Destination.SeriesActions("Saga"))
    }
  }

  @Test
  fun `a stack opens its series`() = runTest {
    val navigator = mockk<Navigator>(relaxed = true)
    val viewModel = viewModel(navigator = navigator)

    viewModel.onSeriesClick("Saga")

    verify {
      navigator.goTo(Destination.Series("Saga"))
    }
  }

  @Test
  fun `grouped by author, each author is one entry below current`() = runTest {
    val started = book(author = "Lena Morgan", time = 1_000)
    val notStarted = book(author = "Lena Morgan", time = 0)
    val finished = book(author = "Lena Morgan", time = 20_000)
    val noAuthor = book(author = null, time = 0)
    val store = MemoryDataStore(LibraryGrouping.Series)
    val viewModel =
      viewModel(
        books = listOf(started, notStarted, finished, noAuthor),
        libraryGroupingStore = store,
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(
          expected = listOf(notStarted.id.value, noAuthor.id.value).sorted(),
          actual = awaitItem().keys(BookCategory.NOT_STARTED).sorted(),
        )

        viewModel.onGroupingChange(LibraryGrouping.Author)

        val state = awaitItem()
        assertEquals(expected = LibraryGrouping.Author, actual = state.grouping)
        assertEquals(expected = LibraryGrouping.Author, actual = store.data.first())
        assertEquals(
          expected = listOf(started.id.value),
          actual = state.keys(BookCategory.CURRENT),
        )
        assertEquals(
          expected = listOf("author:NOT_STARTED:lenamorgan", "author:NOT_STARTED:"),
          actual = state.keys(BookCategory.NOT_STARTED),
        )
        assertEquals(
          expected = listOf("author:FINISHED:lenamorgan"),
          actual = state.keys(BookCategory.FINISHED),
        )
      }
  }

  @Test
  fun `grouped by author, the book on the continue listening card is in no author entry`() =
    runTest {
      val current = book(author = "Lena Morgan", time = 0)
      val other = book(author = "Nora Whitfield", time = 0)
      val viewModel =
        viewModel(
          books = listOf(current, other),
          currentBookId = current.id,
          libraryGroupingStore = MemoryDataStore(LibraryGrouping.Author),
        )

      backgroundScope
        .launchMolecule(RecompositionMode.Immediate) {
          viewModel.state()
        }
        .test {
          assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
          assertEquals(
            expected = listOf("author:NOT_STARTED:norawhitfield"),
            actual = awaitItem().keys(BookCategory.NOT_STARTED),
          )
        }
    }

  @Test
  fun `an author opens their screen`() = runTest {
    val navigator = mockk<Navigator>(relaxed = true)
    val viewModel = viewModel(navigator = navigator)

    viewModel.onAuthorClick("Lena Morgan")
    viewModel.onAuthorClick(null)

    verify {
      navigator.goTo(Destination.Author("Lena Morgan"))
      navigator.goTo(Destination.Author(null))
    }
  }

  @Test
  fun `a long press on an author offers the actions for the books of the entry`() = runTest {
    val navigator = mockk<Navigator>(relaxed = true)
    val viewModel = viewModel(navigator = navigator)
    val first = BookId("first")
    val second = BookId("second")

    viewModel.onAuthorLongClick(
      AuthorEntryViewState(
        key = "lenamorgan",
        name = "Lena Morgan",
        covers = emptyList(),
        seriesCount = 0,
        bookCount = 2,
        category = BookCategory.CURRENT,
        bookIds = listOf(first, second),
      ),
    )

    verify {
      navigator.goTo(Destination.AuthorActions("Lena Morgan", listOf(first, second)))
    }
  }

  @Test
  fun `folder picker icon is hidden when folder picker in settings flag is true`() = runTest {
    val viewModel =
      viewModel(
        folderPickerInSettingsFeatureFlag = MemoryFeatureFlag(true),
        folderPickerMovedDialogShownStore = MemoryDataStore(false),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = false, actual = awaitItem().showFolderPickerIcon)
      }
  }

  @Test
  fun `folder picker icon is shown once when flag is false`() = runTest {
    val viewModel =
      viewModel(
        folderPickerInSettingsFeatureFlag = MemoryFeatureFlag(false),
        folderPickerMovedDialogShownStore = MemoryDataStore(false),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = true, actual = awaitItem().showFolderPickerIcon)
      }
  }

  @Test
  fun `folder picker icon is hidden when moved dialog was shown`() = runTest {
    val viewModel =
      viewModel(
        folderPickerInSettingsFeatureFlag = MemoryFeatureFlag(false),
        folderPickerMovedDialogShownStore = MemoryDataStore(true),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = false, actual = awaitItem().showFolderPickerIcon)
      }
  }

  @Test
  fun `folder picker icon is hidden for installs on migration cutoff date`() = runTest {
    val viewModel =
      viewModel(
        folderPickerInSettingsFeatureFlag = MemoryFeatureFlag(false),
        folderPickerMovedDialogShownStore = MemoryDataStore(false),
        appInfoProvider = appInfoProvider(installTime = Instant.parse("2026-06-17T00:00:00Z")),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = false, actual = awaitItem().showFolderPickerIcon)
      }
  }

  @Test
  fun `folder picker click shows moved dialog instead of navigating`() = runTest {
    val navigator = mockk<Navigator>(relaxed = true)
    val viewModel =
      viewModel(
        navigator = navigator,
        folderPickerInSettingsFeatureFlag = MemoryFeatureFlag(false),
        folderPickerMovedDialogShownStore = MemoryDataStore(false),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = null, actual = awaitItem().dialog)

        viewModel.onBookFolderClick()

        assertEquals(
          expected = BookOverviewViewState.Dialog.FolderPickerMovedToSettings,
          actual = awaitItem().dialog,
        )
        verify(exactly = 0) {
          navigator.goTo(Destination.FolderPicker)
        }
      }
  }

  @Test
  fun `dismissing moved dialog marks it shown and hides folder picker icon`() = runTest {
    val folderPickerMovedDialogShownStore = MemoryDataStore(false)
    val viewModel =
      viewModel(
        folderPickerInSettingsFeatureFlag = MemoryFeatureFlag(false),
        folderPickerMovedDialogShownStore = folderPickerMovedDialogShownStore,
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = true, actual = awaitItem().showFolderPickerIcon)

        viewModel.onBookFolderClick()
        assertEquals(
          expected = BookOverviewViewState.Dialog.FolderPickerMovedToSettings,
          actual = awaitItem().dialog,
        )

        viewModel.onFolderPickerMovedDialogDismiss()

        val dismissed = awaitItem()
        assertEquals(expected = null, actual = dismissed.dialog)
        if (dismissed.showFolderPickerIcon) {
          assertEquals(expected = false, actual = awaitItem().showFolderPickerIcon)
        } else {
          assertEquals(expected = false, actual = dismissed.showFolderPickerIcon)
        }
      }
  }

  @Test
  fun `current book id is dropped when the book is no longer in the library`() = runTest {
    val book = book(name = "Present", time = 1_000)
    val viewModel = viewModel(books = listOf(book), currentBookId = BookId("removed"))

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = null, actual = awaitItem().currentBookId)
      }
  }

  @Test
  fun `a library without books shows that no books were found`() = runTest {
    val viewModel = viewModel()

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = true, actual = awaitItem().showEmptyLibrary)
      }
  }

  @Test
  fun `an empty library shows that no books were found only once scanning is done`() = runTest {
    val scanning = MutableStateFlow(true)
    val viewModel = viewModel(scanning = scanning)

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        testScheduler.runCurrent()
        assertEquals(expected = false, actual = expectMostRecentItem().showEmptyLibrary)
        scanning.value = false
        assertEquals(expected = true, actual = awaitItem().showEmptyLibrary)
      }
  }

  @Test
  fun `an empty library is tracked`() = runTest {
    val viewModel = viewModel()

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = true, actual = awaitItem().showEmptyLibrary)
        testScheduler.runCurrent()
      }

    coVerify { emptyLibraryTracker.track() }
  }

  @Test
  fun `folders without access show the badge`() = runTest {
    every { audiobookFolders.withoutAccess() } returns
      flowOf(mapOf(FolderType.Root to listOf(mockk<Uri>())))
    val viewModel = viewModel()

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        assertEquals(expected = true, actual = awaitItem().showFolderAccessBadge)
      }
  }

  @Test
  fun `an ebook read after the current audiobook was played is in the Continue card`() = runTest {
    val book =
      book(name = "Listened", time = 1_000).update {
        it.copy(lastPlayedAt = Instant.fromEpochMilliseconds(1_000))
      }
    val ebook = ebook(lastOpened = 2_000)
    val viewModel =
      viewModel(
        books = listOf(book),
        currentBookId = book.id,
        ebooks = listOf(ebook, ebook(lastOpened = 1_500)),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        val state = awaitItem()
        assertEquals(expected = ebook.bookId, actual = state.heroBookId)
        assertEquals(expected = book.id, actual = state.currentBookId)
        assertEquals(expected = true, actual = state.showSearchIcon)
      }
  }

  @Test
  fun `ebooks are in the sections after the audiobooks, without the one in the Continue card`() =
    runTest {
      val book =
        book(name = "Listened", time = 1_000).update {
          it.copy(lastPlayedAt = Instant.fromEpochMilliseconds(1_000))
        }
      val reading = ebook(lastOpened = 2_000)
      val notStarted = ebook(lastOpened = 0, progress = 0F)
      val viewModel =
        viewModel(
          books = listOf(book),
          currentBookId = book.id,
          ebooks = listOf(reading, notStarted),
        )

      backgroundScope
        .launchMolecule(RecompositionMode.Immediate) {
          viewModel.state()
        }
        .test {
          assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
          val state = awaitItem()
          assertEquals(expected = reading.bookId, actual = state.hero?.value?.id)
          assertEquals(expected = listOf(book.id.value), actual = state.keys(BookCategory.CURRENT))
          assertEquals(
            expected = listOf(notStarted.bookId.value),
            actual = state.keys(BookCategory.NOT_STARTED),
          )
          assertEquals(expected = 2, actual = state.inProgressCount)
        }
    }

  @Test
  fun `an ebook stacks with the audiobooks of its series and shows its series in the Continue card`() =
    runTest {
      val first =
        book(
          name = "First",
          series = "Saga",
          part = "1",
          time = 20_000,
          lastPlayedAt = Instant.fromEpochSeconds(1),
        )
      val reading = ebook(lastOpened = 2_000).copy(series = "Saga", customPart = "2")
      val third = ebook(lastOpened = 0, progress = 0F).copy(customSeries = "saga", customPart = "3")
      val viewModel = viewModel(books = listOf(first), ebooks = listOf(reading, third))

      backgroundScope
        .launchMolecule(RecompositionMode.Immediate) {
          viewModel.state()
        }
        .test {
          assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
          val state = awaitItem()
          assertEquals(expected = reading.bookId, actual = state.hero?.value?.id)
          assertEquals(expected = "Saga 2", actual = state.hero?.value?.series)
          assertEquals(expected = listOf("series:saga"), actual = state.keys(BookCategory.CURRENT))
          assertEquals(expected = setOf(BookCategory.CURRENT), actual = state.sections.keys)
          val stack =
            (state.sections.getValue(BookCategory.CURRENT).single() as LibraryItem.Series).series
          assertEquals(expected = 3, actual = stack.bookCount)
          assertEquals(expected = 1, actual = stack.finishedCount)
        }
    }

  @Test
  fun `an ebook opens in the reader, and its actions on a long press`() = runTest {
    val navigator = mockk<Navigator>(relaxed = true)
    val ebook = ebook(lastOpened = 0)
    val viewModel = viewModel(navigator = navigator, ebooks = listOf(ebook))

    viewModel.onBookClick(ebook.bookId)
    viewModel.onBookLongClick(ebook.bookId)

    verify {
      navigator.goTo(Destination.Reader(ebook.uri))
      navigator.goTo(Destination.EbookActions(ebook.uri))
    }
  }

  @Test
  fun `the current audiobook stays in the Continue card when it was played after the ebook was read`() =
    runTest {
      val book =
        book(name = "Listened", time = 1_000).update {
          it.copy(lastPlayedAt = Instant.fromEpochMilliseconds(3_000))
        }
      val viewModel =
        viewModel(
          books = listOf(book),
          currentBookId = book.id,
          // a finished ebook isn't read anymore
          ebooks = listOf(ebook(lastOpened = 2_000), ebook(lastOpened = 4_000, progress = 1F)),
        )

      backgroundScope
        .launchMolecule(RecompositionMode.Immediate) {
          viewModel.state()
        }
        .test {
          assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
          assertEquals(expected = book.id, actual = awaitItem().heroBookId)
        }
    }

  @Test
  fun `in the author grouping ebooks are with the audiobooks of their author`() = runTest {
    val book = book(name = "Listened", author = "Lena Morgan", time = 0)
    val ebook = ebook(lastOpened = 0, progress = 0F).copy(customAuthor = "lena morgan")
    val other = ebook(lastOpened = 0, progress = 0F).copy(author = "Nora Whitfield")
    val viewModel =
      viewModel(
        books = listOf(book),
        ebooks = listOf(ebook, other),
        libraryGroupingStore = MemoryDataStore(LibraryGrouping.Author),
      )

    backgroundScope
      .launchMolecule(RecompositionMode.Immediate) {
        viewModel.state()
      }
      .test {
        assertEquals(expected = BookOverviewViewState.Loading, actual = awaitItem())
        val authors =
          awaitItem().sections.getValue(BookCategory.NOT_STARTED).map {
            (it as LibraryItem.Author).author
          }
        assertEquals(
          expected = listOf("Lena Morgan", "Nora Whitfield"),
          actual = authors.map { it.name },
        )
        assertEquals(
          expected = listOf(listOf(book.id, ebook.bookId), listOf(other.bookId)),
          actual = authors.map { it.bookIds },
        )
      }
  }

  @Test
  fun `hidden ebooks leave the library, count for the filter and come back with a label`() =
    runTest {
      val hidden = ebook(lastOpened = 0, progress = 0F).copy(hidden = true)
      val visible = ebook(lastOpened = 0, progress = 0F)
      val viewModel = viewModel(ebooks = listOf(hidden, visible))
      val state = viewModel.stateIn(backgroundScope)

      val filtered = state.first { it.sections.isNotEmpty() }
      assertEquals(
        expected = listOf(visible.bookId.value),
        actual = filtered.keys(BookCategory.NOT_STARTED),
      )
      assertEquals(expected = 1, actual = filtered.hiddenBookCount)
      assertEquals(expected = true, actual = filtered.showFilterIcon)

      viewModel.onShowHiddenBooksChange(true)

      val shown = state.first { it.showHiddenBooks }
      assertEquals(expected = true, actual = shown.book(hidden.bookId).hidden)
      assertEquals(expected = false, actual = shown.book(visible.bookId).hidden)
    }

  @Test
  fun `ebooks are selected and hidden with the audiobooks`() = runTest {
    val book = book(time = 0)
    val ebook = ebook(lastOpened = 0, progress = 0F)
    val ebooksStore = EbooksStore(MemoryDataStore(listOf(ebook)))
    val viewModel = viewModel(repo = libraryRepo(book), ebooksStore = ebooksStore)
    val state = viewModel.stateIn(backgroundScope)
    val hidden = mutableListOf<HiddenBooks>()
    backgroundScope.launch(testDispatcher) { viewModel.hiddenBooks.collect { hidden += it } }
    state.first { it.sections.isNotEmpty() }

    viewModel.onSelectionStart(listOf(ebook.bookId))
    viewModel.onSelectAll()
    assertEquals(
      expected = setOf(book.id, ebook.bookId),
      actual = state.first { it.selection?.allSelected == true }.selection?.ids,
    )
    viewModel.onSelectionHide()

    state.first { it.selection == null && it.hiddenBookCount == 2 }
    assertEquals(expected = true, actual = ebooksStore.data.first().single().hidden)
    assertEquals(
      expected = listOf(HiddenBooks(listOf(book.id, ebook.bookId), name = null, series = false)),
      actual = hidden,
    )
  }

  @Test
  fun `the series of a selected ebook is set with the audiobooks`() = runTest {
    val book = book(series = "Saga", time = 0)
    val ebook = ebook(lastOpened = 0, progress = 0F)
    val ebooksStore = EbooksStore(MemoryDataStore(listOf(ebook)))
    val viewModel = viewModel(repo = libraryRepo(book), ebooksStore = ebooksStore)
    val state = viewModel.stateIn(backgroundScope)
    state.first { it.sections.isNotEmpty() }

    viewModel.onSelectionStart(listOf(ebook.bookId))
    viewModel.onSelectionEdit(BookField.Series)
    val edit = state.first { it.selectionEdit != null }.selectionEdit
    assertEquals(expected = setOf(ebook.bookId), actual = edit?.ids)
    assertEquals(expected = listOf("Saga"), actual = edit?.suggestions?.map { it.name })

    viewModel.onSelectionEditSave("Saga")

    state.first { it.selectionEdit?.saved == true }
    assertEquals(expected = "Saga", actual = ebooksStore.data.first().single().customSeries)
  }

  @Test
  fun `show folders opens the folders`() = runTest {
    val navigator = mockk<Navigator>(relaxed = true)
    val viewModel = viewModel(navigator = navigator)

    viewModel.onShowFoldersClick()

    verify {
      navigator.goTo(Destination.FolderPicker)
    }
  }

  @Test
  fun `search click opens the search`() = runTest {
    val navigator = mockk<Navigator>(relaxed = true)
    val viewModel = viewModel(navigator = navigator)

    viewModel.onSearchClick()

    verify {
      navigator.goTo(Destination.LibrarySearch)
    }
  }

  @Test
  fun `a request from the book actions starts selecting the book`() = runTest {
    val book = book(time = 0)
    val requests = BookSelectionRequests()
    val viewModel = viewModel(books = listOf(book), bookSelectionRequests = requests)
    val state = viewModel.stateIn(backgroundScope)
    backgroundScope.launch(testDispatcher) {
      viewModel.selectionRequests.collect(viewModel::onSelectionStart)
    }

    requests.select(listOf(book.id))

    assertEquals(setOf(book.id), state.first { it.selection != null }.selection?.ids)
  }

  @Test
  fun `a tap on a stack selects all its books, and removing the last book ends selecting`() =
    runTest {
      val first = book(series = "Saga", time = 0)
      val second = book(series = "Saga", time = 0)
      val single = book(time = 0)
      val viewModel = viewModel(books = listOf(first, second, single))
      val state = viewModel.stateIn(backgroundScope)
      state.first { it.sections.isNotEmpty() }

      viewModel.onSelectionStart(listOf(single.id))
      val stack =
        state.value.sections.values.flatten().filterIsInstance<LibraryItem.Series>().single()
      viewModel.onSelectionToggle(stack.series.bookIds)
      assertEquals(
        expected = setOf(first.id, second.id, single.id),
        actual = state.first { it.selection?.ids?.size == 3 }.selection?.ids,
      )

      viewModel.onSelectionToggle(stack.series.bookIds)
      viewModel.onSelectionToggle(listOf(single.id))
      state.first { it.selection == null }
    }

  @Test
  fun `select all takes the books on screen and leaves out hidden ones`() = runTest {
    val current = book(time = 1_000)
    val other = book(time = 0)
    val hidden = book(time = 0, hidden = true)
    val viewModel = viewModel(books = listOf(current, other, hidden), currentBookId = current.id)
    val state = viewModel.stateIn(backgroundScope)
    state.first { it.hero != null }

    viewModel.onSelectionStart(listOf(other.id))
    viewModel.onSelectAll()

    val selection = state.first { it.selection?.allSelected == true }.selection
    assertEquals(expected = setOf(current.id, other.id), actual = selection?.ids)
  }

  @Test
  fun `hide hides the selected books, ends selecting and offers one undo`() = runTest {
    val first = book(time = 0)
    val second = book(time = 0)
    val repo = libraryRepo(first, second)
    val viewModel = viewModel(repo = repo)
    val state = viewModel.stateIn(backgroundScope)
    val hidden = mutableListOf<HiddenBooks>()
    backgroundScope.launch(testDispatcher) { viewModel.hiddenBooks.collect { hidden += it } }
    state.first { it.sections.isNotEmpty() }

    viewModel.onSelectionStart(listOf(first.id))
    viewModel.onSelectionToggle(listOf(second.id))
    state.first { it.selection?.ids?.size == 2 }
    viewModel.onSelectionHide()

    state.first { it.selection == null && it.hiddenBookCount == 2 }
    assertEquals(
      expected = listOf(HiddenBooks(listOf(first.id, second.id), name = null, series = false)),
      actual = hidden,
    )
  }

  @Test
  fun `delete asks first and deletes the selected books once the files are ticked`() = runTest {
    val first = book(time = 0)
    val second = book(time = 0)
    val bookDeleter = mockk<BookDeleter>(relaxUnitFun = true)
    val viewModel = viewModel(repo = libraryRepo(first, second), bookDeleter = bookDeleter)
    val state = viewModel.stateIn(backgroundScope)
    state.first { it.sections.isNotEmpty() }

    viewModel.onSelectionStart(listOf(first.id, second.id))
    state.first { it.selection?.ids?.size == 2 }
    viewModel.onSelectionDelete()
    assertEquals(
      expected = 2,
      actual = state.first { it.selectionDelete != null }.selectionDelete?.bookCount,
    )
    viewModel.onSelectionDeleteConfirm()
    coVerify(exactly = 0) { bookDeleter.delete(any()) }

    viewModel.onSelectionDelete()
    viewModel.onSelectionDeleteCheckBoxCheck(true)
    viewModel.onSelectionDeleteConfirm()

    state.first { it.selection == null && it.selectionDelete == null }
    coVerify { bookDeleter.delete(listOf(first.id, second.id)) }
  }

  @Test
  fun `a series for device books is set, and undo lets them follow their files again`() = runTest {
    val first = book(series = "Sagaa", time = 0)
    val second = book(series = "Saga", time = 0)
    val repo = libraryRepo(first, second)
    val viewModel = viewModel(repo = repo)
    val state = viewModel.stateIn(backgroundScope)
    val edits = mutableListOf<BooksEdited>()
    backgroundScope.launch(testDispatcher) { viewModel.booksEdited.collect { edits += it } }
    state.first { it.sections.isNotEmpty() }

    viewModel.onSelectionStart(listOf(first.id))
    viewModel.onSelectionToggle(listOf(second.id))
    state.first { it.selection?.ids?.size == 2 }
    viewModel.onSelectionEdit(BookField.Series)
    val edit = state.first { it.selectionEdit != null }.selectionEdit
    assertEquals(expected = "", actual = edit?.value)
    assertEquals(expected = listOf("Saga", "Sagaa"), actual = edit?.suggestions?.map { it.name })

    viewModel.onSelectionEditSave(" Saga ")

    assertEquals(
      expected = true,
      actual = state.first { it.selection == null }.selectionEdit?.saved,
    )
    assertEquals(expected = listOf("Saga", "Saga"), actual = repo.all().map { it.series })
    val edited = edits.single()
    assertEquals(expected = 2, actual = edited.count)

    viewModel.onUndoEdit(edited)

    assertEquals(expected = listOf(null, null), actual = repo.all().map { it.custom.series })
  }

  @Test
  fun `a second edit while the sheet is open keeps the first`() = runTest {
    val book = book(time = 0)
    val viewModel = viewModel(repo = libraryRepo(book))
    val state = viewModel.stateIn(backgroundScope)
    state.first { it.sections.isNotEmpty() }

    viewModel.onSelectionStart(listOf(book.id))
    viewModel.onSelectionEdit(BookField.Series)
    state.first { it.selectionEdit != null }
    viewModel.onSelectionEdit(BookField.Author)

    assertEquals(expected = BookField.Series, actual = state.value.selectionEdit?.field)
  }

  @Test
  fun `selecting ends for good once the selected books leave the library`() = runTest {
    val book = book(time = 0)
    val other = book(time = 0)
    val repo = libraryRepo(book, other)
    val viewModel = viewModel(repo = repo)
    val state = viewModel.stateIn(backgroundScope)
    state.first { it.sections.isNotEmpty() }

    viewModel.onSelectionStart(listOf(book.id))
    state.first { it.selection != null }
    repo.updateCustomMetadata(book.id) { it.copy(hidden = true) }
    state.first { it.selection == null }
    repo.updateCustomMetadata(book.id) { it.copy(hidden = false) }

    assertEquals(
      expected = null,
      actual = state.first { it.sections.values.flatten().size == 2 }.selection,
    )
  }

  @Test
  fun `a selected book that leaves the library doesn't come back selected`() = runTest {
    val book = book(time = 0)
    val other = book(time = 0)
    val repo = libraryRepo(book, other)
    val viewModel = viewModel(repo = repo)
    val state = viewModel.stateIn(backgroundScope)
    state.first { it.sections.isNotEmpty() }

    viewModel.onSelectionStart(listOf(book.id, other.id))
    state.first { it.selection?.ids?.size == 2 }
    repo.updateCustomMetadata(book.id) { it.copy(hidden = true) }
    state.first { it.selection?.ids == setOf(other.id) }
    repo.updateCustomMetadata(book.id) { it.copy(hidden = false) }

    assertEquals(
      expected = setOf(other.id),
      actual = state.first { it.sections.values.flatten().size == 2 }.selection?.ids,
    )
  }

  private fun BookOverviewViewModel.stateIn(
    scope: CoroutineScope,
  ): StateFlow<BookOverviewViewState> =
    scope.launchMolecule(RecompositionMode.Immediate) { state() }

  // a repository that keeps the books in memory, so edits show in the library
  private fun libraryRepo(vararg books: Book): BookRepository {
    val stored = MutableStateFlow(books.toList())
    val repo = mockk<BookRepository>()
    every { repo.flow() } returns stored
    coEvery { repo.all() } answers { stored.value }
    coEvery { repo.get(any()) } answers
      {
        val id = firstArg<BookId>()
        stored.value.find { it.id == id }
      }
    coEvery { repo.updateCustomMetadata(any(), any()) } answers
      {
        val id = firstArg<BookId>()
        val update = secondArg<(CustomMetadata) -> CustomMetadata>()
        stored.value =
          stored.value.map { if (it.id == id) it.copy(custom = update(it.custom)) else it }
      }
    return repo
  }

  private fun Book.copyMetadata(
    author: String? = this.author,
    series: String? = this.series,
    part: String? = this.part,
  ): Book =
    copy(
      content =
        content.copy(
          metadata = content.metadata.copy(author = author, series = series, part = part),
        ),
    )

  private fun BookOverviewViewState.book(bookId: BookId): BookOverviewItemViewState {
    return sections.values
      .flatten()
      .filterIsInstance<LibraryItem.Book>()
      .single { it.id == bookId }
      .state
      .value
  }

  private fun BookOverviewViewState.keys(category: BookCategory): List<String> {
    return sections.getValue(category).map { it.key }
  }

  private fun viewModel(
    folderPickerInSettingsFeatureFlag: MemoryFeatureFlag<Boolean> = MemoryFeatureFlag(false),
    folderPickerMovedDialogShownStore: DataStore<Boolean> = MemoryDataStore(false),
    navigator: Navigator = mockk(),
    appInfoProvider: AppInfoProvider = appInfoProvider(),
    books: List<Book> = emptyList(),
    currentBookId: BookId? = null,
    scanning: Flow<Boolean> = MutableStateFlow(false),
    ebooks: List<Ebook> = emptyList(),
    showHiddenBooksStore: DataStore<Boolean> = MemoryDataStore(false),
    libraryGroupingStore: DataStore<LibraryGrouping> = MemoryDataStore(LibraryGrouping.Series),
    hiddenSourcesStore: DataStore<Set<LibrarySource>> = MemoryDataStore(emptySet()),
    repo: BookRepository = mockk {
      every { flow() } returns MutableStateFlow(books)
    },
    bookSelectionRequests: BookSelectionRequests = BookSelectionRequests(),
    ebooksStore: EbooksStore = EbooksStore(MemoryDataStore(ebooks)),
    bookDeleter: BookDeleter = mockk(),
  ): BookOverviewViewModel {
    return BookOverviewViewModel(
      repo = repo,
      mediaScanner =
        mockk<MediaScanTrigger> {
          every { scannerActive } returns scanning
          every { scan(any()) } just Runs
        },
      playStateManager = PlayStateManager(),
      playerController = mockk(),
      currentBookStoreDataStore = CurrentBookStore(MemoryDataStore(currentBookId)),
      folderPickerMovedDialogShownStore =
        FolderPickerMovedDialogShownStore(folderPickerMovedDialogShownStore),
      gridModeStore = GridModeStore(MemoryDataStore(GridMode.LIST)),
      ebooksStore = ebooksStore,
      gridCount =
        mockk<GridCount> {
          every { useGridAsDefault() } returns false
        },
      navigator = navigator,
      appInfoProvider = appInfoProvider,
      deviceHasStoragePermissionBug =
        mockk<DeviceHasStoragePermissionBug> {
          every { hasBug } returns MutableStateFlow(false)
        },
      folderPickerInSettingsFeatureFlag =
        FolderPickerInSettingsFeatureFlag(folderPickerInSettingsFeatureFlag),
      experimentalPlaybackPersistenceFeatureFlag =
        ExperimentalPlaybackPersistenceFeatureFlag(MemoryFeatureFlag(false)),
      kioskModeFeatureFlag = KioskModeFeatureFlag(MemoryFeatureFlag(false)),
      dispatcherProvider = dispatcherProvider,
      audiobookFolders = audiobookFolders,
      emptyLibraryTracker = emptyLibraryTracker,
      showHiddenBooksStore = ShowHiddenBooksStore(showHiddenBooksStore),
      libraryGroupingStore = LibraryGroupingStore(libraryGroupingStore),
      hiddenSourcesStore = HiddenSourcesStore(hiddenSourcesStore),
      bookHider = BookHider(repo, ebooksStore),
      bookSelectionRequests = bookSelectionRequests,
      selectedBooksEditor = SelectedBooksEditor(repo, ebooksStore),
      bookDeleter = bookDeleter,
    )
  }

  private fun ebook(
    lastOpened: Long,
    progress: Float = 0.3F,
  ) =
    Ebook(
      uri = mockk<Uri>(),
      name = "Ebook",
      format = EbookFormat.Epub,
      progress = progress,
      lastOpened = lastOpened,
    )

  private fun appInfoProvider(
    installTime: Instant = Instant.parse("2026-06-16T00:00:00Z"),
  ): AppInfoProvider {
    return mockk {
      every { this@mockk.installTime } returns installTime
    }
  }
}
