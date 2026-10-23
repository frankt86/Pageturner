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
  val bookmarks: List<EbookPosition> = emptyList(),
  val highlights: List<Highlight> = emptyList(),
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
