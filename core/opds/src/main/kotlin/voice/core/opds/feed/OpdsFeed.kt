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
  /** The feed says that this is an audiobook, which is what makes a plain zip one. */
  val isAudiobook: Boolean = false,
) {

  /** The file Pageturner can read, an EPUB before a PDF. */
  val preferredAcquisition: OpdsAcquisition?
    get() = acquisitions.firstOrNull { it.format == OpdsFormat.Epub }
      ?: acquisitions.firstOrNull { it.format == OpdsFormat.Pdf }

  /** The audio Pageturner can play, or null when the entry has none. */
  val audiobook: OpdsAudiobook? get() = opdsAudiobook(acquisitions, isAudiobook)
}

public data class OpdsAcquisition(
  val url: String,
  /** The media type without parameters, or null when the feed doesn't name one. */
  val type: String?,
  val title: String? = null,
  /** The size in bytes, when the feed tells it. */
  val length: Long? = null,
) {

  val format: OpdsFormat? get() = opdsFormat(type, url)

  /** A short name for a format Pageturner can't read, like CBZ or MOBI. */
  val label: String get() = formatLabel(type, url)

  /** The audio format, like M4B or MP3, or null when this isn't an audio file. */
  val audioLabel: String? get() = audioLabel(type, url, title)

  /** The file extension the audio is stored with, so the scanner knows what it is. */
  internal val audioExtension: String get() = audioExtension(type, url, title)
}

/** The files of an audiobook, in the order they play. */
public data class OpdsAudiobook(
  val files: List<OpdsAcquisition>,
  val packaging: AudiobookPackaging,
) {

  /** A short name of the format, like M4B or MP3. */
  val format: String
    get() = when (packaging) {
      AudiobookPackaging.Files -> files.first().audioLabel ?: "?"
      AudiobookPackaging.Zip -> "ZIP"
      AudiobookPackaging.Manifest -> files.first().label
    }

  /** The size of all files in bytes, or null when the feed doesn't tell it for each of them. */
  val size: Long?
    get() = files.sumOf { it.length?.takeIf { length -> length > 0 } ?: return null }
}

public enum class AudiobookPackaging {
  /** Audio files to download as they are: a single M4B, or one MP3 per chapter. */
  Files,

  /** A zip with the audio files inside, like an `application/audiobook+zip`. */
  Zip,

  /** A Readium audiobook manifest, which lists the audio files. */
  Manifest,
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
    AUDIOBOOK_ZIP -> "ZIP"
    AUDIOBOOK_MANIFEST -> "JSON"
    else -> null
  }
  if (known != null) return known
  val extension = url.extension()
  if (extension != null && extension.length in 2..5) return extension.uppercase()
  return type?.substringAfter('/')?.substringAfterLast('.')?.substringBefore('+')?.uppercase() ?: "?"
}

private val audioTypes = mapOf(
  "audio/mp4" to "M4B",
  "audio/x-m4b" to "M4B",
  "audio/m4b" to "M4B",
  "audio/x-m4a" to "M4A",
  "audio/m4a" to "M4A",
  "audio/mpeg" to "MP3",
  "audio/mp3" to "MP3",
  "audio/ogg" to "OGG",
  "audio/opus" to "OPUS",
  "audio/flac" to "FLAC",
  "audio/x-flac" to "FLAC",
  // some servers label an m4b as a video
  "video/mp4" to "M4B",
)

// the formats the library plays, see SupportedAudioFormats
private val audioExtensions = setOf("m4b", "m4a", "mp4", "mp3", "ogg", "oga", "opus", "flac", "aac", "wav", "mka")

internal fun isAudioFileName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in audioExtensions

private fun isGenericType(type: String?): Boolean {
  return type == null || type == "application/octet-stream" || type == "binary/octet-stream"
}

private fun audioLabel(
  type: String?,
  url: String,
  title: String?,
): String? {
  val extension = audioExtensionOf(url, title)
  return when {
    type != null && type in audioTypes -> extension?.uppercase() ?: audioTypes.getValue(type)
    isGenericType(type) -> extension?.uppercase()
    else -> null
  }
}

private fun audioExtension(
  type: String?,
  url: String,
  title: String?,
): String {
  audioExtensionOf(url, title)?.let { return it }
  return when (audioTypes[type]) {
    "M4A" -> "m4a"
    "MP3" -> "mp3"
    "OGG" -> "ogg"
    "OPUS" -> "opus"
    "FLAC" -> "flac"
    else -> "m4b"
  }
}

private fun audioExtensionOf(
  url: String,
  title: String?,
): String? {
  return url.extension()?.takeIf { it in audioExtensions }
    ?: title?.extension()?.takeIf { it in audioExtensions }
}

private const val AUDIOBOOK_ZIP = "application/audiobook+zip"
private const val AUDIOBOOK_MANIFEST = "application/audiobook+json"

// a book that comes in both is downloaded in the first, which keeps its chapters in one file
private val audioPreference = listOf("M4B", "M4A", "MP3", "OPUS", "OGG", "FLAC")

internal fun opdsAudiobook(
  acquisitions: List<OpdsAcquisition>,
  isAudiobook: Boolean,
): OpdsAudiobook? {
  val audio = acquisitions.filter { it.audioLabel != null }.distinctBy { it.url }
  if (audio.isNotEmpty()) {
    val byFormat = audio.groupBy { it.audioLabel!! }
    val format = audioPreference.firstOrNull { it in byFormat } ?: byFormat.keys.first()
    return OpdsAudiobook(files = byFormat.getValue(format), packaging = AudiobookPackaging.Files)
  }
  acquisitions.firstOrNull { it.type == AUDIOBOOK_MANIFEST }?.let {
    return OpdsAudiobook(files = listOf(it), packaging = AudiobookPackaging.Manifest)
  }
  val zip = acquisitions.firstOrNull { acquisition ->
    val extension = acquisition.url.extension() ?: acquisition.title?.extension()
    when {
      acquisition.type == AUDIOBOOK_ZIP || extension == "audiobook" -> true
      // a plain zip is a comic just as often
      isAudiobook -> acquisition.type == "application/zip" || (isGenericType(acquisition.type) && extension == "zip")
      else -> false
    }
  }
  return zip?.let { OpdsAudiobook(files = listOf(it), packaging = AudiobookPackaging.Zip) }
}

private fun String.extension(): String? {
  val path = substringBefore('?').substringBefore('#').substringAfterLast('/')
  if ('.' !in path) return null
  return path.substringAfterLast('.').lowercase()
}

internal fun String.mediaType(): String = substringBefore(';').trim().lowercase()
