package voice.features.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voice.core.data.EbookPosition
import voice.core.data.ProgressBarStyle
import voice.core.data.ProgressVisibility
import voice.core.ui.bookText
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

/** How much of the book is read once the spread holding [index] is. */
internal fun List<Page>.progressAt(
  index: Int,
  pagesPerSpread: Int,
): Float {
  val pagesSeen = ((index / pagesPerSpread + 1) * pagesPerSpread).coerceAtMost(size)
  return pagesSeen.toFloat() / size
}

/** The pages of the chapter that come after the spread holding [index]. */
internal fun List<Page>.pagesLeftInChapter(
  index: Int,
  pagesPerSpread: Int,
): Int {
  val lastShown = ((index / pagesPerSpread + 1) * pagesPerSpread - 1).coerceAtMost(lastIndex)
  val section = this[lastShown].section
  return subList(lastShown + 1, size).takeWhile { it.section == section }.size
}

/**
 * The bar to go anywhere in the book, shown while [barsVisible], and the volume keys turning the
 * pages. Reading aloud keeps the volume keys for the volume.
 */
@Composable
internal fun BoxScope.ReaderControls(
  state: ReaderState.Loaded,
  pages: List<Page>,
  pagesPerSpread: Int,
  barsVisible: Boolean,
  onTurn: (step: Int) -> Unit,
  onJump: (Page, Float) -> Unit,
) {
  if (pages.isEmpty()) return
  val index = pages.indexOfPosition(state.position)
  if (state.settings.volumeKeysTurnPages && !state.readingAloud) {
    VolumeKeyPageTurns(onTurn)
  }
  val chapters = remember(pages) { pages.chapterStarts().map { fractionOf(it, pages.size) } }
  if (state.settings.progressVisibility == ProgressVisibility.Always) {
    ProgressLine(
      fraction = pages.progressAt(index, pagesPerSpread),
      style = state.settings.progressBar,
      chapters = chapters,
      color = state.settings.theme.pageColors()?.ink ?: MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(horizontal = 16.dp, vertical = 4.dp),
    )
  }
  BottomBar(visible = barsVisible && state.settings.progressVisibility != ProgressVisibility.Hidden) {
    ReaderNavigationBar(
      state = state,
      pages = pages,
      currentIndex = index,
      pagesPerSpread = pagesPerSpread,
      chapters = chapters,
      onJump = { target -> onJump(pages[target], pages.progressAt(target, pagesPerSpread)) },
    )
  }
}

/** Shows [content] at the bottom of the book while [visible]. */
@Composable
internal fun BoxScope.BottomBar(
  visible: Boolean,
  content: @Composable () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn() + slideInVertically { it / 2 },
    exit = fadeOut() + slideOutVertically { it / 2 },
    modifier = Modifier
      .align(Alignment.BottomCenter)
      .padding(16.dp)
      .widthIn(max = 560.dp)
      .fillMaxWidth(),
  ) {
    content()
  }
}

@Composable
internal fun VolumeKeyPageTurns(onTurn: (step: Int) -> Unit) {
  val focusRequester = remember { FocusRequester() }
  val latestOnTurn by rememberUpdatedState(onTurn)
  Box(
    Modifier
      .onPreviewKeyEvent { event ->
        val step = when (event.key) {
          Key.VolumeDown -> 1
          Key.VolumeUp -> -1
          else -> return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown) latestOnTurn(step)
        // the key up too, or the phone shows its volume panel
        true
      }
      .focusRequester(focusRequester)
      .focusable(),
  )
  LaunchedEffect(focusRequester) {
    focusRequester.requestFocus()
  }
}

@Composable
private fun ReaderNavigationBar(
  state: ReaderState.Loaded,
  pages: List<Page>,
  currentIndex: Int,
  pagesPerSpread: Int,
  chapters: List<Float>,
  onJump: (Int) -> Unit,
) {
  val content = state.content
  val marks = if (state.settings.progressMarks) {
    remember(pages, chapters, state.bookmarks, state.highlights) {
      BarMarks(
        chapters = if (content is ReaderContent.Epub) chapters else emptyList(),
        bookmarks = state.bookmarks.map { fractionOf(pages.indexOfPosition(it), pages.size) },
        highlights = state.highlights.map { fractionOf(pages.indexOfPosition(EbookPosition(it.section, it.start)), pages.size) },
      )
    }
  } else {
    null
  }
  PageBar(
    pageCount = pages.size,
    currentIndex = currentIndex,
    chapterTitle = { index -> (content as? ReaderContent.Epub)?.chapterTitle(pages[index].section).orEmpty() },
    progress = { index -> content.readingProgress(pages, index, if (index == currentIndex) pagesPerSpread else 1, state.pace) },
    style = state.settings.progressBar,
    chapters = if (content is ReaderContent.Epub) chapters else emptyList(),
    marks = marks,
    onJump = onJump,
  )
}

/**
 * The chapter and page of [currentIndex], a slider to go to any page, and how far the reader is in the chapter and the
 * book.
 */
@Composable
internal fun PageBar(
  pageCount: Int,
  currentIndex: Int,
  chapterTitle: @Composable (index: Int) -> String,
  progress: (index: Int) -> ReadingProgress,
  style: ProgressBarStyle,
  chapters: List<Float>,
  marks: BarMarks?,
  onJump: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var dragTarget by remember { mutableStateOf<Int?>(null) }
  val shown = dragTarget ?: currentIndex
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shadowElevation = 6.dp,
  ) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = chapterTitle(shown),
          style = MaterialTheme.typography.labelLarge.bookText(),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(1F)
            .padding(end = 12.dp),
        )
        Text(
          text = stringResource(StringsR.string.reader_page, shown + 1, pageCount),
          style = MaterialTheme.typography.labelLarge,
        )
      }
      if (pageCount > 1) {
        // the range is part of the saved state, so a new layout of the pages starts a new slider
        key(pageCount) {
          val sliderState = rememberSliderState(
            value = currentIndex.toFloat(),
            trackRange = 0F..(pageCount - 1).toFloat(),
          )
          LaunchedEffect(currentIndex) {
            if (dragTarget == null) sliderState.value = currentIndex.toFloat()
          }
          val description = stringResource(StringsR.string.reader_go_to_page)
          val onValueChange = { value: Float ->
            sliderState.value = value
            dragTarget = value.roundToInt()
          }
          val onValueChangeFinished = {
            dragTarget?.let(onJump)
            dragTarget = null
          }
          Box {
            val sliderModifier = Modifier.semantics { contentDescription = description }
            if (style == ProgressBarStyle.Bar) {
              Slider(
                state = sliderState,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                modifier = sliderModifier,
              )
            } else {
              Slider(
                state = sliderState,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                modifier = sliderModifier,
                track = {
                  ProgressTrack(
                    fraction = fractionOf(shown, pageCount),
                    style = style,
                    chapters = chapters,
                  )
                },
              )
            }
            if (marks != null) {
              ProgressMarks(
                marks = marks,
                modifier = Modifier
                  .matchParentSize()
                  .padding(horizontal = 2.dp),
              )
            }
          }
        }
      }
      ProgressDetails(progress(shown))
    }
  }
}
