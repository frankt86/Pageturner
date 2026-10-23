@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import voice.core.common.rootGraphAs
import voice.core.data.EbookPosition
import voice.core.data.Highlight
import voice.core.data.HighlightColor
import voice.core.data.ProgressVisibility
import voice.core.data.ReaderOrientation
import voice.core.data.ReaderPages
import voice.core.data.ReaderSettings
import voice.core.data.ReadingMode
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import kotlin.time.Duration.Companion.seconds
import voice.core.strings.R as StringsR

@ContributesTo(AppScope::class)
interface ReaderGraph {
  val readerViewModelFactory: ReaderViewModel.Factory
}

@BindingContainer
@ContributesTo(AppScope::class)
object ReaderProvider {

  @Provides
  @IntoSet
  fun readerNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.Reader> { key ->
    NavEntry(key) {
      ReaderScreen(uri = key.uri)
    }
  }
}

@Composable
internal fun ReaderScreen(uri: Uri) {
  val viewModel = retain(uri.toString()) {
    rootGraphAs<ReaderGraph>().readerViewModelFactory.create(uri)
  }
  val activity = LocalActivity.current
  DisposableEffect(viewModel) {
    onDispose {
      // folding and unfolding recreates the activity, reading aloud carries on through that
      if (activity?.isChangingConfigurations != true) {
        viewModel.close()
      }
    }
  }
  val snackbarHostState = remember { SnackbarHostState() }
  val unavailableMessage = stringResource(StringsR.string.reader_read_aloud_unavailable)
  val nothingToReadMessage = stringResource(StringsR.string.reader_nothing_to_read_aloud)
  val naturalVoiceUnavailableMessage = stringResource(StringsR.string.reader_voice_natural_unavailable)
  LaunchedEffect(viewModel) {
    viewModel.messages.collect { message ->
      snackbarHostState.showSnackbar(
        when (message) {
          ReaderMessage.ReadAloudUnavailable -> unavailableMessage
          ReaderMessage.NothingToReadAloud -> nothingToReadMessage
          ReaderMessage.NaturalVoiceUnavailable -> naturalVoiceUnavailableMessage
        },
      )
    }
  }
  val view = LocalView.current
  DisposableEffect(view) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }
  }
  val state = viewModel.state()
  val loaded = state as? ReaderState.Loaded
  var sheet by rememberSaveable { mutableStateOf<ReaderSheet?>(null) }
  // the bars show when the book opens and get out of the way once reading starts
  var barsVisible by rememberSaveable { mutableStateOf(true) }
  val selection = remember { PageSelection() }
  var visiblePages by remember { mutableStateOf(emptyList<Page>()) }
  var editedHighlight by remember { mutableStateOf<Highlight?>(null) }
  val readiumJumps = remember { MutableSharedFlow<Locator>(extraBufferCapacity = 1) }
  val readiumJump = { locator: Locator ->
    viewModel.onReadiumJump()
    val _ = readiumJumps.tryEmit(locator)
  }
  var editedReadiumHighlight by remember { mutableStateOf<LocatedHighlight?>(null) }
  // each request turns a page or scrolls a screen, forward for a positive step
  val turnRequests = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
  // automatic scrolling or page turns, which wait while the bars, a sheet, a selection or reading aloud are on
  var autoRunning by rememberSaveable { mutableStateOf(false) }
  val scrollMode = loaded?.settings?.readingMode == ReadingMode.Scroll
  val autoPaused = barsVisible || sheet != null || editedHighlight != null || editedReadiumHighlight != null ||
    selection.range != null || loaded?.readingAloud == true
  val autoScrollSpeed = loaded?.settings?.autoScrollSpeed?.takeIf { autoRunning && scrollMode && !autoPaused }
  val autoTurnSeconds = loaded?.settings?.autoTurnSeconds?.takeIf { autoRunning && !scrollMode && !autoPaused }
  // any turn, also one by hand, starts the wait anew
  val place: Any? = loaded?.readium?.locator ?: loaded?.position
  LaunchedEffect(autoTurnSeconds, place) {
    if (autoTurnSeconds == null) return@LaunchedEffect
    delay(autoTurnSeconds.seconds)
    val _ = turnRequests.tryEmit(1)
  }
  ReaderWindow(
    immersive = !barsVisible && loaded != null,
    brightness = loaded?.settings?.brightness,
    orientation = loaded?.settings?.orientation ?: ReaderOrientation.Auto,
  )
  val turnPage = { page: Page, progress: Float ->
    barsVisible = false
    viewModel.onPageTurned(page, progress)
  }
  Box(
    Modifier
      .fillMaxSize()
      .background(loaded?.settings?.pageColors()?.paper ?: MaterialTheme.colorScheme.surface),
  ) {
    when (state) {
      ReaderState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(64.dp))
      }
      ReaderState.Failed -> Box(
        Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(StringsR.string.reader_failed),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
        )
      }
      is ReaderState.Loaded -> if (state.readium != null) {
        Box(
          Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
            .padding(top = state.settings.topMargin?.dp ?: 0.dp, bottom = state.settings.bottomMargin?.dp ?: 0.dp),
        ) {
          val characters = (state.content as? ReaderContent.Epub)?.totalLength ?: 0
          Column(Modifier.fillMaxSize()) {
            ReadiumPages(
              book = state.readium,
              settings = state.settings,
              // reading aloud keeps the volume keys for the volume
              volumeKeysTurnPages = state.settings.volumeKeysTurnPages && !state.readingAloud,
              transition = state.settings.pageTransition,
              jumps = readiumJumps,
              turnRequests = turnRequests,
              autoScrollSpeed = autoScrollSpeed,
              onAutoScrollEnd = { autoRunning = false },
              onLocatorChange = viewModel::onLocatorChange,
              onPageTurn = { barsVisible = false },
              onCenterTap = { barsVisible = !barsVisible },
              onHighlightAdd = { locator, color -> val _ = viewModel.onReadiumHighlightAdd(locator, color) },
              onNoteAdd = { locator -> editedReadiumHighlight = viewModel.onReadiumHighlightAdd(locator, HighlightColor.Yellow) },
              onHighlightClick = { editedReadiumHighlight = it },
              modifier = Modifier
                .fillMaxWidth()
                .weight(1F),
            )
            if (state.settings.progressVisibility == ProgressVisibility.Always) {
              ReadiumFooter(
                book = state.readium,
                settings = state.settings,
                characters = characters,
                pace = state.pace,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
          ReadiumPageBar(
            book = state.readium,
            settings = state.settings,
            characters = characters,
            pace = state.pace,
            visible = barsVisible,
            onJump = readiumJump,
          )
        }
      } else {
        Book(
          state = state,
          onPageTurn = turnPage,
          selection = selection,
          onHighlightClick = { editedHighlight = it },
          onVisiblePagesChange = { visiblePages = it },
          turnRequests = turnRequests,
          onTurnRequest = { val _ = turnRequests.tryEmit(it) },
          onCenterTap = { barsVisible = !barsVisible },
          autoScrollSpeed = autoScrollSpeed,
          onScroll = viewModel::onScrolled,
          onUserScroll = { barsVisible = false },
          onAutoScrollEnd = { autoRunning = false },
          // the insets of hidden bars too, or showing them would lay out all pages anew
          modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)),
          overlay = { pages, pagesPerSpread, turn ->
            ReaderControls(
              state = state,
              pages = pages,
              pagesPerSpread = pagesPerSpread,
              barsVisible = barsVisible,
              onTurn = turn,
              onJump = viewModel::onPageTurned,
            )
          },
        )
      }
    }
    val selected = selection.range
    val epub = loaded?.content as? ReaderContent.Epub
    if (selected != null && epub != null) {
      SelectionToolbar(
        selectedText = epub.text(selected),
        highlightNames = loaded.settings.highlightNames,
        onHighlightClick = { color ->
          val _ = viewModel.onHighlightAdd(selected, color)
          selection.clear()
        },
        onNoteClick = {
          editedHighlight = viewModel.onHighlightAdd(selected, HighlightColor.Yellow)
          selection.clear()
        },
        onLookUp = selection::clear,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .safeDrawingPadding()
          .padding(16.dp),
      )
    }
    AnimatedVisibility(
      visible = barsVisible || loaded == null,
      enter = fadeIn() + slideInVertically { -it },
      exit = fadeOut() + slideOutVertically { -it },
    ) {
      val readium = loaded?.readium
      ReaderTopBar(
        title = loaded?.content?.title,
        readingAloud = loaded?.readingAloud,
        bookmarked = if (readium != null) {
          readium.bookmarks.any { readium.isOnPage(it, readium.locator) }
        } else {
          loaded?.bookmarks?.any { bookmark -> visiblePages.any { it.contains(bookmark) } }
        },
        showSearch = loaded?.content?.searchable == true,
        auto = when {
          loaded == null -> null
          scrollMode -> AutoMode.Scroll
          else -> AutoMode.Turn
        },
        autoRunning = autoRunning,
        onCloseClick = viewModel::onCloseClick,
        onReadAloudClick = {
          if (loaded?.readingAloud == true) viewModel.stopReadingAloudClick() else sheet = ReaderSheet.ReadAloud
        },
        onContentsClick = { sheet = ReaderSheet.Contents },
        onDisplayClick = { sheet = ReaderSheet.Display },
        onBookmarkClick = {
          if (readium != null) viewModel.onReadiumBookmarkClick() else viewModel.onBookmarkClick(visiblePages)
        },
        onSearchClick = { sheet = ReaderSheet.Search },
        onAutoClick = {
          autoRunning = !autoRunning
          // the bars wait it out, so they make way
          if (autoRunning) barsVisible = false
        },
      )
    }
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .safeDrawingPadding(),
    )
  }
  val content = loaded?.content as? ReaderContent.Epub
  if (loaded != null && sheet == ReaderSheet.Display) {
    DisplaySheet(
      settings = loaded.settings,
      showTextOptions = content != null,
      newEngine = loaded.readium != null,
      fonts = loaded.fonts,
      fontImportFailed = loaded.fontImportFailed,
      onFontImport = viewModel::onFontImport,
      onSettingsChange = viewModel::onSettingsChange,
      onColorsClick = { sheet = ReaderSheet.Colors },
      onDismiss = { sheet = null },
    )
  }
  if (loaded != null && sheet == ReaderSheet.Colors) {
    ColorsSheet(
      settings = loaded.settings,
      content = when {
        loaded.readium != null -> ColorsContent.NewEngine
        content != null -> ColorsContent.OldEngine
        else -> ColorsContent.Pdf
      },
      themesFileFailed = loaded.themesFileFailed,
      onThemesExport = viewModel::onThemesExport,
      onThemesImport = viewModel::onThemesImport,
      onSettingsChange = viewModel::onSettingsChange,
      onDismiss = { sheet = null },
    )
  }
  if (loaded != null && sheet == ReaderSheet.ReadAloud) {
    ReadAloudSheet(
      voice = loaded.voice,
      onVoiceSelect = viewModel::onVoiceSelect,
      onSpeakerSelect = viewModel::onSpeakerSelect,
      onSpeechRateChange = viewModel::onSpeechRateChange,
      onStartClick = {
        sheet = null
        viewModel.onStartReadingClick()
      },
      onDismiss = { sheet = null },
    )
  }
  val readium = loaded?.readium
  if (readium != null && sheet == ReaderSheet.Contents) {
    ReadiumContentsSheet(
      book = readium,
      onJump = { locator ->
        sheet = null
        readiumJump(locator)
      },
      onBookmarkDelete = viewModel::onReadiumBookmarkDelete,
      onHighlightDelete = viewModel::onReadiumHighlightDelete,
      highlightOpacity = loaded.settings.highlightOpacity,
      onDismiss = { sheet = null },
    )
  } else if (readium != null && sheet == ReaderSheet.Search) {
    SearchSheet(
      search = viewModel::searchReadium,
      resultTitle = { result -> readium.chapterTitle(result).orEmpty() },
      resultSnippet = { result -> result.searchSnippet() },
      onResultClick = { result ->
        sheet = null
        readiumJump(result)
        viewModel.onReadiumSearchResultClick(result)
      },
      onDismiss = { sheet = null },
    )
  } else if (loaded != null) {
    when (sheet) {
      ReaderSheet.Contents -> ContentsSheet(
        state = loaded,
        onChapterClick = { section ->
          sheet = null
          viewModel.onChapterClick(section)
        },
        onMarkClick = { position ->
          sheet = null
          viewModel.onMarkClick(position)
        },
        onBookmarkDelete = viewModel::onBookmarkDelete,
        onHighlightDelete = viewModel::onHighlightDelete,
        onDismiss = { sheet = null },
      )
      ReaderSheet.Search -> SearchSheet(
        content = loaded.content,
        search = viewModel::search,
        onResultClick = { result ->
          sheet = null
          viewModel.onSearchResultClick(result)
        },
        onDismiss = { sheet = null },
      )
      ReaderSheet.Display, ReaderSheet.Colors, ReaderSheet.ReadAloud, null -> Unit
    }
  }
  val readiumHighlight = editedReadiumHighlight
  if (readiumHighlight != null) {
    ReadiumHighlightSheet(
      highlight = readiumHighlight,
      settings = loaded?.settings ?: ReaderSettings(),
      onDone = { color, note ->
        editedReadiumHighlight = null
        if (color != readiumHighlight.color || note != readiumHighlight.note) {
          viewModel.onReadiumHighlightChange(readiumHighlight, color, note)
        }
      },
      onDelete = {
        editedReadiumHighlight = null
        viewModel.onReadiumHighlightDelete(readiumHighlight)
      },
    )
  }
  val highlight = editedHighlight
  if (highlight != null && content != null) {
    HighlightSheet(
      highlight = highlight,
      text = content.text(BookRange(highlight.section, highlight.start, highlight.end)),
      highlightNames = loaded.settings.highlightNames,
      highlightOpacity = loaded.settings.highlightOpacity,
      onDone = { changed ->
        editedHighlight = null
        if (changed != highlight) viewModel.onHighlightChange(highlight, changed)
      },
      onDelete = {
        editedHighlight = null
        viewModel.onHighlightDelete(highlight)
      },
    )
  }
}

