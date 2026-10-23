package voice.core.ebook

import android.net.Uri
import android.util.Xml
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

class EpubBook(
  val title: String?,
  val author: String?,
  val chapters: List<EpubChapter>,
  val cover: ByteArray?,
)

class EpubChapter(
  val title: String?,
  val text: String,
)

/** Without [withText], only the title, author and cover are read, which is what the library needs. */
fun parseEpub(
  input: InputStream,
  withText: Boolean = true,
): EpubBook {
  val entries = readEntries(input, withText)
  fun text(path: String) = entries[path]?.decodeToString()
  val opfPath = text("META-INF/container.xml")
    ?.let { Regex("full-path=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
    ?: entries.keys.first { it.endsWith(".opf", ignoreCase = true) }
  val opf = parseOpf(text(opfPath)!!)
  val opfFolder = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
  fun resolve(href: String) = resolvePath(opfFolder, href)

  val chapters = if (withText) {
    val titles = opf.tocHref?.let { tocHref ->
      val tocPath = resolve(tocHref)
      val tocFolder = tocPath.substringBeforeLast('/', missingDelimiterValue = "")
      text(tocPath)?.let { toc -> tocTitles(toc).mapKeys { (href, _) -> resolvePath(tocFolder, href) } }
    }.orEmpty()
    opf.spine.mapNotNull { href ->
      val path = resolve(href)
      text(path)?.let(::htmlToText)
        ?.takeIf { it.isNotBlank() }
        ?.let { EpubChapter(title = titles[path], text = it) }
    }
  } else {
    emptyList()
  }
  return EpubBook(
    title = opf.title,
    author = opf.author,
    chapters = chapters,
    cover = opf.coverHref?.let { entries[resolve(it)] },
  )
}

private val textExtensions = setOf("xml", "opf", "ncx", "html", "htm", "xhtml")
private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp")

private fun readEntries(
  input: InputStream,
  withText: Boolean,
): Map<String, ByteArray> {
  return buildMap {
    ZipInputStream(input).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        if (entry.isDirectory) continue
        val extension = entry.name.substringAfterLast('.').lowercase()
        val wanted = extension in imageExtensions ||
          extension == "opf" ||
          extension == "ncx" ||
          entry.name == "META-INF/container.xml" ||
          (withText && extension in textExtensions)
        if (wanted) {
          put(entry.name, zip.readBytes())
        }
      }
    }
  }
}

private class Opf(
  val title: String?,
  val author: String?,
  val spine: List<String>,
  val coverHref: String?,
  val tocHref: String?,
)

private fun parseOpf(xml: String): Opf {
  val parser = Xml.newPullParser()
  // tags are matched by their name without a prefix, so a book that forgot to declare one still opens
  parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
  parser.setInput(xml.reader())
  var title: String? = null
  var author: String? = null
  var coverId: String? = null
  var coverHref: String? = null
  var navHref: String? = null
  var ncxId: String? = null
  val hrefs = mutableMapOf<String, String>()
  val spine = mutableListOf<String>()
  while (parser.next() != XmlPullParser.END_DOCUMENT) {
    if (parser.eventType != XmlPullParser.START_TAG) continue
    when (parser.name.substringAfter(':')) {
      "title" -> if (title == null) title = parser.nextText().trim().takeIf { it.isNotEmpty() }
      "creator" -> if (author == null) author = parser.nextText().trim().takeIf { it.isNotEmpty() }
      "meta" -> if (parser.getAttributeValue(null, "name") == "cover") {
        coverId = parser.getAttributeValue(null, "content")
      }
      "item" -> {
        val id = parser.getAttributeValue(null, "id")
        val href = parser.getAttributeValue(null, "href")
        val properties = parser.getAttributeValue(null, "properties").orEmpty().split(' ')
        if (id != null && href != null) hrefs[id] = href
        if ("cover-image" in properties) coverHref = href
        if ("nav" in properties) navHref = href
      }
      "spine" -> ncxId = parser.getAttributeValue(null, "toc")
      "itemref" -> parser.getAttributeValue(null, "idref")?.let(spine::add)
    }
  }
  return Opf(
    title = title,
    author = author,
    spine = spine.mapNotNull(hrefs::get),
    coverHref = coverHref ?: coverId?.let(hrefs::get),
    tocHref = navHref ?: ncxId?.let(hrefs::get),
  )
}

/** The chapter titles from an EPUB 3 nav document or an EPUB 2 NCX, by the file they point to. */
internal fun tocTitles(toc: String): Map<String, String> {
  val links = if (toc.contains("<navMap", ignoreCase = true)) {
    Regex("<navLabel>\\s*<text>(.*?)</text>\\s*</navLabel>\\s*<content[^>]*src=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
      .findAll(toc)
      .map { it.groupValues[2] to it.groupValues[1] }
  } else {
    val nav = Regex("<nav[^>]*toc[^>]*>(.*?)</nav>", RegexOption.DOT_MATCHES_ALL).find(toc)?.groupValues?.get(1) ?: toc
    Regex("<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
      .findAll(nav)
      .map { it.groupValues[1] to it.groupValues[2] }
  }
  return buildMap {
    links.forEach { (href, label) ->
      val title = HtmlCompat.fromHtml(label, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim()
      val file = href.substringBefore('#')
      if (title.isNotEmpty() && file.isNotEmpty() && file !in this) put(file, title)
    }
  }
}

internal fun resolvePath(
  folder: String,
  href: String,
): String {
  val segments = ArrayDeque<String>()
  val path = if (folder.isEmpty()) href else "$folder/$href"
  Uri.decode(path.substringBefore('#')).split('/').forEach { segment ->
    when (segment) {
      "", "." -> Unit
      ".." -> segments.removeLastOrNull()
      else -> segments.addLast(segment)
    }
  }
  return segments.joinToString("/")
}

private fun htmlToText(html: String): String {
  val bodyStart = html.indexOf("<body", ignoreCase = true)
  val body = if (bodyStart == -1) html else html.substring(html.indexOf('>', bodyStart) + 1)
  val withoutScripts = body.replace(Regex("<(script|style)\\b[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
  return HtmlCompat.fromHtml(withoutScripts, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
    // the placeholder for images, which the reader leaves out
    .replace("￼", "")
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n\n")
}
