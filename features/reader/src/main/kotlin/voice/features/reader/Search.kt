package voice.features.reader

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// PDFs only give their text from Android 15 on
internal val ReaderContent.searchable: Boolean
  get() = this is ReaderContent.Epub || Build.VERSION.SDK_INT >= 35

internal data class SearchResult(
  val match: BookRange,
  /** The text around the match, which starts at [snippetMatchStart] in it. */
  val snippet: String,
  val snippetMatchStart: Int,
)

internal suspend fun searchBook(
  content: ReaderContent,
  query: String,
  limit: Int = 500,
): List<SearchResult> = withContext(Dispatchers.Default) {
  val results = mutableListOf<SearchResult>()
  if (query.isEmpty()) return@withContext results
  for (section in 0 until content.sectionCount) {
    ensureActive()
    val text = when (content) {
      is ReaderContent.Epub -> content.chapters[section]
      is ReaderContent.Pdf -> content.pdf.text(section)
    }
    var start = text.indexOf(query, ignoreCase = true)
    while (start != -1) {
      if (results.size == limit) return@withContext results
      results += searchResult(text, BookRange(section, start, start + query.length))
      start = text.indexOf(query, startIndex = start + query.length, ignoreCase = true)
    }
  }
  results
}

private fun searchResult(
  text: String,
  match: BookRange,
): SearchResult {
  val from = (match.start - 40).coerceAtLeast(0)
  val to = (match.end + 80).coerceAtMost(text.length)
  val before = if (from > 0) "…" else ""
  val after = if (to < text.length) "…" else ""
  return SearchResult(
    match = match,
    snippet = before + text.substring(from, to).replace('\n', ' ') + after,
    snippetMatchStart = before.length + match.start - from,
  )
}

internal fun snippet(
  text: String,
  offset: Int,
): String {
  val start = offset.coerceIn(0, text.length)
  return text.substring(start, (start + 160).coerceAtMost(text.length)).replace('\n', ' ').trim()
}
