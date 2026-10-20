package voice.features.reader

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import voice.core.data.EbookPosition

/** A PDF page is a whole section. An EPUB page is the [start] until [end] of a chapter's text. */
internal data class Page(
  val section: Int,
  val start: Int = 0,
  val end: Int = 0,
)

internal fun List<Page>.indexOfPosition(position: EbookPosition): Int {
  val index = indexOfLast { page ->
    page.section < position.section || (page.section == position.section && page.start <= position.offset)
  }
  return index.coerceAtLeast(0)
}

internal fun paginate(
  chapters: List<String>,
  textMeasurer: TextMeasurer,
  style: TextStyle,
  width: Int,
  height: Int,
): List<Page> {
  return chapters.flatMapIndexed { section, text ->
    if (text.isBlank()) return@flatMapIndexed emptyList()
    val layout = textMeasurer.measure(
      text = text,
      style = style,
      constraints = Constraints(maxWidth = width),
      skipCache = true,
    )
    buildList {
      var firstLine = 0
      while (firstLine < layout.lineCount) {
        val top = layout.getLineTop(firstLine)
        var lastLine = firstLine
        while (lastLine + 1 < layout.lineCount && layout.getLineBottom(lastLine + 1) - top <= height) {
          lastLine++
        }
        add(Page(section, layout.getLineStart(firstLine), layout.getLineEnd(lastLine)))
        firstLine = lastLine + 1
      }
    }
  }
}

/** The next paragraph to read aloud, cut at a sentence when it's longer than the speech engine takes. */
internal fun nextChunk(
  text: String,
  from: Int,
  maxLength: Int = 3000,
): IntRange? {
  var start = from.coerceAtLeast(0)
  while (start < text.length && text[start].isWhitespace()) start++
  if (start >= text.length) return null
  var end = text.indexOf('\n', start).takeIf { it != -1 } ?: text.length
  if (end - start > maxLength) {
    val limit = start + maxLength
    val sentenceEnd = text.lastIndexOf(". ", limit)
    end = if (sentenceEnd > start) sentenceEnd + 1 else limit
  }
  return start until end
}
