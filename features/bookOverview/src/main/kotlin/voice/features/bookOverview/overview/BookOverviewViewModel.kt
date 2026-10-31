package voice.features.bookOverview.overview

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.core.audiobookshelf.ServerDownloads
import voice.core.audiobookshelf.ServerLibrary
import voice.core.common.AppInfoProvider
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.folders.AudiobookFolders
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.EbooksStore
import voice.core.kioskmode.DemoAudiobook
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
import voice.features.bookOverview.di.BookOverviewScope
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.time.Instant

@SingleIn(BookOverviewScope::class)
@Inject
class BookOverviewViewModel(
  private val repo: BookRepository,
  private val mediaScanner: MediaScanTrigger,
  private val playStateManager: PlayStateManager,
  private val playerController: PlayerController,
  private val currentBookStoreDataStore: CurrentBookStore,
  private val folderPickerMovedDialogShownStore: FolderPickerMovedDialogShownStore,
  private val gridModeStore: GridModeStore,
  private val ebooksStore: EbooksStore,
  private val gridCount: GridCount,
  private val navigator: Navigator,
  private val appInfoProvider: AppInfoProvider,
  private val deviceHasStoragePermissionBug: DeviceHasStoragePermissionBug,
  private val folderPickerInSettingsFeatureFlag: FolderPickerInSettingsFeatureFlag,
  private val experimentalPlaybackPersistenceFeatureFlag: ExperimentalPlaybackPersistenceFeatureFlag,
  private val kioskModeFeatureFlag: KioskModeFeatureFlag,
  dispatcherProvider: DispatcherProvider,
  private val serverLibrary: ServerLibrary,
  private val serverDownloads: ServerDownloads,
  private val audiobookFolders: AudiobookFolders,
  private val emptyLibraryTracker: EmptyLibraryTracker,
) {

  private val scope = MainScope(dispatcherProvider)
  private var dialog by mutableStateOf<BookOverviewViewState.Dialog?>(null)

  fun attach() {
    mediaScanner.scan()
    serverLibrary.sync()
  }

  @Composable
  internal fun state(): BookOverviewViewState {
    val kioskMode = remember { kioskModeFeatureFlag.get() }
    if (kioskMode) return kioskModeState()

    val playState = remember { playStateManager.playStateFlow }
      .collectAsState(initial = PlayStateManager.PlayState.Paused).value
    val hasStoragePermissionBug = remember { deviceHasStoragePermissionBug.hasBug }
      .collectAsState().value
    // Everything shown on the first frame stays loading until known, so the library never shows a
    // half loaded state (e.g. no hero card, or top bar icons that disappear again).
    val books = remember { repo.flow() }
      .collectAsState(initial = null).value
    val ebooks = remember { ebooksStore.data.map { ebooks -> ebooks.filter { it.available && !it.hidden } } }
      .collectAsState(initial = null).value
    val currentBook = remember { currentBookStoreDataStore.data.map(::CurrentBook) }
      .collectAsState(initial = null).value
    val scanning = remember { mediaScanner.scannerActive }
      .collectAsState(initial = false).value
    val syncing = remember { serverLibrary.syncing }.collectAsState(initial = false).value
    val scannerActive = scanning || syncing
    val unavailableBooks = remember { serverLibrary.unavailableBooks }
      .collectAsState(initial = emptySet()).value
    val downloadStates = remember { serverDownloads.states }
      .collectAsState(initial = emptyMap()).value
    val folderPickerMovedDialogShown = remember { folderPickerMovedDialogShownStore.data }
      .collectAsState(initial = null).value
    val gridMode = remember { gridModeStore.data }
      .collectAsState(initial = null).value
    val hasFoldersWithoutAccess = remember {
      audiobookFolders.withoutAccess().map { folders -> folders.values.any { it.isNotEmpty() } }
    }.collectAsState(initial = null).value
    if (books == null ||
      ebooks == null ||
      currentBook == null ||
      folderPickerMovedDialogShown == null ||
      gridMode == null ||
      hasFoldersWithoutAccess == null
    ) {
      return BookOverviewViewState.Loading
    }
    // a deleted book can stay the current one
    val currentBookId = currentBook.id?.takeIf { id -> books.any { it.id == id } }
    val heroBookId = heroBookId(books, ebooks, currentBookId)

    val showEmptyLibrary = !hasStoragePermissionBug && !scannerActive && books.isEmpty() && ebooks.isEmpty()
    LaunchedEffect(showEmptyLibrary) {
      if (showEmptyLibrary) emptyLibraryTracker.track()
    }

    val layoutMode = when (gridMode) {
      GridMode.LIST -> BookOverviewLayoutMode.List
      GridMode.GRID -> BookOverviewLayoutMode.Grid
      GridMode.FOLLOW_DEVICE -> if (gridCount.useGridAsDefault()) {
        BookOverviewLayoutMode.Grid
      } else {
        BookOverviewLayoutMode.List
      }
    }

    val experimentalPlaybackPersistence = experimentalPlaybackPersistenceFeatureFlag.get()
    val livePlaybackState: State<LivePlaybackState?> = if (experimentalPlaybackPersistence && currentBookId != null) {
      remember(currentBookId) {
        playerController.livePlaybackStateFlow(currentBookId)
      }.collectAsState(null)
    } else {
      remember { mutableStateOf(null) }
    }

    @Composable
    fun Book.itemState() = itemViewState(
      currentBookId = currentBookId,
      livePlaybackState = { livePlaybackState.value },
      unavailable = id in unavailableBooks,
      download = downloadStates[id].badge(),
    )

    val entries = remember(books, ebooks) { libraryEntries(books, ebooks) }
    val ebookStates = remember(ebooks) { ebooks.associate { it.bookId to mutableStateOf(it.toItemViewState()) } }
    val inProgressCount = remember(books, ebooks) {
      books.count { it.category == BookOverviewCategory.CURRENT } +
        ebooks.count { it.category == BookOverviewCategory.CURRENT }
    }
    return BookOverviewViewState(
      layoutMode = layoutMode,
      sections = BookOverviewCategory.entries
        .associateWith { category ->
          entries[category].orEmpty().mapNotNull { entry ->
            when (entry) {
              // the Continue card shows it
              is LibraryEntry.Single -> if (entry.book.id == heroBookId) {
                null
              } else {
                when (val book = entry.book) {
                  is LibraryBook.Audiobook -> LibraryItem.Book(book.id, book.book.itemState())
                  is LibraryBook.Ebook -> LibraryItem.Book(book.id, ebookStates.getValue(book.id))
                }
              }
              is LibraryEntry.Stack -> LibraryItem.Series(entry.series.toStackViewState())
            }
          }
        }
        .filterValues { it.isNotEmpty() },
      hero = when (heroBookId) {
        null -> null
        currentBookId -> books.find { it.id == heroBookId }?.itemState()
        else -> ebookStates[heroBookId]
      },
      inProgressCount = inProgressCount,
      currentBookId = currentBookId,
      playButtonState = if (playState == PlayStateManager.PlayState.Playing) {
        BookOverviewViewState.PlayButtonState.Playing
      } else {
        BookOverviewViewState.PlayButtonState.Paused
      }.takeIf { currentBookId != null },
      showEmptyLibrary = showEmptyLibrary,
      showSearchIcon = books.isNotEmpty() || ebooks.isNotEmpty(),
      isLoading = scannerActive,
      showStoragePermissionBugCard = hasStoragePermissionBug,
      showFolderAccessBadge = hasFoldersWithoutAccess,
      showFolderPickerIcon = !folderPickerInSettingsFeatureFlag.get() &&
        !folderPickerMovedDialogShown &&
        appInfoProvider.installTime < FolderPickerMigrationInstallTimeCutoff,
      dialog = dialog,
      heroBookId = heroBookId,
    )
  }

  private fun kioskModeState(): BookOverviewViewState {
    return BookOverviewViewState(
      layoutMode = BookOverviewLayoutMode.List,
      sections = mapOf(
        BookOverviewCategory.CURRENT to KioskModeDemoData.demoAudiobooks
          .filter { it.id != KioskModeDemoData.currentlyPlaying.id }
          .map { book -> LibraryItem.Book(book.id, mutableStateOf(book.toItemViewState())) },
      ),
      hero = mutableStateOf(KioskModeDemoData.currentlyPlaying.toItemViewState()),
      inProgressCount = KioskModeDemoData.demoAudiobooks.size,
      currentBookId = KioskModeDemoData.currentlyPlaying.id,
      playButtonState = BookOverviewViewState.PlayButtonState.Paused,
      showEmptyLibrary = false,
      showSearchIcon = true,
      isLoading = false,
      showStoragePermissionBugCard = false,
      showFolderAccessBadge = false,
      showFolderPickerIcon = false,
      dialog = null,
    )
  }

  fun onSettingsClick() {
    navigator.goTo(Destination.Settings)
  }

  fun onSearchClick() {
    navigator.goTo(Destination.LibrarySearch)
  }

  fun onBookClick(id: BookId) {
    scope.launch {
      val ebook = ebooksStore.data.first().firstOrNull { it.bookId == id }
      navigator.goTo(if (ebook != null) Destination.Reader(ebook.uri) else Destination.Playback(id))
    }
  }

  fun onBookLongClick(id: BookId) {
    scope.launch {
      val ebook = ebooksStore.data.first().firstOrNull { it.bookId == id }
      navigator.goTo(if (ebook != null) Destination.EbookActions(ebook.uri) else Destination.BookActions(id))
    }
  }

  fun onSeriesClick(name: String) {
    navigator.goTo(Destination.Series(name))
  }

  fun onBookFolderClick() {
    dialog = BookOverviewViewState.Dialog.FolderPickerMovedToSettings
  }

  fun onFolderPickerMovedDialogDismiss() {
    dialog = null
    scope.launch {
      folderPickerMovedDialogShownStore.updateData { true }
    }
  }

  fun playPause() {
    playerController.playPause()
  }

  fun onShowFoldersClick() {
    navigator.goTo(Destination.FolderPicker)
  }

  fun onPermissionBugCardClick() {
    if (Build.VERSION.SDK_INT >= 30) {
      navigator.goTo(
        Destination.Activity(
          Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData("package:com.android.externalstorage".toUri()),
        ),
      )
    }
  }
}

