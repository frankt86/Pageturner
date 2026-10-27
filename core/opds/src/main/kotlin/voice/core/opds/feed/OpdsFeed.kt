package voice.core.opds.feed

public data class OpdsFeed(
  val title: String?,
  val navigation: List<OpdsNavigation>,
  val publications: List<OpdsPublication>,
  /** The next page of this feed. */
  val nextUrl: String?,
  val search: OpdsSearch?,
)

/** A link to another feed, like a shelf, an author or "Recently added". */
public data class OpdsNavigation(
  val title: String,
  val url: String,
  val summary: String?,
)

public data class OpdsPublication(
  val id: String,
  val title: String,
  val authors: List<String>,
  val summary: String?,
  val coverUrl: String?,
  val thumbnailUrl: String?,
  val acquisitions: List<OpdsAcquisition>,
) {

  /** The file Pageturner can read, an EPUB before a PDF. */
  val preferredAcquisition: OpdsAcquisition?
    get() = acquisitions.firstOrNull { it.format == OpdsFormat.Epub }
      ?: acquisitions.firstOrNull { it.format == OpdsFormat.Pdf }
}

public data class OpdsAcquisition(
  val url: String,
  /** The media type without parameters, or null when the feed doesn't name one. */
  val type: String?,
) {

  val format: OpdsFormat? get() = opdsFormat(type, url)

  /** A short name for a format Pageturner can't read, like CBZ or MOBI. */
  val label: String get() = formatLabel(type, url)
}

public enum class OpdsFormat {
  Epub,
  Pdf,
}

public sealed interface OpdsSearch {

  /** A url with placeholders for the search terms. */
  public data class Template(val template: String) : OpdsSearch

  /** An OpenSearch description, which holds the template. OPDS 1.2 links to one. */
  public data class Description(val url: String) : OpdsSearch
}

internal fun opdsFormat(
  type: String?,
  url: String,
): OpdsFormat? {
  return when (type) {
    "application/epub+zip" -> OpdsFormat.Epub
    "application/pdf" -> OpdsFormat.Pdf
    null, "application/octet-stream" -> when (url.extension()) {
      "epub" -> OpdsFormat.Epub
      "pdf" -> OpdsFormat.Pdf
      else -> null
    }
    else -> null
  }
}

private fun formatLabel(
  type: String?,
  url: String,
): String {
  val known = when (type) {
    "application/epub+zip" -> "EPUB"
    "application/pdf" -> "PDF"
    "application/kepub+zip" -> "KEPUB"
    "application/x-mobipocket-ebook" -> "MOBI"
    "application/vnd.amazon.ebook", "application/x-mobi8-ebook", "application/azw3" -> "AZW3"
    "application/x-cbz", "application/vnd.comicbook+zip", "application/zip" -> "CBZ"
    "application/x-cbr", "application/vnd.comicbook-rar", "application/x-rar-compressed", "application/vnd.rar" -> "CBR"
    "application/x-fictionbook+xml", "text/fb2+xml" -> "FB2"
    "text/plain" -> "TXT"
    "text/html", "application/xhtml+xml" -> "HTML"
    "application/rtf" -> "RTF"
    else -> null
  }
  if (known != null) return known
  val extension = url.extension()
  if (extension != null && extension.length in 2..5) return extension.uppercase()
  return type?.substringAfter('/')?.substringAfterLast('.')?.substringBefore('+')?.uppercase() ?: "?"
}

private fun String.extension(): String? {
  val path = substringBefore('?').substringBefore('#').substringAfterLast('/')
  if ('.' !in path) return null
  return path.substringAfterLast('.').lowercase()
}

internal fun String.mediaType(): String = substringBefore(';').trim().lowercase()
