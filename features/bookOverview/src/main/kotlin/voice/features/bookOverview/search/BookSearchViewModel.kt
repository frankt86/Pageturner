package voice.features.bookOverview.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.repo.BookRepository
import voice.core.data.repo.RecentBookSearchRepo
import voice.core.data.store.CurrentBookStore
import voice.core.data.store.EbooksStore
import voice.core.featureflag.FeatureFlag
import voice.core.kioskmode.KioskModeDemoData
import voice.core.kioskmode.KioskModeFeatureFlag
import voice.core.playback.ExperimentalPlaybackPersistenceFeatureFlag
import voice.core.playback.LivePlaybackState
import voice.core.playback.PlayerController
import voice.core.playback.overlay
import voice.core.playback.playstate.PlayStateManager
import voice.core.search.BookSearch
import voice.core.search.BookSearchField
import voice.core.search.BookSearchResult
import voice.features.bookOverview.di.BookOverviewScope
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.bookId
import voice.features.bookOverview.overview.category
import voice.features.bookOverview.overview.toItemViewState
import voice.navigation.Destination
import voice.navigation.Navigator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@SingleIn(BookOverviewScope::class)
@Inject
class BookSearchViewModel(
  private val repo: BookRepository,
  private val search: BookSearch,
  private val recentBookSearchRepo: RecentBookSearchRepo,
  private val navigator: Navigator,
  private val playerController: PlayerController,
  private val playStateManager: PlayStateManager,
  private val currentBookStore: CurrentBookStore,
  private val kioskModeFeatureFlag: KioskModeFeatureFlag,
  private val experimentalPlaybackPersistenceFeatureFlag: ExperimentalPlaybackPersistenceFeatureFlag,
  private val lastResults: LastBookSearchResults,
  private val ebooksStore: EbooksStore,
  dispatcherProvider: DispatcherProvider,
) {

  private val scope = MainScope(dispatcherProvider)

  val query = TextFieldState()
  private var selectedFilter by mutableStateOf<BookSearchField?>(null)
  private var selectedBrowseCategory by mutableStateOf<BrowseCategory?>(null)

  /**
   * Navigation disposes the search while the player is open, and this view model with it. The saved state brings back
   * what was searched.
   */
  internal val saver: Saver<BookSearchViewModel, Any> = listSaver<BookSearchViewModel, String?>(
    save = { listOf(query.text.toString(), selectedFilter?.name, selectedBrowseCategory?.name) },
    restore = { (text, filter, browseCategory) ->
      query.setTextAndPlaceCursorAtEnd(text.orEmpty())
      selectedFilter = filter?.let(BookSearchField::valueOf)
      selectedBrowseCategory = browseCategory?.let(BrowseCategory::valueOf)
      this
    },
  )

  @Composable
  internal fun state(): BookSearchViewState {
    val kioskMode = remember { kioskModeFeatureFlag.get() }
    if (kioskMode) return kioskModeState()

    val recentSearches = remember { recentBookSearchRepo.flow() }
      .collectAsState(initial = emptyList()).value
    val currentBookId = remember { currentBookStore.data }
      .collectAsState(initial = null).value
    val playState = remember { playStateManager.playStateFlow }
      .collectAsState(initial = PlayStateManager.PlayState.Paused).value

    val queryText = query.text.toString().trim()
    val results = remember { queryResults() }
      // coming back from the player shows the results right away, so the cover can fly back into its row
      .collectAsState(initial = lastResults.value?.takeIf { it.query == queryText }).value
      ?.takeIf { queryText.isNotEmpty() }

    if (results == null) {
      val books = remember { repo.flow() }
        .collectAsState(initial = null).value
      val ebooks = remember { libraryEbooks() }
        .collectAsState(initial = null).value
      val browse = remember(books, ebooks, selectedBrowseCategory) {
        if (books != null && ebooks != null) browse(books, ebooks) else null
      }
      return BookSearchViewState.Idle(
        recentSearches = recentSearches,
        browse = browse,
      )
    }

    val filter = selectedFilter
    val filtered = results.results.filter { filter == null || filter in it.fieldsMatchingAllWords }
    if (filtered.isEmpty()) {
      return BookSearchViewState.NoResults(
        query = results.query,
        filtered = filter != null && results.results.isNotEmpty(),
      )
    }

    val livePlaybackState = livePlaybackState(currentBookId)
    val topResult = filtered.first().toViewState(livePlaybackState)
    return BookSearchViewState.Results(
      filters = filters(results.results, filter),
      topResult = topResult,
      topResultPlaying = topResult.id == currentBookId && playState == PlayStateManager.PlayState.Playing,
      otherResults = filtered.drop(1).map { it.toViewState(livePlaybackState) },
    )
  }

  /** With the experimental persistence, the stored position of the playing book lags behind. */
  @Composable
  private fun livePlaybackState(currentBookId: BookId?): LivePlaybackState? {
    val experimentalPlaybackPersistence = remember { experimentalPlaybackPersistenceFeatureFlag.get() }
    if (!experimentalPlaybackPersistence || currentBookId == null) return null
    return remember(currentBookId) { playerController.livePlaybackStateFlow(currentBookId) }
      .collectAsState(initial = null).value
  }

  @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
  private fun queryResults() = snapshotFlow { query.text.toString().trim() }
    .distinctUntilChanged()
    // a new search starts with all results
    .onEach { if (it.isEmpty()) selectedFilter = null }
    // clearing the field shows the recent searches right away
    .debounce { if (it.isEmpty()) Duration.ZERO else 150.milliseconds }
    // searching again when the library changes keeps the progress and the results up to date
    .combine(repo.flow()) { query, _ -> query }
    .combine(ebooksStore.data) { query, _ -> query }
    .mapLatest { query ->
      if (query.isEmpty()) {
        null
      } else {
        QueryResults(query, search.search(query))
      }
    }
    .onEach { lastResults.value = it }

  private fun filters(
    results: List<BookSearchResult>,
    selected: BookSearchField?,
  ): List<BookSearchViewState.Filter> {
    val fieldFilters = BookSearchField.entries.mapNotNull { field ->
      val count = results.count { field in it.fieldsMatchingAllWords }
      if (count > 0) {
        BookSearchViewState.Filter(field = field, count = count, selected = field == selected)
      } else {
        null
      }
    }
    // chips that each hold all results don't filter anything, unless one is picked and can be unpicked
    if (selected == null && fieldFilters.none { it.count < results.size }) return emptyList()
    return listOf(BookSearchViewState.Filter(field = null, count = results.size, selected = selected == null)) +
      fieldFilters
  }

  private fun libraryEbooks() = ebooksStore.data.map { ebooks -> ebooks.filter { it.available && !it.hidden } }

  private fun browse(
    books: List<Book>,
    ebooks: List<Ebook>,
  ): BookSearchViewState.Browse? {
    val items = books.map { book -> BrowseItem(book.category) { it.value(book) } } +
      ebooks.map { ebook -> BrowseItem(ebook.category) { it.value(ebook) } }
    val categories = BrowseCategory.entries.filter { category ->
      items.any { it.value(category) != null }
    }
    if (categories.isEmpty()) return null
    val selected = selectedBrowseCategory?.takeIf { it in categories } ?: categories.first()
    val entries = items
      .mapNotNull { item -> item.value(selected)?.let { it to item } }
      .groupBy({ (value, _) -> value.lowercase() }, { (value, item) -> value to item })
      .map { (_, values) ->
        val name = values.groupingBy { (value, _) -> value }.eachCount().maxBy { it.value }.key
        val bookCategories = values.map { (_, item) -> item.category }
        BookSearchViewState.BrowseEntry(
          name = name,
          bookCount = values.size,
          inProgressCount = bookCategories.count { it == BookOverviewCategory.CURRENT },
          finishedCount = bookCategories.count { it == BookOverviewCategory.FINISHED },
        )
      }
      .sortedWith(compareBy(NaturalOrderComparator.stringComparator) { it.name.lowercase() })
    return BookSearchViewState.Browse(
      categories = categories,
      selectedCategory = selected,
      entries = entries,
    )
  }

  private fun kioskModeState(): BookSearchViewState {
    return BookSearchViewState.Idle(
      recentSearches = listOf(KioskModeDemoData.echoesOfTomorrow.genre.lowercase()),
      browse = BookSearchViewState.Browse(
        categories = listOf(BrowseCategory.Authors, BrowseCategory.Genres),
        selectedCategory = BrowseCategory.Authors,
        entries = KioskModeDemoData.demoAudiobooks
          .groupBy { it.author }
          .map { (author, books) ->
            BookSearchViewState.BrowseEntry(
              name = author,
              bookCount = books.size,
              inProgressCount = books.count { it.progress in 1..99 },
              finishedCount = books.count { it.progress >= 100 },
            )
          }
          .sortedBy { it.name },
      ),
    )
  }

  fun onSearch() {
    saveRecentSearch(query.text.toString())
  }

  fun onFilterClick(field: BookSearchField?) {
    selectedFilter = field
  }

  fun onSearchEverythingClick() {
    selectedFilter = null
  }

  fun onBrowseCategoryClick(category: BrowseCategory) {
    selectedBrowseCategory = category
  }

  fun onBrowseEntryClick(
    category: BrowseCategory,
    name: String,
  ) {
    query.setTextAndPlaceCursorAtEnd(name)
    selectedFilter = category.field
    saveRecentSearch(name)
  }

  fun onRecentSearchClick(recentSearch: String) {
    query.setTextAndPlaceCursorAtEnd(recentSearch)
    selectedFilter = null
    saveRecentSearch(recentSearch)
  }

  fun onRemoveRecentSearch(recentSearch: String) {
    scope.launch {
      recentBookSearchRepo.delete(recentSearch)
    }
  }

  fun onClearRecentSearches() {
    scope.launch {
      recentBookSearchRepo.clear()
    }
  }

  fun onBookClick(id: BookId) {
    saveRecentSearch(query.text.toString())
    scope.launch {
      val ebook = ebook(id)
      navigator.goTo(if (ebook != null) Destination.Reader(ebook.uri) else Destination.Playback(id))
    }
  }

  fun onBookLongClick(id: BookId) {
    scope.launch {
      val ebook = ebook(id)
      navigator.goTo(if (ebook != null) Destination.EbookActions(ebook.uri) else Destination.BookActions(id))
    }
  }

  fun onPlayClick(id: BookId) {
    saveRecentSearch(query.text.toString())
    scope.launch {
      val ebook = ebook(id)
      if (ebook != null) {
        navigator.goTo(Destination.Reader(ebook.uri))
      } else if (currentBookStore.data.first() == id) {
        playerController.playPause()
      } else {
        playerController.pauseIfCurrentBookDifferentFrom(id)
        currentBookStore.updateData { id }
        playerController.play()
      }
    }
  }

  fun onFoldersClick() {
    navigator.goTo(Destination.FolderPicker)
  }

  fun onBackClick() {
    navigator.goBack()
  }

  private suspend fun ebook(id: BookId): Ebook? = ebooksStore.data.first().firstOrNull { it.bookId == id }

  private fun saveRecentSearch(query: String) {
    val trimmed = query.trim()
    if (trimmed.isNotEmpty()) {
      scope.launch {
        recentBookSearchRepo.add(trimmed)
      }
    }
  }
}

