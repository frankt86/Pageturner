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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
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
  // the bars show when the book opens and get out of the way once reading starts
  var barsVisible by rememberSaveable { mutableStateOf(true) }
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
        onCenterTap = { barsVisible = !barsVisible },
        // the insets of hidden bars too, or showing them would lay out all pages anew
        modifier = Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)),
        overlay = { pages, pagesPerSpread ->
          ReaderControls(
            state = state,
            pages = pages,
            pagesPerSpread = pagesPerSpread,
            barsVisible = barsVisible,
            onPageTurn = turnPage,
            onJump = viewModel::onPageTurned,
          )
        },
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
        showTextOptions = loaded?.content is ReaderContent.Epub,
        onCloseClick = viewModel::onCloseClick,
        onReadAloudClick = {
          if (loaded?.readingAloud == true) viewModel.stopReadingAloudClick() else sheet = ReaderSheet.ReadAloud
        },
        onContentsClick = { sheet = ReaderSheet.Contents },
        onDisplayClick = { sheet = ReaderSheet.Display },
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
  if (loaded != null && content != null) {
    when (sheet) {
      ReaderSheet.Contents -> ContentsSheet(
        content = content,
        currentSection = loaded.position.section,
        onChapterClick = { section ->
          sheet = null
          viewModel.onChapterClick(section)
        },
        onDismiss = { sheet = null },
      )
      ReaderSheet.Display, ReaderSheet.ReadAloud, null -> Unit
    }
  }
}

private enum class ReaderSheet {
  Contents,
  Display,
  ReadAloud,
}

@Composable
internal fun ReaderTopBar(
  title: String?,
  readingAloud: Boolean?,
  showTextOptions: Boolean,
  onCloseClick: () -> Unit,
  onReadAloudClick: () -> Unit,
  onContentsClick: () -> Unit,
  onDisplayClick: () -> Unit,
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
      if (showTextOptions) {
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
  modifier: Modifier = Modifier,
  onCenterTap: () -> Unit = {},
  // previews can't wait for the pages laid out in the background
  paginateNow: Boolean = false,
  // previews can't swipe, this shows the first page turned this far
  previewTurn: Float? = null,
  overlay: @Composable BoxScope.(pages: List<Page>, pagesPerSpread: Int) -> Unit = { _, _ -> },
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
    ReaderPageTheme(state.settings.theme) {
      key(pages, pagesPerSpread) {
        Spreads(
          state = state,
          pages = pages,
          pagesPerSpread = pagesPerSpread,
          textStyle = textStyle,
          onPageTurn = onPageTurn,
          onCenterTap = onCenterTap,
          previewTurn = previewTurn,
        )
      }
    }
    overlay(pages, pagesPerSpread)
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
  onCenterTap: () -> Unit,
  previewTurn: Float?,
) {
  if (pages.isEmpty()) return
  if (pagesPerSpread == 1) {
    val current = pages.indexOfPosition(state.position)
    CurlPages(
      count = pages.size,
      current = current,
      onTurn = { index -> onPageTurn(pages[index], (index + 1F) / pages.size) },
      backColor = MaterialTheme.colorScheme.surface,
      modifier = Modifier.fillMaxSize(),
      onCenterTap = onCenterTap,
      previewTurn = previewTurn,
    ) { index ->
      PageSlot(state, pages, index, textStyle, Modifier.fillMaxSize())
    }
    return
  }
  val currentSpread = pages.indexOfPosition(state.position) / pagesPerSpread
  val spreadCount = (pages.size + pagesPerSpread - 1) / pagesPerSpread
  val pagerState = rememberPagerState(initialPage = currentSpread) { spreadCount }

  // follows reading aloud and the volume keys from page to page, and jumps further away
  LaunchedEffect(currentSpread) {
    when (abs(pagerState.currentPage - currentSpread)) {
      0 -> Unit
      1 -> pagerState.animateScrollToPage(currentSpread)
      else -> pagerState.scrollToPage(currentSpread)
    }
  }
  val latestSpread by rememberUpdatedState(currentSpread)
  val latestOnPageTurn by rememberUpdatedState(onPageTurn)
  val latestOnCenterTap by rememberUpdatedState(onCenterTap)
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }.collect { spread ->
      if (spread != latestSpread) {
        val first = spread * pagesPerSpread
        latestOnPageTurn(pages[first], pages.progressAt(first, pagesPerSpread))
      }
    }
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
    SpreadTurn(state, pages, turning, progress, textStyle)
    // the pager only follows the finger, the pages are drawn above and turn like paper
    HorizontalPager(
      state = pagerState,
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(pagerState) {
          // like turning a paper page, a tap near the edge turns it
          detectTapGestures { offset ->
            val edge = size.width / 4
            val target = when {
              offset.x < edge -> pagerState.currentPage - 1
              offset.x > size.width - edge -> pagerState.currentPage + 1
              else -> {
                latestOnCenterTap()
                return@detectTapGestures
              }
            }
            if (target in 0 until pagerState.pageCount) {
              scope.launch { pagerState.animateScrollToPage(target) }
            }
          }
        },
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
) {
  val first = turning * 2
  Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize()) {
      PageSlot(
        state = state,
        pages = pages,
        index = first,
        textStyle = textStyle,
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
) {
  val pageLabel = stringResource(StringsR.string.reader_page, pageNumber, pageCount)
  when (content) {
    is ReaderContent.Epub -> Column(
      Modifier
        .fillMaxSize()
        .padding(horizontal = margin, vertical = PageVerticalPadding),
    ) {
      val highlight = MaterialTheme.colorScheme.primaryContainer
      val text = remember(content, page, readingRange, highlight) {
        val raw = content.chapters[page.section].substring(page.start, page.end)
        val trimmed = raw.trimStart('\n')
        val shift = page.start + raw.length - trimmed.length
        buildAnnotatedString {
          append(trimmed)
          if (readingRange != null) {
            val start = (readingRange.first - shift).coerceAtLeast(0)
            val end = (readingRange.last + 1 - shift).coerceAtMost(trimmed.length)
            if (start < end) addStyle(SpanStyle(background = highlight), start, end)
          }
        }
      }
      Text(
        text = text,
        style = textStyle,
        overflow = TextOverflow.Clip,
        modifier = Modifier
          .weight(1F)
          .fillMaxWidth(),
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
private fun ContentsSheet(
  content: ReaderContent.Epub,
  currentSection: Int,
  onChapterClick: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Text(
      text = stringResource(StringsR.string.reader_contents),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentSection)
    LazyColumn(state = listState) {
      items(content.sectionCount) { section ->
        val current = section == currentSection
        Text(
          text = content.chapterTitle(section),
          style = MaterialTheme.typography.bodyLarge.bookText(),
          fontWeight = if (current) FontWeight.Bold else null,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onChapterClick(section) }
            .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        )
      }
    }
  }
}
