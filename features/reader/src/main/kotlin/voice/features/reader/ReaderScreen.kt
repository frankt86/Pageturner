@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import kotlin.math.roundToInt
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
  LaunchedEffect(viewModel) {
    viewModel.messages.collect { message ->
      snackbarHostState.showSnackbar(
        when (message) {
          ReaderMessage.ReadAloudUnavailable -> unavailableMessage
          ReaderMessage.NothingToReadAloud -> nothingToReadMessage
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
  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      ReaderTopBar(
        title = loaded?.content?.title,
        readingAloud = loaded?.readingAloud,
        showTextOptions = loaded?.content is ReaderContent.Epub,
        onCloseClick = viewModel::onCloseClick,
        onReadAloudClick = viewModel::onReadAloudClick,
        onContentsClick = { sheet = ReaderSheet.Contents },
        onTextSizeClick = { sheet = ReaderSheet.TextSize },
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
      is ReaderState.Loaded -> Book(
        state = state,
        onPageTurn = viewModel::onPageTurned,
        modifier = modifier,
      )
    }
  }
  val content = loaded?.content as? ReaderContent.Epub
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
      ReaderSheet.TextSize -> TextSizeSheet(
        textSize = loaded.textSize,
        onTextSizeChange = viewModel::onTextSizeChange,
        onDismiss = { sheet = null },
      )
      null -> Unit
    }
  }
}

private enum class ReaderSheet {
  Contents,
  TextSize,
}

@Composable
private fun ReaderTopBar(
  title: String?,
  readingAloud: Boolean?,
  showTextOptions: Boolean,
  onCloseClick: () -> Unit,
  onReadAloudClick: () -> Unit,
  onContentsClick: () -> Unit,
  onTextSizeClick: () -> Unit,
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
private fun Book(
  state: ReaderState.Loaded,
  onPageTurn: (Page, Float) -> Unit,
  modifier: Modifier = Modifier,
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
): List<Page>? {
  return when (content) {
    is ReaderContent.Pdf -> remember(content) { List(content.sectionCount) { Page(section = it) } }
    is ReaderContent.Epub -> {
      val textMeasurer = rememberTextMeasurer(cacheSize = 0)
      val density = LocalDensity.current
      val width = with(density) { textWidth.roundToPx() }
      val height = with(density) { textHeight.roundToPx() }
      produceState<List<Page>?>(null, content, textStyle, width, height, textMeasurer) {
        value = withContext(Dispatchers.Default) {
          paginate(content.chapters, textMeasurer, textStyle, width, height)
        }
      }.value
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
) {
  if (pages.isEmpty()) return
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

  val scope = rememberCoroutineScope()
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
            else -> return@detectTapGestures
          }
          if (target in 0 until pagerState.pageCount) {
            scope.launch { pagerState.animateScrollToPage(target) }
          }
        }
      },
  ) { spread ->
    val surface = MaterialTheme.colorScheme.surface
    Row(
      Modifier
        .fillMaxSize()
        .graphicsLayer {
          // the pager slides pages, this holds them in place and turns the upper one like a paper page
          val offset = (pagerState.currentPage - spread) + pagerState.currentPageOffsetFraction
          translationX = offset * size.width
          if (offset < 0F) {
            transformOrigin = TransformOrigin(0F, 0.5F)
            rotationY = 90F * offset.coerceAtLeast(-1F)
            cameraDistance = 16F * density
          }
        }
        .background(surface),
    ) {
      repeat(pagesPerSpread) { slot ->
        val index = spread * pagesPerSpread + slot
        Box(
          Modifier
            .weight(1F)
            .fillMaxHeight(),
        ) {
          if (index < pages.size) {
            BookPage(
              content = state.content,
              page = pages[index],
              pageNumber = index + 1,
              pageCount = pages.size,
              textStyle = textStyle,
              readingRange = state.readingRange.takeIf { pages[index].section == state.position.section },
            )
          }
        }
        if (slot < pagesPerSpread - 1) {
          Spine()
        }
      }
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
) {
  val pageLabel = stringResource(StringsR.string.reader_page, pageNumber, pageCount)
  when (content) {
    is ReaderContent.Epub -> Column(
      Modifier
        .fillMaxSize()
        .padding(horizontal = PageHorizontalPadding, vertical = PageVerticalPadding),
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
private fun ReaderContent.Epub.chapterTitle(section: Int): String {
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
