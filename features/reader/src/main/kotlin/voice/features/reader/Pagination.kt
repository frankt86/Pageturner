package voice.features.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
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
  paragraphGap: TextUnit,
  width: Int,
  height: Int,
): List<Page> {
  return chapters.flatMapIndexed { section, text ->
    if (text.isBlank()) return@flatMapIndexed emptyList()
    val layout = textMeasurer.measure(
      text = AnnotatedString(text, paragraphStyles = paragraphStyles(text, paragraphGap, continued = false)),
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

/**
 * Lays out the paragraphs of [text] apart from each other, so the empty lines between them take [gap] each, and a
 * paragraph [continued] from the page before doesn't indent its first line. With an unspecified [gap], the text stays
 * one paragraph with blank lines in it, as it's always been.
 */
internal fun paragraphStyles(
  text: String,
  gap: TextUnit,
  continued: Boolean,
): List<AnnotatedString.Range<ParagraphStyle>> {
  if (gap.isUnspecified) return emptyList()
  return buildList {
    var lineBreak = text.indexOf('\n')
    val firstEnd = if (lineBreak == -1) text.length else lineBreak
    if (continued && firstEnd > 0) {
      add(AnnotatedString.Range(ParagraphStyle(textIndent = TextIndent.None), 0, firstEnd))
    }
    while (lineBreak != -1) {
      var end = lineBreak
      while (end < text.length && text[end] == '\n') end++
      add(AnnotatedString.Range(ParagraphStyle(lineHeight = gap), lineBreak, end))
      lineBreak = text.indexOf('\n', end)
    }
  }
}

/**
 * The word after a page that ends in the middle of a paragraph. Laid out after the page but not shown, it keeps the
 * page's last line as it was measured: justified, and with its hyphen when a word is split.
 */
internal fun pageTail(
  chapter: String,
  end: Int,
): String {
  if (end <= 0 || end >= chapter.length || chapter[end - 1] == '\n') return ""
  var tailEnd = end
  while (tailEnd < chapter.length && !chapter[tailEnd].isWhitespace()) tailEnd++
  return chapter.substring(end, tailEnd)
}

/** The next paragraph to read aloud, cut at a sentence when it's longer than the speech engine takes. */
internal fun nextChunk(
  text: String,
  from: Int,
  // the natural voice only starts speaking once the whole chunk is ready, so a long one means a long silence
  maxLength: Int = 200,
): IntRange? {
  var start = from.coerceAtLeast(0)
  while (start < text.length && text[start].isWhitespace()) start++
  if (start >= text.length) return null
  var end = text.indexOf('\n', start).takeIf { it != -1 } ?: text.length
  if (end - start > maxLength) {
    val limit = start + maxLength
    val sentenceEnd = listOf(". ", "! ", "? ").maxOf { text.lastIndexOf(it, limit) }
    val wordEnd = text.lastIndexOf(' ', limit)
    end = when {
      sentenceEnd > start -> sentenceEnd + 1
      wordEnd > start -> wordEnd
      else -> limit
    }
  }
  return start until end
}
