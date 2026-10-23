@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import voice.core.common.rootGraphAs
import voice.core.data.Highlight
import voice.core.data.HighlightColor
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
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
  ReaderWindow(immersive = !barsVisible && loaded != null, brightness = loaded?.settings?.brightness)
  val turnPage = { page: Page, progress: Float ->
    barsVisible = false
    viewModel.onPageTurned(page, progress)
  }
  Box(
    Modifier
      .fillMaxSize()
      .background(loaded?.settings?.theme?.pageColors()?.paper ?: MaterialTheme.colorScheme.surface),
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
      is ReaderState.Loaded -> Book(
        state = state,
        onPageTurn = turnPage,
        selection = selection,
        onHighlightClick = { editedHighlight = it },
        onVisiblePagesChange = { visiblePages = it },
        onCenterTap = { barsVisible = !barsVisible },
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
    val selected = selection.range
    val epub = loaded?.content as? ReaderContent.Epub
    if (selected != null && epub != null) {
      SelectionToolbar(
        selectedText = epub.text(selected),
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
      ReaderTopBar(
        title = loaded?.content?.title,
        readingAloud = loaded?.readingAloud,
        bookmarked = loaded?.bookmarks?.any { bookmark -> visiblePages.any { it.contains(bookmark) } },
        showSearch = loaded?.content?.searchable == true,
        onCloseClick = viewModel::onCloseClick,
        onReadAloudClick = {
          if (loaded?.readingAloud == true) viewModel.stopReadingAloudClick() else sheet = ReaderSheet.ReadAloud
        },
        onContentsClick = { sheet = ReaderSheet.Contents },
        onDisplayClick = { sheet = ReaderSheet.Display },
        onBookmarkClick = { viewModel.onBookmarkClick(visiblePages) },
        onSearchClick = { sheet = ReaderSheet.Search },
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
      onSettingsChange = viewModel::onSettingsChange,
      onDismiss = { sheet = null },
    )
  }
  if (loaded != null && sheet == ReaderSheet.ReadAloud) {
    ReadAloudSheet(
      voice = loaded.voice,
      onVoiceSelect = viewModel::onVoiceSelect,
      onSpeechRateChange = viewModel::onSpeechRateChange,
      onStartClick = {
        sheet = null
        viewModel.onStartReadingClick()
      },
      onDismiss = { sheet = null },
    )
  }
  if (loaded != null) {
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
      ReaderSheet.Display, ReaderSheet.ReadAloud, null -> Unit
    }
  }
  val highlight = editedHighlight
  if (highlight != null && content != null) {
    HighlightSheet(
      highlight = highlight,
      text = content.text(BookRange(highlight.section, highlight.start, highlight.end)),
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

private enum class ReaderSheet {
  Contents,
  Display,
  ReadAloud,
  Search,
}

@Composable
internal fun ReaderTopBar(
  title: String?,
  readingAloud: Boolean?,
  // null while the book loads
  bookmarked: Boolean?,
  showSearch: Boolean,
  onCloseClick: () -> Unit,
  onReadAloudClick: () -> Unit,
  onContentsClick: () -> Unit,
  onDisplayClick: () -> Unit,
  onBookmarkClick: () -> Unit,
  onSearchClick: () -> Unit,
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
        IconButton(onClick = onContentsClick) {
          Icon(
            imageVector = VoiceIcons.ViewList,
            contentDescription = stringResource(StringsR.string.reader_contents),
          )
        }
      }
      if (title != null) {
        IconButton(onClick = onDisplayClick) {
          Icon(
            imageVector = VoiceIcons.Title,
            contentDescription = stringResource(StringsR.string.reader_display),
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

private val PageVerticalPadding = 8.dp
private val PageNumberHeight = 32.dp
private val SpineWidth = 24.dp

@Composable
internal fun Book(
  state: ReaderState.Loaded,
  onPageTurn: (Page, Float) -> Unit,
  selection: PageSelection,
  onHighlightClick: (Highlight) -> Unit,
  onVisiblePagesChange: (List<Page>) -> Unit,
  modifier: Modifier = Modifier,
  onCenterTap: () -> Unit = {},
  // previews can't wait for the pages laid out in the background
  paginateNow: Boolean = false,
  // previews can't swipe, this shows the first page turned this far
  previewTurn: Float? = null,
  // turn is called with 1 for the next page and -1 for the previous one
  overlay: @Composable BoxScope.(pages: List<Page>, pagesPerSpread: Int, turn: (Int) -> Unit) -> Unit = { _, _, _ -> },
) {
  BoxWithConstraints(modifier) {
    // an unfolded foldable, a tablet or a phone on its side has room for a spread like an open book
    val twoPages = maxWidth >= 600.dp && maxWidth >= maxHeight * 0.8F
    val pageWidth = if (twoPages) (maxWidth - SpineWidth) / 2 else maxWidth
    val textStyle = state.settings.pageTextStyle()
    val pages = pages(
      content = state.content,
      textStyle = textStyle,
      textWidth = pageWidth - state.settings.margin.dp * 2,
      textHeight = maxHeight - PageVerticalPadding * 2 - PageNumberHeight,
      paginateNow = paginateNow,
    )
    if (pages == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(64.dp))
      }
      return@BoxWithConstraints
    }
    val pagesPerSpread = if (twoPages) 2 else 1
    val turnRequests = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    ReaderPageTheme(state.settings.theme) {
      key(pages, pagesPerSpread) {
        Spreads(
          state = state,
          pages = pages,
          pagesPerSpread = pagesPerSpread,
          textStyle = textStyle,
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
    overlay(pages, pagesPerSpread) { step -> turnRequests.tryEmit(step) }
  }
}

@Composable
private fun pages(
  content: ReaderContent,
  textStyle: TextStyle,
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
        remember(content, textStyle, width, height) {
          paginate(content.chapters, textMeasurer, textStyle, width, height)
        }
      } else {
        produceState<List<Page>?>(null, content, textStyle, width, height, textMeasurer) {
          value = withContext(Dispatchers.Default) {
            paginate(content.chapters, textMeasurer, textStyle, width, height)
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
    spineWidth = SpineWidth,
    spine = { Spine() },
  ) { index ->
    PageSlot(state, pages, index, textStyle, selection, Modifier.fillMaxSize())
  }
}

@Composable
private fun PageSlot(
  state: ReaderState.Loaded,
  pages: List<Page>,
  index: Int,
  textStyle: TextStyle,
  selection: PageSelection,
  modifier: Modifier = Modifier,
) {
  Box(modifier.background(MaterialTheme.colorScheme.surface)) {
    if (index < pages.size) {
      BookPage(
        content = state.content,
        page = pages[index],
        pageNumber = index + 1,
        pageCount = pages.size,
        textStyle = textStyle,
        margin = state.settings.margin.dp,
        readingRange = state.readingRange.takeIf { pages[index].section == state.position.section },
        highlights = state.highlights,
        searchMatch = state.searchMatch,
        selection = selection,
      )
    }
  }
}

@Composable
private fun Spine() {
  val shade = MaterialTheme.colorScheme.outlineVariant
  Box(
    Modifier
      .width(SpineWidth)
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
  textStyle: TextStyle,
  margin: Dp,
  readingRange: IntRange?,
  highlights: List<Highlight>,
  searchMatch: BookRange?,
  selection: PageSelection,
) {
  val pageLabel = stringResource(StringsR.string.reader_page, pageNumber, pageCount)
  when (content) {
    is ReaderContent.Epub -> Column(
      Modifier
        .fillMaxSize()
        .padding(horizontal = margin, vertical = PageVerticalPadding),
    ) {
      val marks = pageMarks(page.section, readingRange, highlights, searchMatch, selection.range)
      val textStart = remember(content, page) {
        val chapter = content.chapters[page.section]
        var start = page.start
        while (start < page.end && chapter[start] == '\n') start++
        start
      }
      val text = remember(content, page, textStart, marks) {
        markedText(content.chapters[page.section].substring(textStart, page.end), textStart, marks)
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
        Text(
          text = pageLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    is ReaderContent.Pdf -> BoxWithConstraints(
      Modifier
        .fillMaxSize()
        .padding(8.dp),
      contentAlignment = Alignment.Center,
    ) {
      val bitmap by produceState<ImageBitmap?>(null, content, page.section, constraints.maxWidth, constraints.maxHeight) {
        value = content.pdf.render(page.section, constraints.maxWidth, constraints.maxHeight)?.asImageBitmap()
      }
      bitmap?.let {
        Image(
          bitmap = it,
          contentDescription = pageLabel,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}

@Composable
internal fun ReaderContent.Epub.chapterTitle(section: Int): String {
  return chapterTitles.getOrNull(section) ?: stringResource(StringsR.string.reader_chapter, section + 1)
}
