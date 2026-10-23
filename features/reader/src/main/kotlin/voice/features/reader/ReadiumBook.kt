package voice.features.reader

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.landmarks
import org.readium.r2.shared.publication.epub.pageList
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.shared.util.use
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import voice.core.data.Ebook
import voice.core.data.HighlightColor
import voice.core.data.ReadingPace
import voice.core.logging.api.Logger

/** An EPUB opened with Readium, the new book engine. */
@Stable
internal class ReadiumBook(
  val publication: Publication,
  /** Readium's positions, which stay the same for every screen and text size, so they serve as the book's pages. */
  val positions: List<Locator>,
  locator: Locator?,
  bookmarks: List<Locator>,
  highlights: List<LocatedHighlight>,
  /** The pages of the printed book, in reading order. */
  private val printPages: List<PrintPage> = emptyList(),
) {
  val navigatorFactory = EpubNavigatorFactory(publication)

  /** Where the reader is, and where to start when the pages are shown again, e.g. after unfolding. */
  var locator by mutableStateOf(locator)
  var bookmarks by mutableStateOf(bookmarks)
  var highlights by mutableStateOf(highlights)

  /** Shown for a moment after jumping to a search result. */
  var searchMatch by mutableStateOf<Locator?>(null)

  /** What's read aloud right now. */
  var readingAloud by mutableStateOf<Locator?>(null)

  /** The table of contents with the depth of each entry, or the chapters when the book has none. */
  val tableOfContents: List<Pair<Int, Link>> = publication.tableOfContents
    .ifEmpty { publication.readingOrder }
    .flattenWithDepth()

  private val titles: Map<Url, String> = buildMap {
    tableOfContents.forEach { entry ->
      val title = entry.second.title ?: return@forEach
      putIfAbsent(entry.second.url().removeFragment(), title)
    }
  }

  fun chapterTitle(locator: Locator): String? = locator.title ?: titles[locator.href.removeFragment()]

  fun positionIndex(locator: Locator?): Int {
    val locations = locator?.locations ?: return 0
    val index = locations.position?.minus(1)
      // a selection, like a highlight, can come without a position
      ?: locations.totalProgression?.let { (it * positions.size).toInt() }
      ?: 0
    return index.coerceIn(0, (positions.size - 1).coerceAtLeast(0))
  }

  fun pagesLeftInChapter(index: Int): Int {
    val href = positions.getOrNull(index)?.href ?: return 0
    return positions.drop(index + 1).takeWhile { it.href == href }.size
  }

  /** The positions where a new file of the book starts, which is mostly a new chapter. */
  val chapterStarts: List<Int> = positions.indices.filter { it == 0 || positions[it].href != positions[it - 1].href }

  private val readingOrder: List<Url> = publication.readingOrder.map { it.url().removeFragment() }

  /** The positions of the main text, without front and back matter, from the book's landmarks. */
  val bodyRange: IntRange? = run {
    fun landmark(type: String): Int? {
      val link = publication.landmarks.firstOrNull { entry -> entry.rels.any { it.substringAfterLast('#') == type } } ?: return null
      val href = link.url().removeFragment()
      return positions.indexOfFirst { it.href.removeFragment() == href }.takeIf { it >= 0 }
    }
    val start = landmark("bodymatter") ?: 0
    val end = landmark("backmatter")?.takeIf { it > start } ?: positions.size
    if (start == 0 && end == positions.size) null else start until end
  }

  fun printPage(locator: Locator): String? {
    if (printPages.isEmpty()) return null
    val resource = readingOrder.indexOf(locator.href.removeFragment())
    val progression = locator.locations.progression ?: 0.0
    return printPages.lastOrNull { page ->
      page.resource < resource || (page.resource == resource && page.progression <= progression)
    }?.label
  }

  /** The progress at the position [index], or at [locator] when it's the place on screen. */
  fun readingProgress(
    index: Int,
    locator: Locator?,
    bodyOnly: Boolean,
    characters: Int,
    pace: ReadingPace,
  ): ReadingProgress {
    val href = positions[index].href
    var chapterFirst = index
    while (chapterFirst > 0 && positions[chapterFirst - 1].href == href) chapterFirst--
    var chapterLast = index
    while (chapterLast < positions.lastIndex && positions[chapterLast + 1].href == href) chapterLast++
    val body = bodyRange?.takeIf { bodyOnly } ?: positions.indices
    val place = locator ?: positions[index]
    val progression = place.locations.totalProgression ?: positions[index].locations.totalProgression ?: 0.0
    fun progressionAt(position: Int): Double = positions.getOrNull(position)?.locations?.totalProgression ?: 1.0
    return ReadingProgress(
      page = index + 1,
      pageCount = positions.size,
      bookFraction = ((index + 1 - body.first).toFloat() / (body.last - body.first + 1)).coerceIn(0F, 1F),
      pagesLeftInBook = (body.last - index).coerceAtLeast(0),
      chapterPage = index - chapterFirst + 1,
      chapterPageCount = chapterLast - chapterFirst + 1,
      chapterFraction = (index - chapterFirst + 1).toFloat() / (chapterLast - chapterFirst + 1),
      pagesLeftInChapter = chapterLast - index,
      chapterTimeLeft = timeToRead((progressionAt(chapterLast + 1) - progression) * characters, pace),
      bookTimeLeft = timeToRead((progressionAt(body.last + 1) - progression) * characters, pace),
      printPage = printPage(place),
      standardPages = standardPages(characters),
    )
  }

  fun isOnPage(
    locator: Locator,
    page: Locator?,
  ): Boolean {
    page ?: return false
    return locator.href.removeFragment() == page.href.removeFragment() &&
      locator.locations.position == page.locations.position
  }
}