private fun Locator.searchSnippet(): SearchSnippet {
  val before = text.before.orEmpty().replace('\n', ' ').takeLast(40)
  val match = text.highlight.orEmpty()
  val after = text.after.orEmpty().replace('\n', ' ').take(80)
  val prefix = if (before.isEmpty()) "" else "…"
  return SearchSnippet(
    text = prefix + before + match + after + "…",
    matchStart = prefix.length + before.length,
    matchEnd = prefix.length + before.length + match.length,
  )
}

private enum class ReaderSheet {
  Contents,
  Display,
  Colors,
  ReadAloud,
  Search,
}

@Composable
private fun ReadiumHighlightSheet(
  highlight: LocatedHighlight,
  settings: ReaderSettings,
  onDone: (HighlightColor, String?) -> Unit,
  onDelete: () -> Unit,
) {
  // the sheet edits only the color and the note, the place in the text isn't used
  val editable = remember(highlight) { Highlight(section = 0, start = 0, end = 0, color = highlight.color, note = highlight.note) }
  HighlightSheet(
    highlight = editable,
    text = highlight.locator.text.highlight.orEmpty(),
    highlightNames = settings.highlightNames,
    highlightOpacity = settings.highlightOpacity,
    onDone = { changed -> onDone(changed.color, changed.note) },
    onDelete = onDelete,
  )
}

