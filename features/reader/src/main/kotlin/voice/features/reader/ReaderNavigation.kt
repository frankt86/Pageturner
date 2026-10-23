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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import voice.core.ui.bookText
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

/** The first page of the spread [step] spreads away from the one holding [index], or null before the first or after the last. */
internal fun List<Page>.spreadStart(
  index: Int,
  pagesPerSpread: Int,
  step: Int,
): Int? {
  val target = (index / pagesPerSpread + step) * pagesPerSpread
  return target.takeIf { it in indices }
}

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
  onPageTurn: (Page, Float) -> Unit,
  onJump: (Page, Float) -> Unit,
) {
  if (pages.isEmpty()) return
  val index = pages.indexOfPosition(state.position)
  if (state.settings.volumeKeysTurnPages && !state.readingAloud) {
    VolumeKeyPageTurns { step ->
      pages.spreadStart(index, pagesPerSpread, step)?.let { target ->
        onPageTurn(pages[target], pages.progressAt(target, pagesPerSpread))
      }
    }
  }
  AnimatedVisibility(
    visible = barsVisible,
    enter = fadeIn() + slideInVertically { it / 2 },
    exit = fadeOut() + slideOutVertically { it / 2 },
    modifier = Modifier
      .align(Alignment.BottomCenter)
      .padding(16.dp)
      .widthIn(max = 560.dp)
      .fillMaxWidth(),
  ) {
    ReaderNavigationBar(
      content = state.content,
      pages = pages,
      currentIndex = index,
      pagesPerSpread = pagesPerSpread,
      onJump = { target -> onJump(pages[target], pages.progressAt(target, pagesPerSpread)) },
    )
  }
}

@Composable
private fun VolumeKeyPageTurns(onTurn: (step: Int) -> Unit) {
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
internal fun ReaderNavigationBar(
  content: ReaderContent,
  pages: List<Page>,
  currentIndex: Int,
  pagesPerSpread: Int,
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
          text = (content as? ReaderContent.Epub)?.chapterTitle(pages[shown].section).orEmpty(),
          style = MaterialTheme.typography.labelLarge.bookText(),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(1F)
            .padding(end = 12.dp),
        )
        Text(
          text = stringResource(StringsR.string.reader_page, shown + 1, pages.size),
          style = MaterialTheme.typography.labelLarge,
        )
      }
      if (pages.size > 1) {
        // the range is part of the saved state, so a new layout of the pages starts a new slider
        key(pages.size) {
          val sliderState = rememberSliderState(
            value = currentIndex.toFloat(),
            trackRange = 0F..pages.lastIndex.toFloat(),
          )
          LaunchedEffect(currentIndex) {
            if (dragTarget == null) sliderState.value = currentIndex.toFloat()
          }
          val description = stringResource(StringsR.string.reader_go_to_page)
          Slider(
            state = sliderState,
            onValueChange = {
              sliderState.value = it
              dragTarget = it.roundToInt()
            },
            onValueChangeFinished = {
              dragTarget?.let(onJump)
              dragTarget = null
            },
            modifier = Modifier.semantics { contentDescription = description },
          )
        }
      }
      if (content is ReaderContent.Epub) {
        val left = pages.pagesLeftInChapter(currentIndex, pagesPerSpread)
        Text(
          text = if (left == 0) {
            stringResource(StringsR.string.reader_chapter_last_page)
          } else {
            pluralStringResource(StringsR.plurals.reader_chapter_pages_left, left, left)
          },
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
