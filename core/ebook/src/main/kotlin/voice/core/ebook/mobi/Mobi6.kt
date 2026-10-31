package voice.core.ebook.mobi

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import java.util.TreeSet

private val fileposLink = Regex("""<[^<>]+filepos=['"]?0*(\d+)""", RegexOption.IGNORE_CASE)
private val fileposAttribute = Regex("""\sfilepos=['"]?0*(\d+)['"]?""", RegexOption.IGNORE_CASE)
private val recindexAttribute = Regex("""\s(?:hi|lo)?recindex=['"]?0*(\d+)['"]?""", RegexOption.IGNORE_CASE)
private val pageBreak = Regex("""<mbp:pagebreak[^>]*>""", RegexOption.IGNORE_CASE)
private val bodyStart = Regex("""<body[^>]*>""", RegexOption.IGNORE_CASE)
private val bodyEnd = Regex("""</body\s*>""", RegexOption.IGNORE_CASE)
private val headEnd = Regex("""</head\s*>""", RegexOption.IGNORE_CASE)

/**
 * A MOBI 6 book: one HTML text where links point at byte positions (filepos) and images at their record (recindex).
 * Anchors go in at the linked positions, and the text is split into chapters at its page breaks.
 */
internal fun mobi6Content(
  db: PalmDb,
  header: MobiHeader,
  title: String,
  resources: Resources,
): ConvertedText {
  val raw = readText(db, header)
  if (!header.hasMobiHeader) return plainText(raw, title)
  // byte positions stay character positions in ISO-8859-1, whatever the book's encoding
  var text = String(raw, Charsets.ISO_8859_1)

  val ncxIndex = readIndex(db, header.ncxIndex)
  val ncx = ncxIndex.entries.mapNotNull { entry ->
    val position = entry.tag(1) ?: return@mapNotNull null
    val label = entry.tag(3)?.let { ncxIndex.strings[it] }
    Triple(position, label?.let { String(it, header.charset).trim() }, entry.tag(4) ?: 0)
  }
  val positions = TreeSet<Int>()
  fileposLink.findAll(text).forEach { positions += it.groupValues[1].toInt() }
  ncx.forEach { positions += it.first }

  text = buildString {
    var copied = 0
    positions.filter { it in 1..text.length }.forEach { position ->
      // a position inside a tag gets its anchor before the tag
      val lastOpen = text.lastIndexOf('<', position - 1)
      val lastClose = text.lastIndexOf('>', position - 1)
      val at = if (lastOpen > lastClose) lastOpen else position
      if (at < copied) return@forEach
      append(text, copied, at)
      append("""<a id="filepos$position"></a>""")
      copied = at
    }
    append(text, copied, text.length)
  }
  if (0 in positions) text = """<a id="filepos0"></a>""" + text
  text = text.replace(fileposAttribute) { """ href="#filepos${it.groupValues[1]}"""" }
  text = text.replace(recindexAttribute) { match ->
    val path = resources.byNumber(match.groupValues[1].toInt())?.path
    if (path == null) "" else """ src="../$path""""
  }

  val start = bodyStart.find(text)?.range?.last?.plus(1) ?: headEnd.find(text)?.range?.last?.plus(1) ?: 0
  val end = bodyEnd.findAll(text).lastOrNull()?.range?.first?.takeIf { it >= start } ?: text.length
  val body = String(text.substring(start, end).toByteArray(Charsets.ISO_8859_1), header.charset)

  val chunks = mutableListOf<String>()
  var pending = ""
  body.split(pageBreak).forEach { chunk ->
    val joined = pending + chunk
    // a page break right after another would make an empty chapter
    if (hasContent(joined)) {
      chunks += joined
      pending = ""
    } else {
      pending = joined
    }
  }
  if (pending.isNotBlank()) {
    if (chunks.isEmpty()) chunks += pending else chunks[chunks.lastIndex] = chunks.last() + pending
  }

  val documents = chunks.map { Jsoup.parseBodyFragment(it) }
  val anchorFile = mutableMapOf<String, String>()
  documents.forEachIndexed { index, document ->
    document.body().select("[id^=filepos]").forEach { anchorFile[it.id()] = chapterPath(index) }
  }
  val files = documents.mapIndexed { index, document ->
    document.body().select("a[href^=#filepos]").forEach { link ->
      val id = link.attr("href").removePrefix("#")
      anchorFile[id]?.let { link.attr("href", "${it.substringAfterLast('/')}#$id") }
    }
    EpubFile(chapterPath(index), XHTML, xhtmlPage(title, cleanBody(document)).toByteArray())
  }

  val toc = ncx.mapNotNull { (position, label, depth) ->
    val id = "filepos$position"
    val file = anchorFile[id] ?: return@mapNotNull null
    TocEntry(label?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null, "$file#$id", depth)
  }.ifEmpty {
    documents.mapIndexedNotNull { index, document ->
      val heading = document.body().selectFirst("h1, h2, h3")?.text()?.trim()?.takeIf { it.isNotEmpty() }
      heading?.let { TocEntry(it, chapterPath(index), 0) }
    }
  }
  return ConvertedText(files, emptyList(), toc)
}

private fun hasContent(html: String): Boolean {
  if (html.contains("<img", ignoreCase = true)) return true
  return html.replace(Regex("<[^>]*>"), "").replace("&nbsp;", "").isNotBlank()
}

/** A PalmDOC book without a MOBI header is plain text. */
private fun plainText(
  raw: ByteArray,
  title: String,
): ConvertedText {
  val text = String(raw, charset("windows-1252"))
  val body = text.split(Regex("\r?\n\\s*\r?\n|\r?\n"))
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n") { "<p>${xmlEscape(it)}</p>" }
  return ConvertedText(listOf(EpubFile(chapterPath(0), XHTML, xhtmlPage(title, body).toByteArray())), emptyList(), emptyList())
}

/** The body as XHTML, without the tags and attributes of the Mobipocket namespace, which XML readers would refuse. */
private fun cleanBody(document: Document): String {
  document.outputSettings()
    .syntax(Document.OutputSettings.Syntax.xml)
    .escapeMode(Entities.EscapeMode.xhtml)
    .charset(Charsets.UTF_8)
    .prettyPrint(false)
  document.body().select("*").forEach { element ->
    if (':' in element.tagName()) {
      element.unwrap()
      return@forEach
    }
    element.attributes()
      .map { it.key }
      .filter { ':' in it && it != "xml:lang" }
      .forEach { element.removeAttr(it) }
  }
  return document.body().html()
}

internal const val XHTML = "application/xhtml+xml"

internal fun chapterPath(index: Int): String = "text/part${index.toString().padStart(4, '0')}.xhtml"