@Composable
internal fun ReaderTopBar(
  title: String?,
  readingAloud: Boolean?,
  // null while the book loads
  bookmarked: Boolean?,
  showSearch: Boolean,
  // null while the book loads
  auto: AutoMode?,
  autoRunning: Boolean,
  onCloseClick: () -> Unit,
  onReadAloudClick: () -> Unit,
  onContentsClick: () -> Unit,
  onDisplayClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onSearchClick: () -> Unit,
  onAutoClick: () -> Unit,
) {
  TopAppBar(
    title = {
      if (title != null) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium.bookText(),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    navigationIcon = {
      val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
      IconButton(onClick = onCloseClick) {
        Icon(
          modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1F else 1F },
          imageVector = VoiceIcons.ArrowBack,
          contentDescription = stringResource(StringsR.string.common_action_back),
        )
      }
    },
    actions = {
      if (showSearch) {
        IconButton(onClick = onSearchClick) {
          Icon(
            imageVector = VoiceIcons.Search,
            contentDescription = stringResource(StringsR.string.reader_search),
          )
        }
      }
      if (bookmarked != null) {
        IconButton(onClick = onBookmarkClick) {
          Icon(
            imageVector = if (bookmarked) VoiceIcons.BookmarkFilled else VoiceIcons.Bookmark,
            contentDescription = stringResource(
              if (bookmarked) StringsR.string.reader_bookmark_remove else StringsR.string.reader_bookmark_add,
            ),
          )
        }
      }
      if (title != null) {
        IconButton(onClick = onContentsClick) {
          Icon(
            imageVector = VoiceIcons.ViewList,
            contentDescription = stringResource(StringsR.string.reader_contents),
          )
        }
        IconButton(onClick = onDisplayClick) {
          Icon(
            imageVector = VoiceIcons.Title,
            contentDescription = stringResource(StringsR.string.reader_display),
          )
        }
      }
      if (auto != null) {
        IconToggleButton(checked = autoRunning, onCheckedChange = { onAutoClick() }) {
          Icon(
            imageVector = if (autoRunning) VoiceIcons.Pause else VoiceIcons.Speed,
            contentDescription = stringResource(
              when (auto) {
                AutoMode.Scroll -> StringsR.string.reader_auto_scroll
                AutoMode.Turn -> StringsR.string.reader_auto_turn
              },
            ),
          )
        }
      }
      if (readingAloud != null) {
        IconButton(onClick = onReadAloudClick) {
          Icon(
            imageVector = if (readingAloud) VoiceIcons.Pause else VoiceIcons.VolumeUp,
            contentDescription = stringResource(
              if (readingAloud) StringsR.string.reader_stop_reading_aloud else StringsR.string.reader_read_aloud,
            ),
          )
        }
      }
    },
  )
}