internal data class QueryResults(
  val query: String,
  val results: List<BookSearchResult>,
)

/**
 * The results of the last search. They outlive the search screen, which is disposed while the player is open.
 */
@SingleIn(AppScope::class)
@Inject
class LastBookSearchResults {
  internal var value: QueryResults? = null
}

private class BrowseItem(
  val category: BookOverviewCategory,
  val value: (BrowseCategory) -> String?,
)

private fun BrowseCategory.value(book: Book): String? {
  val value = when (this) {
    BrowseCategory.Authors -> book.author
    BrowseCategory.Series -> book.series
    BrowseCategory.Narrators -> book.content.metadata.narrator
    BrowseCategory.Genres -> book.content.metadata.genre
  }
  return value?.trim()?.takeIf { it.isNotEmpty() }
}

private fun BrowseCategory.value(ebook: Ebook): String? {
  val value = when (this) {
    BrowseCategory.Authors -> ebook.shownAuthor
    BrowseCategory.Series -> ebook.shownSeries
    BrowseCategory.Narrators, BrowseCategory.Genres -> null
  }
  return value?.trim()?.takeIf { it.isNotEmpty() }
}

private val TagFields = listOf(BookSearchField.Series, BookSearchField.Narrator, BookSearchField.Genre)

private fun BookSearchResult.toViewState(livePlaybackState: LivePlaybackState?): SearchResultViewState {
  return when (this) {
    is BookSearchResult.Audiobook -> {
      val book = livePlaybackState?.let(book::overlay) ?: book
      toViewState(book.toItemViewState(), book.category) { field -> field.text(book) }
    }
    is BookSearchResult.Ebook -> toViewState(ebook.toItemViewState(), ebook.category) { field -> field.text(ebook) }
  }
}

private fun BookSearchResult.toViewState(
  item: BookOverviewItemViewState,
  category: BookOverviewCategory,
  text: (BookSearchField) -> String?,
): SearchResultViewState {
  return SearchResultViewState(
    id = item.id,
    title = HighlightedText(item.name, matches[BookSearchField.Title].orEmpty()),
    author = item.author?.let { HighlightedText(it, matches[BookSearchField.Author].orEmpty()) },
    cover = item.cover,
    category = category,
    progress = item.progress,
    remainingTime = item.remainingTime,
    matchTag = TagFields.firstNotNullOfOrNull { field ->
      val highlights = matches[field] ?: return@firstNotNullOfOrNull null
      val fieldText = text(field) ?: return@firstNotNullOfOrNull null
      MatchTag(field, HighlightedText(fieldText, highlights))
    },
    isEbook = item.isEbook,
  )
}