private fun List<Link>.flattenWithDepth(depth: Int = 0): List<Pair<Int, Link>> {
  return flatMap { link -> listOf(depth to link) + link.children.flattenWithDepth(depth + 1) }
}

/** A page of the printed book, starting at [progression] in the file [resource] of the reading order. */
internal class PrintPage(
  val resource: Int,
  val progression: Double,
  val label: String,
)

/**
 * Finds where the book's page list puts each printed page. Where a page starts within a file is told by where its
 * anchor is in the file's text, which is about where it is on screen.
 */
private suspend fun Publication.printPages(): List<PrintPage> {
  if (pageList.isEmpty()) return emptyList()
  val files = readingOrder.map { it.url().removeFragment() }
  val texts = mutableMapOf<Int, String?>()
  return pageList
    .mapNotNull { link ->
      val label = link.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      val url = link.url()
      val resource = files.indexOf(url.removeFragment()).takeIf { it >= 0 } ?: return@mapNotNull null
      val fragment = url.fragment ?: return@mapNotNull PrintPage(resource, 0.0, label)
      val text = texts.getOrPut(resource) {
        get(readingOrder[resource])?.use { it.read().getOrNull() }?.decodeToString()
      } ?: return@mapNotNull null
      val anchor = listOf("id=\"$fragment\"", "id='$fragment'")
        .map { text.indexOf(it) }
        .filter { it >= 0 }
        .minOrNull()
        ?: return@mapNotNull null
      PrintPage(resource, anchor.toDouble() / text.length, label)
    }
    .sortedWith(compareBy<PrintPage>({ it.resource }, { it.progression }))
}

internal data class LocatedHighlight(
  val locator: Locator,
  val color: HighlightColor,
  val note: String?,
) {
  /** Stays the same when the color or note changes, so Readium updates the highlight in place. */
  val id: String = locator.toJsonString().hashCode().toString()
}

internal suspend fun openReadiumBook(
  context: Context,
  uri: Uri,
  ebook: Ebook,
): ReadiumBook? {
  val url = uri.toAbsoluteUrl() ?: return null
  val httpClient = DefaultHttpClient()
  val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
  val asset = assetRetriever.retrieve(url).getOrElse { error ->
    Logger.w("Readium could not read the book: ${error::class.simpleName}")
    return null
  }
  val opener = PublicationOpener(
    publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null),
  )
  val publication = opener.open(
    asset = asset,
    allowUserInteraction = false,
    onCreatePublication = { container = FontsContainer(context, container) },
  ).getOrElse { error ->
    Logger.w("Readium could not open the book: ${error::class.simpleName}")
    asset.close()
    return null
  }
  if (!publication.conformsTo(Publication.Profile.EPUB)) {
    publication.close()
    return null
  }
  // without a place from the new engine, start about where the old one was
  val locator = ebook.readiumLocator?.toLocator()
    ?: ebook.progress.takeIf { it > 0F }?.let { publication.locateProgression(it.toDouble()) }
  return ReadiumBook(
    publication = publication,
    positions = publication.positions(),
    locator = locator,
    bookmarks = ebook.readiumBookmarks.mapNotNull { it.toLocator() },
    highlights = ebook.readiumHighlights.mapNotNull { highlight ->
      highlight.locator.toLocator()?.let { LocatedHighlight(it, highlight.color, highlight.note) }
    },
    printPages = publication.printPages(),
  )
}

internal fun String.toLocator(): Locator? {
  return try {
    Locator.fromJSON(JSONObject(this))
  } catch (e: JSONException) {
    // not the exception, its message holds the JSON with the chapter title
    Logger.w("Could not read a saved place")
    null
  }
}

internal fun Locator.toJsonString(): String = toJSON().toString()