private val FolderPickerMigrationInstallTimeCutoff = Instant.parse("2026-06-17T00:00:00Z")

@Composable
private fun Book.itemViewState(
  currentBookId: BookId?,
  livePlaybackState: () -> LivePlaybackState?,
  unavailable: Boolean,
  download: DownloadBadge?,
): State<BookOverviewItemViewState> {
  if (id != currentBookId) {
    return rememberUpdatedState(toItemViewState(unavailable, download))
  }
  val currentPlaybackState by rememberUpdatedState(livePlaybackState)
  return remember(this, currentBookId, unavailable, download) {
    derivedStateOf {
      val livePlayback = currentPlaybackState()
      if (livePlayback != null) {
        overlay(livePlayback)
      } else {
        this
      }.toItemViewState(unavailable, download)
    }
  }
}

private fun heroBookId(
  books: List<Book>,
  ebooks: List<Ebook>,
  currentBookId: BookId?,
): BookId? {
  val ebook = ebooks.filter { it.category == BookOverviewCategory.CURRENT }.maxByOrNull { it.lastOpened }
    ?: return currentBookId
  val playedAt = books.firstOrNull { it.id == currentBookId }?.content?.lastPlayedAt?.toEpochMilli() ?: 0L
  return if (ebook.lastOpened > playedAt) ebook.bookId else currentBookId
}

private fun DemoAudiobook.toItemViewState() = BookOverviewItemViewState(
  name = title,
  author = author,
  cover = coverUrl,
  progress = progress / 100F,
  id = id,
  remainingTime = remaining,
)

/** Distinguishes "no current book" from "not loaded yet". */
internal data class CurrentBook(val id: BookId?)
