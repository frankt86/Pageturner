package voice.features.reader

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import voice.core.data.EbookPosition
import voice.core.data.Highlight
import voice.core.data.ProgressVisibility
import voice.core.data.ScrollBehavior
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

/** The paragraphs of the chapters, each one a [Page], for the scrolled book. */
internal fun paragraphs(chapters: List<String>): List<Page> = chapters.flatMapIndexed { section, text ->
  buildList {
    var start = 0
    while (true) {
      while (start < text.length && text[start] == '\n') start++
      if (start >= text.length) break
      val end = text.indexOf('\n', start).takeIf { it != -1 } ?: text.length
      if ((start until end).any { !text[it].isWhitespace() }) add(Page(section, start, end))
      start = end
    }
  }
}

/**
 * How much further a swipe that already moved the text by [dragged] goes, so it ends on a whole [step]: the next one
 * with a fling forward, the one before with a fling back, else the closer one.
 */
internal fun snapDistance(
  dragged: Float,
  step: Float,
  velocity: Float,
  minVelocity: Float,
): Float {
  if (step <= 0F) return 0F
  val steps = dragged / step
  val target = when {
    velocity > minVelocity -> ceil(steps)
    velocity < -minVelocity -> floor(steps)
    else -> round(steps)
  }
  return target * step - dragged
}

/** One screen of the list less a line, so the last line stays in sight. */
private fun screenStep(
  info: LazyListLayoutInfo,
  line: Float,
): Float {
  val height = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
  return (height - line).coerceAtLeast(line)
}

