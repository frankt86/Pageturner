@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.rootGraphAs
import voice.core.data.Highlight
import voice.core.data.HighlightColor
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
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
  val selection = remember { PageSelection() }
  var visiblePages by remember { mutableStateOf(emptyList<Page>()) }
  var editedHighlight by remember { mutableStateOf<Highlight?>(null) }
  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      ReaderTopBar(
        title = loaded?.content?.title,
        readingAloud = loaded?.readingAloud,
        showTextOptions = loaded?.content is ReaderContent.Epub,
        bookmarked = loaded?.bookmarks?.any { bookmark -> visiblePages.any { it.contains(bookmark) } },
        showSearch = loaded?.content?.searchable == true,
        onCloseClick = viewModel::onCloseClick,
        onReadAloudClick = {
          if (loaded?.readingAloud == true) viewModel.stopReadingAloudClick() else sheet = ReaderSheet.ReadAloud
        },
        onContentsClick = { sheet = ReaderSheet.Contents },
        onTextSizeClick = { sheet = ReaderSheet.TextSize },
        onBookmarkClick = { viewModel.onBookmarkClick(visiblePages) },
        onSearchClick = { sheet = ReaderSheet.Search },
      )
    },
  ) { contentPadding ->
    val modifier = Modifier
      .fillMaxSize()
      .padding(contentPadding)
    when (state) {
      ReaderState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(64.dp))
      }
      ReaderState.Failed -> Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
          text = stringResource(StringsR.string.reader_failed),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
        )
      }
      is ReaderState.Loaded -> Box(modifier) {
        Book(
          state = state,
          onPageTurn = viewModel::onPageTurned,
          selection = selection,
          onHighlightClick = { editedHighlight = it },
          onVisiblePagesChange = { visiblePages = it },
          modifier = Modifier.fillMaxSize(),
        )
        val selected = selection.range
        if (selected != null && state.content is ReaderContent.Epub) {
          SelectionToolbar(
            selectedText = state.content.text(selected),
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
              .padding(16.dp),
          )
        }
      }
    }
  }
  val content = loaded?.content as? ReaderContent.Epub
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
  if (loaded != null && content != null) {
    when (sheet) {
      ReaderSheet.TextSize -> TextSizeSheet(
        textSize = loaded.textSize,
        onTextSizeChange = viewModel::onTextSizeChange,
        onDismiss = { sheet = null },
      )
      else -> Unit
    }
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
      else -> Unit
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
  TextSize,
  ReadAloud,
  Search,
}

