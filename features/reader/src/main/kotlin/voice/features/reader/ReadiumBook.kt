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
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import voice.core.data.Ebook
import voice.core.data.HighlightColor
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
    val position = locator?.locations?.position ?: return 0
    return (position - 1).coerceIn(0, (positions.size - 1).coerceAtLeast(0))
  }

  fun pagesLeftInChapter(index: Int): Int {
    val href = positions.getOrNull(index)?.href ?: return 0
    return positions.drop(index + 1).takeWhile { it.href == href }.size
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
  val publication = opener.open(asset, allowUserInteraction = false).getOrElse { error ->
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
