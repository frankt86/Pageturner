package voice.features.bookOverview.overview

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import voice.core.common.AppInfoProvider
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.Book
import voice.core.data.BookCategory
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.category
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookRepository
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.EbooksStore
import voice.core.documentfile.nameWithoutExtension
import voice.core.kioskmode.DemoAudiobook
import voice.core.kioskmode.KioskModeDemoData
import voice.core.kioskmode.KioskModeFeatureFlag
import voice.core.logging.api.Logger
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
import voice.features.bookOverview.editBook.toNameSuggestions
import voice.features.bookOverview.hideBook.BookHider
import voice.features.bookOverview.hideBook.HiddenBooks
import voice.features.bookOverview.select.BookField
import voice.features.bookOverview.select.BookSelectionRequests
import voice.features.bookOverview.select.BooksEdited
import voice.features.bookOverview.select.SelectedBooksEditor
import voice.features.bookOverview.select.SelectionEditViewState
import voice.features.bookOverview.select.SelectionViewState
import voice.features.bookOverview.select.of
import voice.navigation.Destination
import voice.navigation.Navigator

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
  private val experimentalPlaybackPersistenceFeatureFlag:
    ExperimentalPlaybackPersistenceFeatureFlag,
  private val kioskModeFeatureFlag: KioskModeFeatureFlag,
  private val dispatcherProvider: DispatcherProvider,
  private val audiobookFolders: AudiobookFolders,
  private val emptyLibraryTracker: EmptyLibraryTracker,
  private val showHiddenBooksStore: ShowHiddenBooksStore,
  private val libraryGroupingStore: LibraryGroupingStore,
  private val hiddenSourcesStore: HiddenSourcesStore,
  private val bookHider: BookHider,
  private val bookSelectionRequests: BookSelectionRequests,
  private val selectedBooksEditor: SelectedBooksEditor,
) {

  private val scope = MainScope(dispatcherProvider)
  private var dialog by mutableStateOf<BookOverviewViewState.Dialog?>(null)
  private var selection by mutableStateOf<Set<BookId>?>(null)
  private var selectionEdit by mutableStateOf<SelectionEditViewState?>(null)

  // the books on screen, so Select all takes what the listener sees
  private var shownBookIds: Set<BookId> = emptySet()

  internal val hiddenBooks: Flow<HiddenBooks>
    get() = bookHider.hidden

  internal val selectionRequests: Flow<List<BookId>>
    get() = bookSelectionRequests.requests

  internal val booksEdited: SharedFlow<BooksEdited>
    field = MutableSharedFlow<BooksEdited>(extraBufferCapacity = 1)

  fun attach() {
    mediaScanner.scan()
  }

  @Composable
  internal fun state(): BookOverviewViewState {
    val kioskMode = remember { kioskModeFeatureFlag.get() }
    if (kioskMode) return kioskModeState()

    val playState = remember {
      playStateManager.playStateFlow
    }
      .collectAsState(initial = PlayStateManager.PlayState.Paused)
      .value
    val hasStoragePermissionBug = remember {
      deviceHasStoragePermissionBug.hasBug
    }
      .collectAsState()
      .value
    // Everything shown on the first frame stays loading until known, so the library never shows a
    // half loaded state (e.g. no hero card, or top bar icons that disappear again).
    val library = remember { library() }.collectAsState(initial = null).value
    val currentBook = remember {
      currentBookStoreDataStore.data.map(::CurrentBook)
    }
      .collectAsState(initial = null)
      .value
    val scannerActive = remember {
      mediaScanner.scannerActive
    }
      .collectAsState(initial = false)
      .value
    val folderPickerMovedDialogShown = remember {
      folderPickerMovedDialogShownStore.data
    }
      .collectAsState(initial = null)
      .value
    val gridMode = remember { gridModeStore.data }.collectAsState(initial = null).value
    val hasFoldersWithoutAccess = remember {
      audiobookFolders.withoutAccess().map { folders -> folders.values.any { it.isNotEmpty() } }
    }
      .collectAsState(initial = null)
      .value
    val showHiddenBooks = remember {
      showHiddenBooksStore.data
    }
      .collectAsState(initial = null)
      .value
    val grouping = remember { libraryGroupingStore.data }.collectAsState(initial = null).value
    if (
      library == null ||
        currentBook == null ||
        folderPickerMovedDialogShown == null ||
        gridMode == null ||
        hasFoldersWithoutAccess == null ||
        showHiddenBooks == null ||
        grouping == null
    ) {
      return BookOverviewViewState.Loading
    }
    val hiddenBookCount =
      library.shownBooks.count { it.custom.hidden } + library.shownEbooks.count { it.hidden }
    val books =
      remember(library, showHiddenBooks) {
        library.shownBooks.filter { showHiddenBooks || !it.custom.hidden }
      }
    val ebooks =
      remember(library, showHiddenBooks) {
        library.shownEbooks.filter { showHiddenBooks || !it.hidden }
      }
    // a deleted book can stay the current one
    val currentBookId = currentBook.id?.takeIf { id -> books.any { it.id == id } }
    val heroBookId = heroBookId(books, ebooks, currentBookId)
    val bookIds = remember(books, ebooks) { books.map { it.id }.toSet() + ebooks.map { it.bookId } }
    SideEffect { shownBookIds = bookIds }
    // books that leave the library, like hidden ones, leave the selection too, so they don't come
    // back selected
    val selected = selection?.intersect(bookIds)?.takeIf { it.isNotEmpty() }
    LaunchedEffect(bookIds, selection) {
      selection = selection?.intersect(bookIds)?.takeIf { it.isNotEmpty() }
    }

    // with every book hidden, the folders still have books, so there's nothing to add
    val showEmptyLibrary =
      !hasStoragePermissionBug &&
        !scannerActive &&
        library.books.isEmpty() &&
        library.ebooks.isEmpty()
    LaunchedEffect(showEmptyLibrary) {
      if (showEmptyLibrary) emptyLibraryTracker.track()
    }

    val layoutMode =
      when (gridMode) {
        GridMode.LIST -> BookOverviewLayoutMode.List
        GridMode.GRID -> BookOverviewLayoutMode.Grid
        GridMode.FOLLOW_DEVICE ->
          if (gridCount.useGridAsDefault()) {
            BookOverviewLayoutMode.Grid
          } else {
            BookOverviewLayoutMode.List
          }
      }

    val experimentalPlaybackPersistence = experimentalPlaybackPersistenceFeatureFlag.get()
    val livePlaybackState: State<LivePlaybackState?> =
      if (experimentalPlaybackPersistence && currentBookId != null) {
        remember(currentBookId) {
            playerController.livePlaybackStateFlow(currentBookId)
          }
          .collectAsState(null)
      } else {
        remember { mutableStateOf(null) }
      }

    @Composable
    fun Book.itemState() =
      itemViewState(
        currentBookId = currentBookId,
        livePlaybackState = { livePlaybackState.value },
      )

    val entries =
      remember(books, ebooks, heroBookId) {
        // the Continue card shows it
        libraryEntries(books, ebooks).mapValues { (_, entries) ->
          entries.filterNot { it is LibraryEntry.Single && it.book.id == heroBookId }
        }
      }
    val authors =
      remember(entries, grouping) {
        if (grouping == LibraryGrouping.Author) entries.groupedByAuthor() else emptyMap()
      }
    val ebookStates =
      remember(ebooks) { ebooks.associate { it.bookId to mutableStateOf(it.toItemViewState()) } }

    @Composable
    fun LibraryEntry.item(): LibraryItem =
      when (this) {
        is LibraryEntry.Single ->
          when (val libraryBook = book) {
            is LibraryBook.Audiobook ->
              LibraryItem.Book(libraryBook.id, libraryBook.book.itemState())
            is LibraryBook.Ebook ->
              LibraryItem.Book(libraryBook.id, ebookStates.getValue(libraryBook.id))
          }
        is LibraryEntry.Stack -> LibraryItem.Series(series.toStackViewState())
      }

    val inProgressCount =
      remember(books, ebooks) {
        books.count { it.category == BookCategory.CURRENT } +
          ebooks.count { it.category == BookCategory.CURRENT }
      }
    return BookOverviewViewState(
      layoutMode = layoutMode,
      sections =
        entries
          .mapValues { (category, entries) ->
            val authorGroups = authors[category]
            authorGroups?.map { LibraryItem.Author(it.toEntryViewState(category)) }
              ?: entries.map { it.item() }
          }
          .filterValues { it.isNotEmpty() },
      hero =
        when (heroBookId) {
          null -> null
          currentBookId -> books.find { it.id == heroBookId }?.itemState()
          else -> ebookStates[heroBookId]
        },
      inProgressCount = inProgressCount,
      currentBookId = currentBookId,
      playButtonState =
        if (playState is PlayStateManager.PlayState.Playing) {
            BookOverviewViewState.PlayButtonState.Playing
          } else {
            BookOverviewViewState.PlayButtonState.Paused
          }
          .takeIf { currentBookId != null },
      showEmptyLibrary = showEmptyLibrary,
      showSearchIcon = library.books.isNotEmpty() || library.ebooks.isNotEmpty(),
      showFilterIcon = library.books.isNotEmpty() || library.ebooks.isNotEmpty(),
      isLoading = scannerActive,
      showStoragePermissionBugCard = hasStoragePermissionBug,
      showFolderAccessBadge = hasFoldersWithoutAccess,
      showFolderPickerIcon =
        !folderPickerInSettingsFeatureFlag.get() &&
          !folderPickerMovedDialogShown &&
          appInfoProvider.installTime < FolderPickerMigrationInstallTimeCutoff,
      dialog = dialog,
      hiddenBookCount = hiddenBookCount,
      showHiddenBooks = showHiddenBooks,
      sources = library.sources,
      grouping = grouping,
      selection =
        selected?.let { ids ->
          SelectionViewState(
            ids = ids,
            allSelected = ids.size == bookIds.size,
            allHidden =
              books.all { it.id !in ids || it.custom.hidden } &&
                ebooks.all { it.bookId !in ids || it.hidden },
          )
        },
      selectionEdit = selectionEdit,
      heroBookId = heroBookId,
    )
  }

  private fun library(): Flow<SourcedLibrary> {
    return combine(
        repo.flow(),
        ebooksStore.data.map { ebooks -> ebooks.filter { it.available } },
        sources(),
        hiddenSourcesStore.data,
      ) { books, ebooks, sources, storedHidden ->
        if (sources.size < 2) {
          return@combine SourcedLibrary(books, books, ebooks, ebooks, emptyList())
        }
        val folders = sources.mapNotNull { (source) ->
          (source as? LibrarySource.Folder)?.uri?.toUri()
        }
        val sourceOf = books.associate { it.id to it.source(folders) }
        // a book whose source has no chip, like one from a removed library, always shows
        val hidden =
          sources.map { (source) -> source }.filterTo(mutableSetOf()) { it in storedHidden }
        SourcedLibrary(
          books = books,
          shownBooks = books.filter { sourceOf[it.id] !in hidden },
          ebooks = ebooks,
          // an ebook from a catalog that lives in the app's storage has no folder, so it shows
          shownEbooks = ebooks.filter { it.source(folders) !in hidden },
          sources =
            sources.map { (source, name) ->
              LibrarySourceViewState(
                source = source,
                name = name,
                shown = source !in hidden,
              )
            },
        )
      }
      .flowOn(dispatcherProvider.io)
  }

  private fun sources(): Flow<List<Pair<LibrarySource, String>>> {
    return audiobookFolders.all().map { folders ->
      // a book that is a folder or a file of its own can be hidden on its own
      val sourceFolders = folders[FolderType.Root].orEmpty() + folders[FolderType.Author].orEmpty()
      sourceFolders
        .map { LibrarySource.Folder(it.uri.toString()) to it.documentFile.nameWithoutExtension() }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
    }
  }

  private fun kioskModeState(): BookOverviewViewState {
    return BookOverviewViewState(
      layoutMode = BookOverviewLayoutMode.List,
      sections =
        mapOf(
          BookCategory.CURRENT to
            KioskModeDemoData.demoAudiobooks
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
      navigator.goTo(
        if (ebook != null) Destination.EbookActions(ebook.uri) else Destination.BookActions(id),
      )
    }
  }

  fun onSeriesClick(name: String) {
    navigator.goTo(Destination.Series(name))
  }

  fun onSeriesLongClick(name: String) {
    navigator.goTo(Destination.SeriesActions(name))
  }

  fun onAuthorClick(name: String?) {
    navigator.goTo(Destination.Author(name))
  }

  fun onAuthorLongClick(author: AuthorEntryViewState) {
    navigator.goTo(Destination.AuthorActions(author.name, author.bookIds))
  }

  fun onGroupingChange(grouping: LibraryGrouping) {
    scope.launch {
      libraryGroupingStore.updateData { grouping }
    }
  }

  fun onShowHiddenBooksChange(show: Boolean) {
    scope.launch {
      showHiddenBooksStore.updateData { show }
    }
  }

  fun onSourceChange(
    source: LibrarySource,
    show: Boolean,
  ) {
    scope.launch {
      hiddenSourcesStore.updateData { hidden -> if (show) hidden - source else hidden + source }
    }
  }

  internal fun onUndoHide(ids: List<BookId>) {
    scope.launch {
      bookHider.show(ids)
    }
  }

  internal fun onSelectionStart(ids: List<BookId>) {
    selection = ids.toSet()
  }

  internal fun onSelectionToggle(ids: List<BookId>) {
    val selected = selectedIds()
    val toggled = if (selected.containsAll(ids)) selected - ids.toSet() else selected + ids
    selection = toggled.takeIf { it.isNotEmpty() }
  }

  internal fun onSelectAll() {
    // the top bar still takes taps while it fades out
    if (selection != null) selection = shownBookIds
  }

  internal fun onSelectionClose() {
    selection = null
  }

  internal fun onSelectionHide() {
    val ids = selectedIds().toList()
    selection = null
    scope.launch {
      bookHider.hide(ids)
    }
  }

  internal fun onSelectionShow() {
    val ids = selectedIds().toList()
    selection = null
    scope.launch {
      bookHider.show(ids)
    }
  }

  internal fun onSelectionEdit(field: BookField) {
    scope.launch {
      val books = allLibraryBooks(repo, ebooksStore)
      // while the books load, a second tap or an action that ends selecting can come in
      val ids = selectedIds()
      if (ids.isEmpty() || selectionEdit != null) return@launch
      val selectedBooks = books.filter { it.id in ids }
      selectionEdit =
        SelectionEditViewState(
          field = field,
          ids = selectedBooks.mapTo(mutableSetOf()) { it.id },
          // a value all of them share is likely what the listener wants to fix
          value = selectedBooks.map { field.of(it) }.distinct().singleOrNull().orEmpty(),
          suggestions = books.map { field.of(it) }.toNameSuggestions(),
        )
    }
  }

  internal fun onSelectionEditSave(value: String) {
    val edit = selectionEdit?.takeUnless { it.saving || it.saved } ?: return
    selectionEdit = edit.copy(saving = true)
    scope.launch {
      val edited = selectedBooksEditor.set(edit.field, edit.ids, value)
      selection = null
      selectionEdit = selectionEdit?.copy(saving = false, saved = true)
      Logger.d("Set the ${edit.field} of ${edited.count} books")
      booksEdited.emit(edited)
    }
  }

  internal fun onSelectionEditDismiss() {
    selectionEdit = null
  }

  internal fun onUndoEdit(edited: BooksEdited) {
    scope.launch {
      val _ = selectedBooksEditor.undo(edited)
    }
  }

  private fun selectedIds(): Set<BookId> = selection.orEmpty() intersect shownBookIds

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
): State<BookOverviewItemViewState> {
  if (id != currentBookId) {
    return rememberUpdatedState(toItemViewState())
  }
  val currentPlaybackState by rememberUpdatedState(livePlaybackState)
  return remember(this, currentBookId) {
    derivedStateOf {
      val livePlayback = currentPlaybackState()
      if (livePlayback != null) {
          overlay(livePlayback)
        } else {
          this
        }
        .toItemViewState()
    }
  }
}

private fun heroBookId(
  books: List<Book>,
  ebooks: List<Ebook>,
  currentBookId: BookId?,
): BookId? {
  val ebook =
    ebooks.filter { it.category == BookCategory.CURRENT }.maxByOrNull { it.lastOpened }
      ?: return currentBookId
  val playedAt =
    books.firstOrNull { it.id == currentBookId }?.content?.lastPlayedAt?.toEpochMilli() ?: 0L
  return if (ebook.lastOpened > playedAt) ebook.bookId else currentBookId
}

private fun DemoAudiobook.toItemViewState() =
  BookOverviewItemViewState(
    name = title,
    author = author,
    cover = coverUrl,
    progress = progress / 100F,
    id = id,
    remainingTime = remaining,
  )

private class SourcedLibrary(
  val books: List<Book>,
  val shownBooks: List<Book>,
  val ebooks: List<Ebook>,
  val shownEbooks: List<Ebook>,
  val sources: List<LibrarySourceViewState>,
)

/** Distinguishes "no current book" from "not loaded yet". */
internal data class CurrentBook(val id: BookId?)