/**
 * The book as one long column, read from top to bottom: the paragraphs of an EPUB or the pages of a PDF. The place is
 * the first line on screen, or for a PDF the page in the middle of the screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScrollBook(
  state: ReaderState.Loaded,
  pages: List<Page>,
  textStyle: TextStyle,
  margins: PageMargins,
  selection: PageSelection,
  turnRequests: Flow<Int>,
  // in dp per second while it scrolls by itself, else null
  autoScrollSpeed: Int?,
  onScroll: (EbookPosition) -> Unit,
  // the finger moves the text
  onUserScroll: () -> Unit,
  onHighlightClick: (Highlight) -> Unit,
  onVisiblePagesChange: (List<Page>) -> Unit,
  // with where the tap was across the screen, from 0 to 1
  onTap: (Float) -> Unit,
  onAutoScrollEnd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val content = state.content
  val items = when (content) {
    is ReaderContent.Epub -> remember(content) { paragraphs(content.chapters) }
    is ReaderContent.Pdf -> pages
  }
  // the page that shows first tells the shape of the others until they're opened
  val firstAspectRatio = (content as? ReaderContent.Pdf)?.let { pdf ->
    produceState<Float?>(null, pdf) {
      val pageCount = pdf.pdf.pageCount
      if (pageCount > 0) value = pdf.pdf.aspectRatio(state.position.section.coerceIn(0, pageCount - 1))
    }.value
  }
  if (items.isEmpty()) return
  if (content is ReaderContent.Pdf && firstAspectRatio == null) {
    Box(modifier, contentAlignment = Alignment.Center) {
      LoadingIndicator(Modifier.size(64.dp))
    }
    return
  }
  val density = LocalDensity.current
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val latestState by rememberUpdatedState(state)
  val latestOnScroll by rememberUpdatedState(onScroll)
  val latestOnVisiblePagesChange by rememberUpdatedState(onVisiblePagesChange)
  val latestOnHighlightClick by rememberUpdatedState(onHighlightClick)
  val latestOnTap by rememberUpdatedState(onTap)
  val latestOnAutoScrollEnd by rememberUpdatedState(onAutoScrollEnd)
  val latestOnUserScroll by rememberUpdatedState(onUserScroll)
  val latestBehavior by rememberUpdatedState(state.settings.scrollBehavior)
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = items.indexOfPosition(state.position))
  // the lines of each paragraph, to tell the first line on screen
  val layouts = remember(items) { mutableMapOf<Int, TextLayoutResult>() }
  val line = with(density) { if (content is ReaderContent.Epub) textStyle.lineHeight.toPx() else 48.dp.toPx() }
  val latestLine by rememberUpdatedState(line)
  val step: (Int) -> Unit = { direction ->
    scope.launch {
      val distance = direction * screenStep(listState.layoutInfo, latestLine)
      if (latestBehavior == ScrollBehavior.Stepped) {
        val _ = listState.scrollBy(distance)
      } else {
        val _ = listState.animateScrollBy(distance)
      }
    }
  }
  LaunchedEffect(turnRequests) {
    turnRequests.collect { step(it) }
  }

  var lastReported by remember { mutableStateOf<EbookPosition?>(null) }
  LaunchedEffect(listState, items) {
    snapshotFlow { shownPosition(listState, items, layouts, pdf = content is ReaderContent.Pdf) }
      .distinctUntilChanged()
      .collectLatest { position ->
        // a scroll changes the place many times a second, it's kept once it stops
        delay(300)
        latestOnVisiblePagesChange(shownPages(listState, items, position, content))
        if (position != latestState.position) {
          lastReported = position
          latestOnScroll(position)
        }
      }
  }
  // jumps from the contents, a bookmark, a search result or the page bar, and the text read aloud
  LaunchedEffect(state.position, items) {
    val target = state.position
    if (target == lastReported) return@LaunchedEffect
    val index = items.indexOfPosition(target)
    if (latestState.readingAloud && listState.isFullyVisible(index)) return@LaunchedEffect
    listState.scrollToItem(index)
    val item = items[index]
    if (content !is ReaderContent.Epub || target.offset <= item.start) return@LaunchedEffect
    val layout = layouts[index] ?: run {
      withFrameNanos { }
      layouts[index]
    } ?: return@LaunchedEffect
    val lineIndex = layout.getLineForOffset((target.offset - item.start).coerceAtMost(layout.layoutInput.text.length))
    listState.scrollToItem(index, layout.getLineTop(lineIndex).roundToInt())
  }

  var fingerDown by remember { mutableStateOf(false) }
  val speed = autoScrollSpeed?.takeIf { !fingerDown && selection.range == null }
  LaunchedEffect(speed) {
    if (speed == null) return@LaunchedEffect
    val pixelsPerSecond = with(density) { speed.dp.toPx() }
    var carry = 0F
    var last = withFrameNanos { it }
    while (true) {
      val now = withFrameNanos { it }
      carry += pixelsPerSecond * (now - last) / 1_000_000_000F
      last = now
      // a swipe that's still moving the text goes first
      if (listState.isScrollInProgress) {
        carry = 0F
        continue
      }
      val whole = floor(carry)
      if (whole >= 1F) {
        carry -= whole
        val _ = listState.dispatchRawDelta(whole)
        if (!listState.canScrollForward) {
          latestOnAutoScrollEnd()
          return@LaunchedEffect
        }
      }
    }
  }

  val drag = remember { DragDistance() }
  val minVelocity = with(density) { 400.dp.toPx() }
  val snapFling = remember(listState, drag, minVelocity) {
    SnapFling(drag, minVelocity) { screenStep(listState.layoutInfo, latestLine) }
  }
  Column(modifier) {
    if (content is ReaderContent.Epub) {
      pageHeader(state, state.position.section)?.let { PageHeader(it, Modifier.padding(horizontal = margins.side)) }
    }
    Box(
      Modifier
        .weight(1F)
        .fillMaxWidth(),
    ) {
      if (content is ReaderContent.Epub) {
        PaperTexture(
          intensity = state.settings.paperTexture,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.matchParentSize(),
        )
      }
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = margins.top, bottom = margins.bottom),
        flingBehavior = if (state.settings.scrollBehavior == ScrollBehavior.Snap) snapFling else ScrollableDefaults.flingBehavior(),
        modifier = Modifier
          .fillMaxSize()
          .ignoreEdgeTouches(state.settings.edgeMargin.dp)
          .nestedScroll(drag)
          .pointerInput(Unit) {
            awaitPointerEventScope {
              var down: Offset? = null
              var moved = false
              while (true) {
                val finger = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed }
                fingerDown = finger != null
                if (finger == null) {
                  down = null
                  continue
                }
                val start = down ?: finger.position.also {
                  down = it
                  moved = false
                }
                if (!moved && (finger.position - start).getDistance() > viewConfiguration.touchSlop) {
                  moved = true
                  latestOnUserScroll()
                }
              }
            }
          }
          .selectableText(
            selection = selection,
            shownPages = {
              val visible = listState.layoutInfo.visibleItemsInfo
              if (visible.isEmpty()) IntRange.EMPTY else visible.first().index..visible.last().index
            },
            state = { latestState },
            onHighlightClick = { latestOnHighlightClick(it) },
            onSelect = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            onOtherTap = { offset, size ->
              latestOnTap(offset.x / size.width)
              true
            },
          ),
      ) {
        when (content) {
          is ReaderContent.Epub -> itemsIndexed(items) { index, item ->
            ParagraphText(
              content = content,
              item = item,
              index = index,
              state = state,
              textStyle = textStyle,
              gap = with(density) { (textStyle.lineHeight * (state.settings.paragraphSpacing ?: 1F)).toDp() },
              side = margins.side,
              selection = selection,
              onLayout = { layouts[index] = it },
            )
          }
          is ReaderContent.Pdf -> itemsIndexed(items) { index, item ->
            ScrolledPdfPage(
              pdf = content.pdf,
              index = item.section,
              colorFilter = state.settings.imageFilter.colorFilter(paper = MaterialTheme.colorScheme.surface),
              pageCount = items.size,
            )
          }
        }
      }
    }
    if (content is ReaderContent.Epub && state.settings.progressVisibility != ProgressVisibility.Hidden && pages.isNotEmpty()) {
      val index = pages.indexOfPosition(state.position)
      PageFooter(
        chapter = content.chapterTitle(state.position.section),
        footer = footerText(state.settings.footer, content.readingProgress(pages, index, pagesPerSpread = 1, pace = state.pace)),
        settings = state.settings,
        modifier = Modifier
          .height(PageNumberHeight)
          .padding(horizontal = margins.side),
      )
    }
  }
}

@Composable
private fun ParagraphText(
  content: ReaderContent.Epub,
  item: Page,
  index: Int,
  state: ReaderState.Loaded,
  textStyle: TextStyle,
  gap: Dp,
  side: Dp,
  selection: PageSelection,
  onLayout: (TextLayoutResult) -> Unit,
) {
  val marks = pageMarks(
    section = item.section,
    readingRange = state.readingRange.takeIf { item.section == state.position.section },
    highlights = state.highlights,
    highlightOpacity = state.settings.highlightOpacity,
    searchMatch = state.searchMatch,
    selection = selection.range,
  )
  val text = remember(content, item, marks) {
    markedText(content.chapters[item.section].substring(item.start, item.end), item.start, marks)
  }
  val pageText = remember { PageText() }
  SideEffect {
    pageText.section = item.section
    pageText.offset = item.start
    selection.register(index, pageText)
  }
  Text(
    text = text,
    style = textStyle,
    onTextLayout = {
      pageText.layout = it
      onLayout(it)
    },
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = side, end = side, bottom = gap)
      .onGloballyPositioned { pageText.coordinates = it },
  )
}

@Composable
private fun ScrolledPdfPage(
  pdf: PdfFile,
  index: Int,
  colorFilter: ColorFilter?,
  pageCount: Int,
) {
  val aspectRatio by produceState(pdf.knownAspectRatio(index), pdf, index) { value = pdf.aspectRatio(index) }
  ZoomablePdfPage(
    pdf = pdf,
    index = index,
    contentDescription = stringResource(StringsR.string.reader_page, index + 1, pageCount),
    colorFilter = colorFilter,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .aspectRatio(aspectRatio.coerceIn(0.1F, 10F))
      .clipToBounds()
      .background(MaterialTheme.colorScheme.surface),
  )
}

private fun shownPosition(
  listState: LazyListState,
  items: List<Page>,
  layouts: Map<Int, TextLayoutResult>,
  pdf: Boolean,
): EbookPosition {
  if (pdf) {
    val info = listState.layoutInfo
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val index = info.visibleItemsInfo.lastOrNull { it.offset <= middle }?.index ?: listState.firstVisibleItemIndex
    return EbookPosition(items[index.coerceIn(items.indices)].section, 0)
  }
  val index = listState.firstVisibleItemIndex.coerceIn(items.indices)
  val item = items[index]
  val offset = layouts[index]?.let { layout ->
    layout.getLineStart(layout.getLineForVerticalPosition(listState.firstVisibleItemScrollOffset.toFloat()))
  } ?: 0
  return EbookPosition(item.section, item.start + offset)
}

/** What's on screen from [position] on, as one page of its chapter, for the bookmark button. */
private fun shownPages(
  listState: LazyListState,
  items: List<Page>,
  position: EbookPosition,
  content: ReaderContent,
): List<Page> {
  if (content !is ReaderContent.Epub) return listOf(Page(position.section))
  val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { items.getOrNull(it.index) }
  val end = if (last != null && last.section == position.section) last.end else content.chapters[position.section].length
  return listOf(Page(position.section, position.offset, end.coerceAtLeast(position.offset + 1)))
}

private fun LazyListState.isFullyVisible(index: Int): Boolean {
  val info = layoutInfo
  val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
  return item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
}

/** How far the finger moved the text since the last fling, forward being positive. */
private class DragDistance : NestedScrollConnection {

  private var distance = 0F

  override fun onPostScroll(
    consumed: Offset,
    available: Offset,
    source: NestedScrollSource,
  ): Offset {
    if (source == NestedScrollSource.UserInput) distance -= consumed.y
    return Offset.Zero
  }

  fun take(): Float = distance.also { distance = 0F }
}

/** Ends each swipe on a whole screen, so the text moves a page at a time. */
private class SnapFling(
  private val drag: DragDistance,
  private val minVelocity: Float,
  private val step: () -> Float,
) : FlingBehavior {

  override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
    val distance = snapDistance(drag.take(), step(), initialVelocity, minVelocity)
    var moved = 0F
    animate(0F, distance) { value, _ -> moved += scrollBy(value - moved) }
    return 0F
  }
}
