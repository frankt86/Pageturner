package voice.features.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import voice.core.data.EbookPosition
import voice.core.data.Highlight
import voice.core.data.HighlightColor
import kotlin.math.abs

/** The text from [start] until [end] in a section, counted like [EbookPosition.offset]. */
internal data class BookRange(
  val section: Int,
  val start: Int,
  val end: Int,
)

internal fun ReaderContent.Epub.text(range: BookRange): String {
  val chapter = chapters.getOrNull(range.section) ?: return ""
  val start = range.start.coerceIn(0, chapter.length)
  return chapter.substring(start, range.end.coerceIn(start, chapter.length))
}

internal fun Page.contains(position: EbookPosition): Boolean {
  // a PDF page is a whole section
  return position.section == section && (start == end || position.offset in start until end)
}

// highlighter ink like on paper, so it's not taken from the theme
internal fun HighlightColor.ink(): Color = when (this) {
  HighlightColor.Yellow -> Color(0xFFFFD54F)
  HighlightColor.Green -> Color(0xFF81C784)
  HighlightColor.Blue -> Color(0xFF64B5F6)
  HighlightColor.Pink -> Color(0xFFF48FB1)
}

// see-through, so the text stays readable on light and dark pages
internal fun HighlightColor.background(): Color = ink().copy(alpha = 0.45F)

internal data class Mark(
  val start: Int,
  val end: Int,
  val color: Color,
)

@Composable
internal fun pageMarks(
  section: Int,
  readingRange: IntRange?,
  highlights: List<Highlight>,
  searchMatch: BookRange?,
  selection: BookRange?,
): List<Mark> {
  val readAloudColor = MaterialTheme.colorScheme.primaryContainer
  val searchColor = MaterialTheme.colorScheme.tertiaryContainer
  val selectionColor = LocalTextSelectionColors.current.backgroundColor
  return buildList {
    highlights.forEach { if (it.section == section) add(Mark(it.start, it.end, it.color.background())) }
    if (readingRange != null) add(Mark(readingRange.first, readingRange.last + 1, readAloudColor))
    if (searchMatch != null && searchMatch.section == section) add(Mark(searchMatch.start, searchMatch.end, searchColor))
    if (selection != null && selection.section == section) add(Mark(selection.start, selection.end, selectionColor))
  }
}

/** [text] starts at [offset] in its section. */
internal fun markedText(
  text: String,
  offset: Int,
  marks: List<Mark>,
): AnnotatedString = buildAnnotatedString {
  append(text)
  marks.forEach { mark ->
    val start = (mark.start - offset).coerceAtLeast(0)
    val end = (mark.end - offset).coerceAtMost(text.length)
    if (start < end) addStyle(SpanStyle(background = mark.color), start, end)
  }
}

internal fun wordAt(
  text: String,
  index: Int,
): IntRange {
  if (!text[index].isWordCharacter()) return index..index
  var start = index
  while (start > 0 && text[start - 1].isWordCharacter()) start--
  var end = index
  while (end + 1 < text.length && text[end + 1].isWordCharacter()) end++
  return start..end
}

private fun Char.isWordCharacter(): Boolean = isLetterOrDigit() || this == '\'' || this == '’'

/** Where the text of a page is laid out, so a touch can be matched to a character. */
internal class PageText {
  var section: Int = 0
  var offset: Int = 0
  var layout: TextLayoutResult? = null
  var coordinates: LayoutCoordinates? = null
}

private class TextHit(
  val section: Int,
  val caret: Int,
  val character: Int?,
)

/**
 * The text selected on the pages. The pager lies above the pages to turn them, so it passes the touches on here and
 * this finds the characters under them.
 */
@Stable
internal class PageSelection {

  var range: BookRange? by mutableStateOf(null)
    private set

  // the part that stays selected while a finger moves the other end
  private var anchor: BookRange? = null
  private val texts = mutableMapOf<Int, PageText>()
  var root: LayoutCoordinates? = null

  fun register(
    pageIndex: Int,
    text: PageText,
  ) {
    texts[pageIndex] = text
  }

  fun clear() {
    range = null
    anchor = null
  }

