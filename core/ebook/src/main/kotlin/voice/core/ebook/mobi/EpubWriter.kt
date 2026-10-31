package voice.core.ebook.mobi

import java.io.OutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A file in the EPUB, with its [path] inside the OEBPS folder. */
internal class EpubFile(
  val path: String,
  val mediaType: String,
  val data: ByteArray,
)

internal class TocEntry(
  val title: String,
  /** Relative to the OEBPS folder. */
  val href: String,
  val depth: Int,
)

internal class EpubContent(
  val title: String,
  val author: String?,
  val language: String?,
  val identifier: String?,
  /** The XHTML files, in reading order. */
  val documents: List<EpubFile>,
  /** Style sheets, images and fonts. */
  val resources: List<EpubFile>,
  val coverPath: String?,
  val toc: List<TocEntry>,
)

internal fun writeEpub(
  content: EpubContent,
  output: OutputStream,
) {
  val identifier = content.identifier
    ?: "urn:uuid:" + UUID.nameUUIDFromBytes((content.title + content.author).toByteArray())
  val toc = content.toc.ifEmpty {
    content.documents.take(1).map { TocEntry(content.title, it.path, 0) }
  }
  ZipOutputStream(output).use { zip ->
    // the mimetype comes first and uncompressed, so the file can be told apart by its first bytes
    val mimetype = "application/epub+zip".toByteArray()
    zip.putNextEntry(
      ZipEntry("mimetype").apply {
        method = ZipEntry.STORED
        size = mimetype.size.toLong()
        compressedSize = mimetype.size.toLong()
        crc = CRC32().apply { update(mimetype) }.value
      },
    )
    zip.write(mimetype)
    zip.closeEntry()
    fun put(
      name: String,
      data: ByteArray,
    ) {
      zip.putNextEntry(ZipEntry(name))
      zip.write(data)
      zip.closeEntry()
    }
    put("META-INF/container.xml", CONTAINER.toByteArray())
    put("OEBPS/content.opf", opf(content, identifier).toByteArray())
    put("OEBPS/nav.xhtml", nav(content.title, toc).toByteArray())
    put("OEBPS/toc.ncx", ncx(content.title, identifier, toc).toByteArray())
    (content.documents + content.resources).forEach { put("OEBPS/${it.path}", it.data) }
  }
}

private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

private fun opf(
  content: EpubContent,
  identifier: String,
): String = buildString {
  append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
  append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">""").append('\n')
  append("""<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""").append('\n')
  append("""<dc:identifier id="book-id">""").append(xmlEscape(identifier)).append("</dc:identifier>\n")
  append("<dc:title>").append(xmlEscape(content.title)).append("</dc:title>\n")
  content.author?.let { append("<dc:creator>").append(xmlEscape(it)).append("</dc:creator>\n") }
  append("<dc:language>").append(xmlEscape(content.language ?: "und")).append("</dc:language>\n")
  append("""<meta property="dcterms:modified">2000-01-01T00:00:00Z</meta>""").append('\n')
  val ids = (content.documents + content.resources).withIndex().associate { (index, file) -> file.path to "item$index" }
  content.coverPath?.let { ids[it] }?.let { append("""<meta name="cover" content="$it"/>""").append('\n') }
  append("</metadata>\n<manifest>\n")
  append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""").append('\n')
  append("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""").append('\n')
  (content.documents + content.resources).forEach { file ->
    append("""<item id="${ids[file.path]}" href="${xmlEscape(file.path)}" media-type="${file.mediaType}"""")
    if (file.path == content.coverPath) append(""" properties="cover-image"""")
    append("/>\n")
  }
  append("</manifest>\n<spine toc=\"ncx\">\n")
  content.documents.forEach { append("""<itemref idref="${ids[it.path]}"/>""").append('\n') }
  append("</spine>\n</package>\n")
}

private fun nav(
  title: String,
  toc: List<TocEntry>,
): String = buildString {
  append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
  append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">""").append('\n')
  append("<head><title>").append(xmlEscape(title)).append("</title></head>\n<body>\n")
  append("""<nav epub:type="toc" id="toc"><ol>""").append('\n')
  var depth = 0
  toc.forEachIndexed { index, entry ->
    val entryDepth = if (index == 0) 0 else entry.depth.coerceIn(0, depth + 1)
    if (index > 0) {
      if (entryDepth > depth) {
        append("<ol>")
      } else {
        append("</li>")
        repeat(depth - entryDepth) { append("</ol></li>") }
      }
    }
    append("""<li><a href="${xmlEscape(entry.href)}">""").append(xmlEscape(entry.title)).append("</a>")
    depth = entryDepth
  }
  if (toc.isNotEmpty()) {
    append("</li>")
    repeat(depth) { append("</ol></li>") }
  }
  append("\n</ol></nav>\n</body>\n</html>\n")
}

private fun ncx(
  title: String,
  identifier: String,
  toc: List<TocEntry>,
): String = buildString {
  append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
  append("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">""").append('\n')
  append("""<head><meta name="dtb:uid" content="${xmlEscape(identifier)}"/></head>""").append('\n')
  append("<docTitle><text>").append(xmlEscape(title)).append("</text></docTitle>\n<navMap>\n")
  var depth = 0
  toc.forEachIndexed { index, entry ->
    val entryDepth = if (index == 0) 0 else entry.depth.coerceIn(0, depth + 1)
    if (index > 0 && entryDepth <= depth) {
      repeat(depth - entryDepth + 1) { append("</navPoint>") }
      append('\n')
    }
    append("""<navPoint id="nav${index + 1}" playOrder="${index + 1}">""")
    append("<navLabel><text>").append(xmlEscape(entry.title)).append("</text></navLabel>")
    append("""<content src="${xmlEscape(entry.href)}"/>""")
    depth = entryDepth
  }
  if (toc.isNotEmpty()) repeat(depth + 1) { append("</navPoint>") }
  append("\n</navMap>\n</ncx>\n")
}

internal fun xmlEscape(text: String): String = buildString {
  text.forEach { char ->
    when (char) {
      '&' -> append("&amp;")
      '<' -> append("&lt;")
      '>' -> append("&gt;")
      '"' -> append("&quot;")
      else -> if (char >= ' ' || char == '\n' || char == '\t') append(char)
    }
  }
}

/** The page around the body of a converted chapter. */
internal fun xhtmlPage(
  title: String,
  body: String,
): String = buildString {
  append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
  append("<!DOCTYPE html>\n")
  append("""<html xmlns="http://www.w3.org/1999/xhtml">""").append('\n')
  append("<head><title>").append(xmlEscape(title)).append("</title></head>\n")
  append("<body>").append(body).append("</body>\n</html>\n")
}

/** The type of an image or font record by its first bytes, or null when it's neither. */
internal fun resourceType(data: ByteArray): Pair<String, String>? {
  fun startsWith(vararg bytes: Int) = data.size >= bytes.size && bytes.indices.all { data.u8(it) == bytes[it] }
  return when {
    startsWith(0xFF, 0xD8, 0xFF) -> "jpg" to "image/jpeg"
    startsWith(0x89, 'P'.code, 'N'.code, 'G'.code) -> "png" to "image/png"
    startsWith('G'.code, 'I'.code, 'F'.code, '8'.code) -> "gif" to "image/gif"
    startsWith('B'.code, 'M'.code) && data.size > 26 -> "bmp" to "image/bmp"
    else -> null
  }
}
