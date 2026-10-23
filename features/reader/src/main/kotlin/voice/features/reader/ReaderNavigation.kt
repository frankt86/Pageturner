package voice.features.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voice.core.data.EbookPosition
import voice.core.data.ProgressBarStyle
import voice.core.data.ProgressVisibility
import voice.core.data.ReaderSettings
import voice.core.ui.bookText
import kotlin.math.abs
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

/** The bar to go anywhere in the book, shown while [barsVisible]. */
@Composable
internal fun BoxScope.ReaderControls(
  state: ReaderState.Loaded,
  pages: List<Page>,
  pagesPerSpread: Int,
  barsVisible: Boolean,
  onJump: (Page, Float) -> Unit,
) {
  if (pages.isEmpty()) return
  val index = pages.indexOfPosition(state.position)
  val chapters = remember(pages) { pages.chapterStarts().map { fractionOf(it, pages.size) } }
  if (state.settings.progressVisibility == ProgressVisibility.Always) {
    ProgressLine(
      fraction = pages.progressAt(index, pagesPerSpread),
      style = state.settings.progressBar,
      chapters = chapters,
      color = state.settings.pageColors()?.ink ?: MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(horizontal = 16.dp, vertical = 4.dp),
    )
  }
  BottomBar(
    visible = barsVisible && state.settings.progressVisibility != ProgressVisibility.Hidden,
    settings = state.settings,
  ) {
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

/**
 * Shows [content] at the bottom of the book while [visible], at the size of the reader's controls, and above the buttons
 * when they're at the bottom.
 */
@Composable
internal fun BoxScope.BottomBar(
  visible: Boolean,
  settings: ReaderSettings,
  content: @Composable () -> Unit,
) {
  val alignment = Modifier.align(Alignment.BottomCenter)
  ScaledControls(settings.interfaceScale) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn() + slideInVertically { it / 2 },
      exit = fadeOut() + slideOutVertically { it / 2 },
      modifier = alignment
        .padding(16.dp)
        .padding(bottom = if (settings.oneHanded) BottomActionsHeight else 0.dp)
        .widthIn(max = 560.dp)
        .fillMaxWidth(),
    ) {
      content()
    }
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
    confirmJumpPages = state.settings.confirmJumpPages,
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
  // a jump over more pages asks first, null never asks
  confirmJumpPages: Int?,
  onJump: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  var dragTarget by remember { mutableStateOf<Int?>(null) }
  var askedJump by remember { mutableStateOf<Int?>(null) }
  val shown = dragTarget ?: askedJump ?: currentIndex
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
            dragTarget?.let { target ->
              if (confirmJumpPages != null && abs(target - currentIndex) > confirmJumpPages) {
                askedJump = target
              } else {
                onJump(target)
              }
            }
            dragTarget = null
          }
          askedJump?.let { target ->
            JumpDialog(
              page = target + 1,
              onConfirm = {
                askedJump = null
                onJump(target)
              },
              onDismiss = {
                askedJump = null
                sliderState.value = currentIndex.toFloat()
              },
            )
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

@Composable
private fun JumpDialog(
  page: Int,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(StringsR.string.reader_jump_title, page)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(stringResource(StringsR.string.reader_jump))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(StringsR.string.common_dialog_cancel))
      }
    },
  )
}