/** What the automatic button of the top bar does. */
internal enum class AutoMode {
  Scroll,
  Turn,
}

private val PageVerticalPadding = 8.dp

/** The space around the text of a page. */
@Immutable
internal data class PageMargins(
  val side: Dp,
  val top: Dp,
  val bottom: Dp,
)

@Composable
private fun ReaderSettings.pageMargins(pageWidth: Dp): PageMargins {
  val sideMargin = margin.dp
  // about 65 characters per line
  val maxTextWidth = with(LocalDensity.current) { (textSize * 32).sp.toDp() }
  return PageMargins(
    side = if (shortLines) max(sideMargin, (pageWidth - maxTextWidth) / 2) else sideMargin,
    top = topMargin?.dp ?: PageVerticalPadding,
    bottom = bottomMargin?.dp ?: PageVerticalPadding,
  )
}
internal val PageNumberHeight = 32.dp
private val SpineWidth = 24.dp

@Composable
internal fun Book(
  state: ReaderState.Loaded,
  onPageTurn: (Page, Float) -> Unit,
  selection: PageSelection,
  onHighlightClick: (Highlight) -> Unit,
  onVisiblePagesChange: (List<Page>) -> Unit,
  turnRequests: Flow<Int>,
  onTurnRequest: (step: Int) -> Unit,
  modifier: Modifier = Modifier,
  onCenterTap: () -> Unit = {},
  // in dp per second while the scrolled book moves by itself
  autoScrollSpeed: Int? = null,
  onScroll: (EbookPosition) -> Unit = {},
  onUserScroll: () -> Unit = {},
  onAutoScrollEnd: () -> Unit = {},
  // previews can't wait for the pages laid out in the background
  paginateNow: Boolean = false,
  // previews can't swipe, this shows the first page turned this far
  previewTurn: Float? = null,
  // turn is called with 1 for the next page and -1 for the previous one
  overlay: @Composable BoxScope.(pages: List<Page>, pagesPerSpread: Int, turn: (Int) -> Unit) -> Unit = { _, _, _ -> },
) {
  BoxWithConstraints(modifier) {
    val scroll = state.settings.readingMode == ReadingMode.Scroll
    val twoPages = !scroll && when (state.settings.pages) {
      // an unfolded foldable, a tablet or a phone on its side has room for a spread like an open book
      ReaderPages.Auto -> maxWidth >= 600.dp && maxWidth >= maxHeight * 0.8F
      ReaderPages.One -> false
      ReaderPages.Two -> true
    }
    val spineWidth = state.settings.pageGap?.dp ?: SpineWidth
    val pageWidth = if (twoPages) (maxWidth - spineWidth) / 2 else maxWidth
    val textStyle = state.settings.pageTextStyle()
    val paragraphGap = state.settings.paragraphGap()
    val margins = state.settings.pageMargins(pageWidth)
    val pages = pages(
      content = state.content,
      textStyle = textStyle,
      paragraphGap = paragraphGap,
      textWidth = pageWidth - margins.side * 2,
      textHeight = maxHeight - margins.top - margins.bottom - PageNumberHeight,
      paginateNow = paginateNow,
    )
    if (pages == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(64.dp))
      }
      return@BoxWithConstraints
    }
    val pagesPerSpread = if (twoPages) 2 else 1
    ReaderPageTheme(state.settings.pageColors()) {
      if (scroll) {
        ScrollBook(
          state = state,
          pages = pages,
          textStyle = textStyle,
          margins = margins,
          selection = selection,
          turnRequests = turnRequests,
          autoScrollSpeed = autoScrollSpeed,
          onScroll = onScroll,
          onUserScroll = onUserScroll,
          onHighlightClick = onHighlightClick,
          onVisiblePagesChange = onVisiblePagesChange,
          onCenterTap = onCenterTap,
          onAutoScrollEnd = onAutoScrollEnd,
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        )
      } else {
        key(pages, pagesPerSpread) {
          Spreads(
            state = state,
            pages = pages,
            pagesPerSpread = pagesPerSpread,
            textStyle = textStyle,
            paragraphGap = paragraphGap,
            margins = margins,
            spineWidth = spineWidth,
            onPageTurn = onPageTurn,
            selection = selection,
            onHighlightClick = onHighlightClick,
            onVisiblePagesChange = onVisiblePagesChange,
            onCenterTap = onCenterTap,
            turnRequests = turnRequests,
            previewTurn = previewTurn,
          )
        }
      }
    }
    overlay(pages, pagesPerSpread, onTurnRequest)
  }
}

