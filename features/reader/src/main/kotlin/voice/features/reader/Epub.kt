package voice.features.reader

import android.net.Uri
import android.util.Xml
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

internal class EpubFile(
  val title: String?,
  val chapters: List<String>,
)

internal fun parseEpub(input: InputStream): EpubFile {
  val entries = readTextEntries(input)
  val opfPath = entries["META-INF/container.xml"]
    ?.let { Regex("full-path=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
    ?: entries.keys.first { it.endsWith(".opf", ignoreCase = true) }
  val opf = parseOpf(entries.getValue(opfPath))
  val opfFolder = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
  val chapters = opf.spine.mapNotNull { href ->
    entries[resolvePath(opfFolder, href)]?.let(::htmlToText)
  }
  return EpubFile(title = opf.title, chapters = chapters.filter { it.isNotBlank() })
}

private val textExtensions = setOf("xml", "opf", "html", "htm", "xhtml")

private fun readTextEntries(input: InputStream): Map<String, String> {
  return buildMap {
    ZipInputStream(input).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        val extension = entry.name.substringAfterLast('.').lowercase()
        if (!entry.isDirectory && extension in textExtensions) {
          put(entry.name, zip.readBytes().decodeToString())
        }
      }
    }
  }
}

private class Opf(
  val title: String?,
  val spine: List<String>,
)

private fun parseOpf(xml: String): Opf {
  val parser = Xml.newPullParser()
  parser.setInput(xml.reader())
  var title: String? = null
  val hrefs = mutableMapOf<String, String>()
  val spine = mutableListOf<String>()
  while (parser.next() != XmlPullParser.END_DOCUMENT) {
    if (parser.eventType != XmlPullParser.START_TAG) continue
    when (parser.name.substringAfter(':')) {
      "title" -> if (title == null) title = parser.nextText().trim().takeIf { it.isNotEmpty() }
      "item" -> {
        val id = parser.getAttributeValue(null, "id")
        val href = parser.getAttributeValue(null, "href")
        if (id != null && href != null) hrefs[id] = href
      }
      "itemref" -> parser.getAttributeValue(null, "idref")?.let(spine::add)
    }
  }
  return Opf(title, spine.mapNotNull(hrefs::get))
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