  fun highlightAt(
    position: Offset,
    pageIndices: IntRange,
    highlights: List<Highlight>,
  ): Highlight? {
    val hit = hit(position, pageIndices) ?: return null
    val character = hit.character ?: return null
    return highlights.lastOrNull { it.section == hit.section && character >= it.start && character < it.end }
  }

  /** Selects the word under [position], returns whether there was one. */
  fun selectWord(
    content: ReaderContent,
    position: Offset,
    pageIndices: IntRange,
  ): Boolean {
    val hit = hit(position, pageIndices) ?: return false
    val character = hit.character ?: return false
    val text = (content as? ReaderContent.Epub)?.chapters?.getOrNull(hit.section) ?: return false
    if (character >= text.length || text[character].isWhitespace()) return false
    val word = wordAt(text, character)
    val selected = BookRange(hit.section, word.first, word.last + 1)
    range = selected
    anchor = selected
    return true
  }

  /** The end of the selection closer to [position] follows the finger from now on. */
  fun grab(
    position: Offset,
    pageIndices: IntRange,
  ) {
    val range = range ?: return
    val hit = hit(position, pageIndices) ?: return
    if (hit.section != range.section) return
    anchor = if (abs(hit.caret - range.start) < abs(hit.caret - range.end)) {
      range.copy(start = range.end)
    } else {
      range.copy(end = range.start)
    }
  }

  fun extend(
    content: ReaderContent,
    position: Offset,
    pageIndices: IntRange,
  ) {
    val anchor = anchor ?: return
    val hit = hit(position, pageIndices) ?: return
    if (hit.section != anchor.section) return
    val text = (content as? ReaderContent.Epub)?.chapters?.getOrNull(hit.section) ?: return
    if (text.isEmpty()) return
    val word = wordAt(text, (hit.character ?: hit.caret).coerceIn(0, text.length - 1))
    range = BookRange(anchor.section, minOf(anchor.start, word.first), maxOf(anchor.end, word.last + 1))
  }

  private fun hit(
    position: Offset,
    pageIndices: IntRange,
  ): TextHit? {
    val root = root?.takeIf { it.isAttached } ?: return null
    for (index in pageIndices) {
      val text = texts[index] ?: continue
      val coordinates = text.coordinates?.takeIf { it.isAttached } ?: continue
      val layout = text.layout ?: continue
      val local = coordinates.localPositionOf(root, position)
      val size = coordinates.size
      if (local.x < 0F || local.y < 0F || local.x > size.width || local.y > size.height) continue
      val caret = layout.getOffsetForPosition(local)
      val length = layout.layoutInput.text.length
      val character = listOf(caret, caret - 1).firstOrNull { it in 0 until length && layout.getBoundingBox(it).contains(local) }
      return TextHit(
        section = text.section,
        caret = text.offset + caret,
        character = character?.let { text.offset + it },
      )
    }
    return null
  }
}

/**
 * Like detectTapGestures, but a long press starts a selection and the finger then moves its end. While something is
 * selected, a drag moves the closer end of the selection and a tap ends it.
 */
internal suspend fun PointerInputScope.detectPageGestures(
  selecting: () -> Boolean,
  onTap: (Offset) -> Unit,
  onLongPress: (Offset) -> Boolean,
  onSelectionGrab: (Offset) -> Unit,
  onSelectionDrag: (Offset) -> Unit,
) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (selecting()) {
      onSelectionGrab(down.position)
      if (!followPointer(down, viewConfiguration.touchSlop, onSelectionDrag)) {
        onTap(down.position)
      }
      return@awaitEachGesture
    }
    var cancelled = false
    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
      waitForUpOrCancellation().also { cancelled = it == null }
    }
    if (up != null) {
      up.consume()
      onTap(up.position)
    } else if (!cancelled && onLongPress(down.position)) {
      val _ = followPointer(down, slop = 0F, onMove = onSelectionDrag)
    }
  }
}

// consumes before the pager sees the events, so it doesn't turn the page while selecting. Returns whether it moved.
private suspend fun AwaitPointerEventScope.followPointer(
  down: PointerInputChange,
  slop: Float,
  onMove: (Offset) -> Unit,
): Boolean {
  var moved = false
  while (true) {
    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return moved
    if (!change.pressed) {
      change.consume()
      return moved
    }
    if (!moved && (change.position - down.position).getDistance() > slop) moved = true
    if (moved) onMove(change.position)
    change.consume()
  }
}