@Composable
private fun pages(
  content: ReaderContent,
  textStyle: TextStyle,
  paragraphGap: TextUnit,
  textWidth: Dp,
  textHeight: Dp,
  paginateNow: Boolean,
): List<Page>? {
  return when (content) {
    is ReaderContent.Pdf -> remember(content) { List(content.sectionCount) { Page(section = it) } }
    is ReaderContent.Epub -> {
      val textMeasurer = rememberTextMeasurer(cacheSize = 0)
      val density = LocalDensity.current
      val width = with(density) { textWidth.roundToPx() }
      val height = with(density) { textHeight.roundToPx() }
      if (paginateNow) {
        remember(content, textStyle, paragraphGap, width, height) {
          paginate(content.chapters, textMeasurer, textStyle, paragraphGap, width, height)
        }
      } else {
        produceState<List<Page>?>(null, content, textStyle, paragraphGap, width, height, textMeasurer) {
          value = withContext(Dispatchers.Default) {
            paginate(content.chapters, textMeasurer, textStyle, paragraphGap, width, height)
          }
        }.value
      }
    }
  }
}

@Composable
private fun Spreads(
  state: ReaderState.Loaded,
  pages: List<Page>,
  pagesPerSpread: Int,
  textStyle: TextStyle,
  paragraphGap: TextUnit,
  margins: PageMargins,
  spineWidth: Dp,
  onPageTurn: (Page, Float) -> Unit,
  selection: PageSelection,
  onHighlightClick: (Highlight) -> Unit,
  onVisiblePagesChange: (List<Page>) -> Unit,
  onCenterTap: () -> Unit,
  turnRequests: Flow<Int>,
  previewTurn: Float?,
) {
  if (pages.isEmpty()) return
  val latestState by rememberUpdatedState(state)
  val latestOnHighlightClick by rememberUpdatedState(onHighlightClick)
  val latestOnVisiblePagesChange by rememberUpdatedState(onVisiblePagesChange)
  val haptics = LocalHapticFeedback.current
  fun Modifier.pageGestures(
    shownPages: () -> IntRange,
    onOtherTap: (Offset, IntSize) -> Boolean,
  ) = selectableText(
    selection = selection,
    shownPages = shownPages,
    state = { latestState },
    onHighlightClick = { latestOnHighlightClick(it) },
    onSelect = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
    onOtherTap = onOtherTap,
  )

  val current = pages.indexOfPosition(state.position) / pagesPerSpread * pagesPerSpread
  val latestCurrent by rememberUpdatedState(current)
  LaunchedEffect(current) {
    selection.clear()
    latestOnVisiblePagesChange(pages.subList(current, (current + pagesPerSpread).coerceAtMost(pages.size)))
  }
  CurlPages(
    count = pages.size,
    current = current,
    pagesPerSpread = pagesPerSpread,
    onTurn = { first -> onPageTurn(pages[first], pages.progressAt(first, pagesPerSpread)) },
    backColor = MaterialTheme.colorScheme.surface,
    transition = state.settings.pageTransition,
    modifier = Modifier
      .fillMaxSize()
      // the pages turn themselves on a tap at the side and show the bars on a tap in the middle
      .pageGestures(
        shownPages = { latestCurrent until (latestCurrent + pagesPerSpread).coerceAtMost(pages.size) },
        onOtherTap = { _, _ -> false },
      ),
    onCenterTap = onCenterTap,
    turnRequests = turnRequests,
    previewTurn = previewTurn,
    spineWidth = spineWidth,
    spine = { Spine(spineWidth) },
  ) { index ->
    PageSlot(state, pages, index, textStyle, paragraphGap, margins, selection, Modifier.fillMaxSize())
  }
}