@Composable
internal fun ReaderTopBar(
  title: String?,
  readingAloud: Boolean?,
  showTextOptions: Boolean,
  // null while the book loads
  bookmarked: Boolean?,
  showSearch: Boolean,
  onCloseClick: () -> Unit,
  onReadAloudClick: () -> Unit,
  onContentsClick: () -> Unit,
  onTextSizeClick: () -> Unit,
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
      if (showTextOptions) {
        IconButton(onClick = onTextSizeClick) {
          Icon(
            imageVector = VoiceIcons.Title,
            contentDescription = stringResource(StringsR.string.reader_text_size),
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

private val PageHorizontalPadding = 24.dp
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
  // previews can't wait for the pages laid out in the background
  paginateNow: Boolean = false,
  // previews can't swipe, this shows the first page turned this far
  previewTurn: Float? = null,
) {
  BoxWithConstraints(modifier) {
    // an unfolded foldable, a tablet or a phone on its side has room for a spread like an open book
    val twoPages = maxWidth >= 600.dp && maxWidth >= maxHeight * 0.8F
    val pageWidth = if (twoPages) (maxWidth - SpineWidth) / 2 else maxWidth
    val textStyle = MaterialTheme.typography.bodyLarge.bookText().copy(
      fontSize = state.textSize.sp,
      lineHeight = (state.textSize * 1.55F).sp,
      // the same breaks on screen as when the pages were measured, so no line spills over
      lineBreak = LineBreak.Simple,
      hyphens = Hyphens.None,
    )
    val pages = pages(
      content = state.content,
      textStyle = textStyle,
      textWidth = pageWidth - PageHorizontalPadding * 2,
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
        previewTurn = previewTurn,
      )
    }
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

  if (pagesPerSpread == 1) {
    val current = pages.indexOfPosition(state.position)
    val latestCurrent by rememberUpdatedState(current)
    LaunchedEffect(current) {
      selection.clear()
      latestOnVisiblePagesChange(listOf(pages[current]))
    }
    CurlPages(
      count = pages.size,
      current = current,
      onTurn = { index -> onPageTurn(pages[index], (index + 1F) / pages.size) },
      backColor = MaterialTheme.colorScheme.surface,
      modifier = Modifier
        .fillMaxSize()
        // the pages turn themselves on a tap at the side
        .pageGestures(shownPages = { latestCurrent..latestCurrent }, onOtherTap = { _, _ -> false }),
      previewTurn = previewTurn,
    ) { index ->
      PageSlot(state, pages, index, textStyle, selection, Modifier.fillMaxSize())
    }
    return
  }
  val currentSpread = pages.indexOfPosition(state.position) / pagesPerSpread
  val spreadCount = (pages.size + pagesPerSpread - 1) / pagesPerSpread
  val pagerState = rememberPagerState(initialPage = currentSpread) { spreadCount }

  // follows reading aloud from page to page
  LaunchedEffect(currentSpread) {
    if (pagerState.currentPage != currentSpread) {
      pagerState.animateScrollToPage(currentSpread)
    }
  }
  val latestSpread by rememberUpdatedState(currentSpread)
  val latestOnPageTurn by rememberUpdatedState(onPageTurn)
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }.collect { spread ->
      if (spread != latestSpread) {
        val pagesSeen = ((spread + 1) * pagesPerSpread).coerceAtMost(pages.size)
        latestOnPageTurn(pages[spread * pagesPerSpread], pagesSeen.toFloat() / pages.size)
      }
    }
  }
  LaunchedEffect(currentSpread) {
    selection.clear()
    val first = currentSpread * pagesPerSpread
    latestOnVisiblePagesChange(pages.subList(first, (first + pagesPerSpread).coerceAtMost(pages.size)))
  }

  val turning by remember {
    derivedStateOf {
      floor(pagerState.currentPage + pagerState.currentPageOffsetFraction).toInt().coerceIn(0, spreadCount - 1)
    }
  }
  val progress = {
    previewTurn ?: (pagerState.currentPage + pagerState.currentPageOffsetFraction - turning).coerceIn(0F, 1F)
  }
  val scope = rememberCoroutineScope()
  Box(Modifier.fillMaxSize()) {
    SpreadTurn(state, pages, turning, progress, textStyle, selection)
    // the pager only follows the finger, the pages are drawn above and turn like paper
    HorizontalPager(
      state = pagerState,
      userScrollEnabled = selection.range == null,
      modifier = Modifier
        .fillMaxSize()
        .pageGestures(
          shownPages = {
            val first = pagerState.currentPage * pagesPerSpread
            first until (first + pagesPerSpread).coerceAtMost(pages.size)
          },
          onOtherTap = { offset, size ->
            // like turning a paper page, a tap near the edge turns it
            val edge = size.width / 4
            val target = when {
              offset.x < edge -> pagerState.currentPage - 1
              offset.x > size.width - edge -> pagerState.currentPage + 1
              else -> null
            }
            if (target != null && target in 0 until pagerState.pageCount) {
              scope.launch { pagerState.animateScrollToPage(target) }
            }
            target != null
          },
        ),
    ) {
      Box(Modifier.fillMaxSize())
    }
  }
}

// the right page turns around the spine like a leaf of a book, its back is the left page of the next spread
@Composable
private fun SpreadTurn(
  state: ReaderState.Loaded,
  pages: List<Page>,
  turning: Int,
  progress: () -> Float,
  textStyle: TextStyle,
  selection: PageSelection,
) {
  val first = turning * 2
  Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize()) {
      PageSlot(
        state = state,
        pages = pages,
        index = first,
        textStyle = textStyle,
        selection = selection,
        modifier = Modifier
          .weight(1F)
          .fillMaxHeight()
          .shade { 0.2F * progress() },
      )
      Spine()
      PageSlot(
        state = state,
        pages = pages,
        index = first + 3,
        textStyle = textStyle,
        selection = selection,
        modifier = Modifier
          .weight(1F)
          .fillMaxHeight()
          .shade { progress().let { if (it > 0F) 0.2F * (1F - it) else 0F } },
      )
    }
    Row(Modifier.fillMaxSize()) {
      PageSlot(
        state = state,
        pages = pages,
        index = first + 2,
        textStyle = textStyle,
        selection = selection,
        modifier = Modifier
          .weight(1F)
          .fillMaxHeight()
          .graphicsLayer {
            val turned = progress()
            alpha = if (turned > 0.5F) 1F else 0F
            transformOrigin = TransformOrigin(1F, 0.5F)
            rotationY = -180F * (1F - turned)
            cameraDistance = 32F * density
          }
          .shade { 0.3F * sin(PI.toFloat() * progress()) },
      )
      Spacer(Modifier.width(SpineWidth))
      PageSlot(
        state = state,
        pages = pages,
        index = first + 1,
        textStyle = textStyle,
        selection = selection,
        modifier = Modifier
          .weight(1F)
          .fillMaxHeight()
          .graphicsLayer {
            val turned = progress()
            alpha = if (turned <= 0.5F) 1F else 0F
            transformOrigin = TransformOrigin(0F, 0.5F)
            rotationY = 180F * turned
            cameraDistance = 32F * density
          }
          .shade { 0.3F * sin(PI.toFloat() * progress()) },
      )
    }
  }
}

private fun Modifier.shade(alpha: () -> Float): Modifier = drawWithContent {
  drawContent()
  val value = alpha()
  if (value > 0F) drawRect(Color.Black.copy(alpha = value))
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
        .padding(horizontal = PageHorizontalPadding, vertical = PageVerticalPadding),
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

@Composable
private fun TextSizeSheet(
  textSize: Float,
  onTextSizeChange: (Float) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
      Text(
        text = stringResource(StringsR.string.reader_text_size),
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        text = "Aa",
        style = MaterialTheme.typography.bodyLarge.bookText().copy(fontSize = textSize.sp),
        modifier = Modifier.padding(vertical = 16.dp),
      )
      val sliderState = rememberSliderState(value = textSize, trackRange = 14F..30F)
      Slider(
        state = sliderState,
        onValueChange = {
          sliderState.value = it
          onTextSizeChange(it.roundToInt().toFloat())
        },
      )
    }
  }
}
