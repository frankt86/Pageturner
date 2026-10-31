package voice.core.data

import android.net.Uri
import kotlinx.serialization.Serializable
import voice.core.common.serialization.UriSerializer
import voice.core.documentfile.CachedDocumentFile

@Serializable
public data class Ebook(
  @Serializable(with = UriSerializer::class)
  val uri: Uri,
  val name: String,
  val format: EbookFormat,
  val position: EbookPosition = EbookPosition(),
  val progress: Float = 0F,
  // kept when the file is gone, so the place isn't lost while a folder is unreachable
  val available: Boolean = true,
  val author: String? = null,
  /** The path of the cover image taken from the book. */
  val cover: String? = null,
  /** Epoch milliseconds, 0 before the book was first opened. */
  val lastOpened: Long = 0,
  /** Whether the title, author and cover were read from the file yet. */
  val metadataRead: Boolean = false,
  /** The name the listener gave the book, which wins over the one from the file. */
  val customName: String? = null,
  /** Removed from the library by the listener, while the file stays. */
  val hidden: Boolean = false,
  val bookmarks: List<EbookPosition> = emptyList(),
  val highlights: List<Highlight> = emptyList(),
  /** Where the new book engine is in the book, a Readium Locator as JSON. */
  val readiumLocator: String? = null,
  /** The bookmarks of the new book engine, Readium Locators as JSON. */
  val readiumBookmarks: List<String> = emptyList(),
  val readiumHighlights: List<ReadiumHighlight> = emptyList(),
  /** Set for a book downloaded from an OPDS catalog. Older downloads live in the app's own storage. */
  val opdsSource: OpdsSource? = null,
  /** Epoch milliseconds of the last move in the book here, which decides against a newer place on a server. */
  val positionModified: Long = 0,
  /** The author the listener gave the book, which wins over the one from the file. */
  val customAuthor: String? = null,
  /** The cover the listener picked, which wins over the one from the file or the catalog. */
  val customCover: String? = null,
  val series: String? = null,
  /** The place of the book in its [series], like "2" or "2.5". */
  val seriesPart: String? = null,
  // books whose metadata was read before series were, read their file once more
  val seriesRead: Boolean = false,
  /** The series the listener gave the book, which wins over the one from the file. */
  val customSeries: String? = null,
  /** The part the listener gave the book, which wins over the one from the file. */
  val customPart: String? = null,
) {

  val title: String get() = customName ?: name

  val shownAuthor: String? get() = customAuthor ?: author

  val shownCover: String? get() = customCover ?: cover

  val shownSeries: String? get() = customSeries ?: series

  val shownPart: String? get() = customPart ?: seriesPart
}

@Serializable
public data class OpdsSource(
  val catalogId: String,
  val entryId: String,
  val acquisitionUrl: String,
  /** The id of the book on a Komga server, which syncs the reading progress. */
  val komgaBookId: String? = null,
  /** The [Ebook.positionModified] the server knows, so a place that couldn't be sent goes out later. */
  val syncedPosition: Long = 0,
)

/**
 * In an EPUB, the chapter and the character in it. In a PDF, the page and the character in its text.
 * Pages are laid out anew for every screen size and font size, so the place is kept in the text.
 */
@Serializable
public data class EbookPosition(
  val section: Int = 0,
  val offset: Int = 0,
)

/** The text from [start] until [end] in a section, counted like [EbookPosition.offset]. */
@Serializable
public data class Highlight(
  val section: Int,
  val start: Int,
  val end: Int,
  val color: HighlightColor = HighlightColor.Yellow,
  val note: String? = null,
)

/** A highlight of the new book engine, where [locator] is a Readium Locator as JSON. */
@Serializable
public data class ReadiumHighlight(
  val locator: String,
  val color: HighlightColor = HighlightColor.Yellow,
  val note: String? = null,
)

public enum class HighlightColor {
  Yellow,
  Green,
  Blue,
  Pink,
}

public enum class EbookFormat {
  Epub,
  Pdf,
}

public fun CachedDocumentFile.ebookFormat(): EbookFormat? {
  if (!isFile) return null
  return when (name?.substringAfterLast(".")?.lowercase()) {
    "epub" -> EbookFormat.Epub
    "pdf" -> EbookFormat.Pdf
    else -> null
  }
}