@Composable
private fun PageSlot(
  state: ReaderState.Loaded,
  pages: List<Page>,
  index: Int,
  textStyle: TextStyle,
  paragraphGap: TextUnit,
  margins: PageMargins,
  selection: PageSelection,
  modifier: Modifier = Modifier,
) {
  Box(modifier.background(MaterialTheme.colorScheme.surface)) {
    if (state.content is ReaderContent.Epub) {
      PaperTexture(
        intensity = state.settings.paperTexture,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.matchParentSize(),
      )
    }
    if (index < pages.size) {
      val footer = if (state.settings.progressVisibility == ProgressVisibility.Hidden) {
        null
      } else {
        footerText(
          state.settings.footer,
          state.content.readingProgress(pages = pages, index = index, pagesPerSpread = 1, pace = state.pace),
        )
      }
      BookPage(
        content = state.content,
        page = pages[index],
        pageNumber = index + 1,
        pageCount = pages.size,
        footer = footer,
        textStyle = textStyle,
        paragraphGap = paragraphGap,
        margins = margins,
        readingRange = state.readingRange.takeIf { pages[index].section == state.position.section },
        highlights = state.highlights,
        highlightOpacity = state.settings.highlightOpacity,
        searchMatch = state.searchMatch,
        selection = selection,
        imageFilter = state.settings.imageFilter.colorFilter(paper = MaterialTheme.colorScheme.surface),
      )
    }
  }
}

@Composable
private fun Spine(width: Dp) {
  val shade = MaterialTheme.colorScheme.outlineVariant
  Box(
    Modifier
      .width(width)
      .fillMaxHeight()
      .background(Brush.horizontalGradient(listOf(Color.Transparent, shade, Color.Transparent))),
  )
}

@Composable
private fun BookPage(
  content: ReaderContent,
  page: Page,
  pageNumber: Int,
  pageCount: Int,
  footer: String?,
  textStyle: TextStyle,
  paragraphGap: TextUnit,
  margins: PageMargins,
  readingRange: IntRange?,
  highlights: List<Highlight>,
  highlightOpacity: Float,
  searchMatch: BookRange?,
  selection: PageSelection,
  imageFilter: ColorFilter?,
) {
  val pageLabel = stringResource(StringsR.string.reader_page, pageNumber, pageCount)
  when (content) {
    is ReaderContent.Epub -> Column(
      Modifier
        .fillMaxSize()
        .padding(start = margins.side, end = margins.side, top = margins.top, bottom = margins.bottom),
    ) {
      val marks = pageMarks(page.section, readingRange, highlights, highlightOpacity, searchMatch, selection.range)
      val textStart = remember(content, page) {
        val chapter = content.chapters[page.section]
        var start = page.start
        while (start < page.end && chapter[start] == '\n') start++
        start
      }
      val text = remember(content, page, textStart, marks, paragraphGap) {
        val chapter = content.chapters[page.section]
        val shown = chapter.substring(textStart, page.end)
        val tail = pageTail(chapter, page.end)
        val continued = textStart > 0 && chapter[textStart - 1] != '\n'
        buildAnnotatedString {
          append(markedText(shown, textStart, marks))
          withStyle(SpanStyle(color = Color.Transparent)) { append(tail) }
          paragraphStyles(shown + tail, paragraphGap, continued).forEach { addStyle(it.item, it.start, it.end) }
        }
      }
      val pageText = remember { PageText() }
      SideEffect {
        pageText.section = page.section
        pageText.offset = textStart
        selection.register(pageNumber - 1, pageText)
      }
      Text(
        text = text,
        style = textStyle,
        overflow = TextOverflow.Clip,
        onTextLayout = { pageText.layout = it },
        modifier = Modifier
          .weight(1F)
          .fillMaxWidth()
          .onGloballyPositioned { pageText.coordinates = it },
      )
      Row(
        Modifier
          .height(PageNumberHeight)
          .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = content.chapterTitle(page.section),
          style = MaterialTheme.typography.labelMedium.bookText(),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(1F)
            .padding(end = 12.dp),
        )
        if (footer != null) {
          Text(
            text = footer,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    is ReaderContent.Pdf -> ZoomablePdfPage(
      pdf = content.pdf,
      index = page.section,
      contentDescription = pageLabel,
      colorFilter = imageFilter,
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp),
    )
  }
}

@Composable
internal fun ReaderContent.Epub.chapterTitle(section: Int): String {
  return chapterTitles.getOrNull(section) ?: stringResource(StringsR.string.reader_chapter, section + 1)
}
