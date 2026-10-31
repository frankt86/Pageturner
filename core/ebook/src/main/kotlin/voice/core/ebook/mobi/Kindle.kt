package voice.core.ebook.mobi

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.InflaterInputStream

class KindleMetadata(
  val title: String?,
  val author: String?,
  val cover: ByteArray?,
  val drmProtected: Boolean,
)

/**
 * Converts a MOBI, AZW or AZW3 book into an EPUB. A file with both a MOBI 6 and a KF8 part is converted from the KF8
 * part, which keeps more of the book's layout.
 */
fun convertKindleToEpub(
  book: ByteArray,
  output: OutputStream,
) {
  val db = PalmDb(book)
  checkType(db.type)
  val first = MobiHeader(db.record(0), start = 0)
  if (first.encryption != 0) throw KindleDrmException()
  // the KF8 part of a combined file starts at the record the boundary names, right after a BOUNDARY record
  val boundary = first.exth.int(Exth.KF8_BOUNDARY)?.takeIf { !first.isKf8 && it > 0 && it < db.recordCount }
  val kf8 = boundary?.let { MobiHeader(db.record(it), start = it) }?.takeIf { it.isKf8 }
  val header = kf8 ?: first
  if (header.encryption != 0) throw KindleDrmException()

  val fields = header.metadata(db.name)
  val fallback = first.metadata(db.name)
  val title = fields.title ?: fallback.title ?: "Book"
  var resources = Resources.read(db, header.firstResource, end = if (kf8 == null && boundary != null) boundary else db.recordCount)
  // the parts of a combined file share the resources before the boundary
  if (kf8 != null && resources.isEmpty()) {
    resources = Resources.read(db, first.firstResource, end = boundary)
  }
  val text = if (header.isKf8) kf8Content(db, header, resources) else mobi6Content(db, header, title, resources)
  val cover = (fields.coverOffset ?: fallback.coverOffset)
    ?.let { resources.byNumber(it + 1) }
    ?.takeIf { it.mediaType.startsWith("image/") }
  writeEpub(
    EpubContent(
      title = title,
      author = fields.author ?: fallback.author,
      language = fields.language ?: fallback.language,
      identifier = fields.identifier ?: fallback.identifier,
      documents = text.documents,
      resources = resources.all + text.resources,
      coverPath = cover?.path,
      toc = text.toc,
    ),
    output,
  )
}

/** The title, author and cover from the headers, read without converting or even loading the whole book. */
fun readKindleMetadata(
  input: InputStream,
  withCover: Boolean = true,
): KindleMetadata {
  val stream = DataInputStream(BufferedInputStream(input))
  val head = ByteArray(78)
  stream.readFully(head)
  checkType(head.ascii(60, 8))
  val count = head.u16(76)
  val table = ByteArray(count * 8)
  stream.readFully(table)
  val offsets = IntArray(count) { table.u32(it * 8).toInt() }
  var position = head.size + table.size

  fun read(index: Int): ByteArray {
    val start = offsets[index]
    if (start < position) throw KindleFormatException("Records out of order")
    var toSkip = start - position
    while (toSkip > 0) {
      val skipped = stream.skipBytes(toSkip)
      if (skipped <= 0) throw EOFException()
      toSkip -= skipped
    }
    val record = if (index + 1 < count) {
      ByteArray(offsets[index + 1] - start).also { stream.readFully(it) }
    } else {
      stream.readBytes()
    }
    position = start + record.size
    return record
  }

  val header = MobiHeader(read(0), start = 0)
  val name = head.ascii(0, 32).substringBefore('\u0000')
  val fields = header.metadata(name)
  val cover = fields.coverOffset
    ?.takeIf { withCover && header.firstResource != NO_INDEX }
    ?.let { header.firstResource + it }
    ?.takeIf { it in 1 until count }
    ?.let(::read)
    ?.takeIf { resourceType(it) != null }
  return KindleMetadata(
    title = fields.title,
    author = fields.author,
    cover = cover,
    drmProtected = header.encryption != 0,
  )
}

private fun checkType(type: String) {
  // Topaz and KFX books are other formats under the same names
  if (type != "BOOKMOBI" && type != "TEXtREAd") throw KindleFormatException("Not a MOBI book")
}

/** The images and fonts of a book, by their number counted from 1, as recindex and kindle:embed links count them. */
internal class Resources(private val files: Map<Int, EpubFile>) {

  fun byNumber(number: Int): EpubFile? = files[number]

  fun isEmpty(): Boolean = files.isEmpty()

  val all: List<EpubFile> get() = files.values.toList()

  companion object {
    fun read(
      db: PalmDb,
      first: Int,
      end: Int,
    ): Resources {
      if (first == NO_INDEX) return Resources(emptyMap())
      val files = mutableMapOf<Int, EpubFile>()
      for (index in first until end.coerceAtMost(db.recordCount)) {
        val number = index - first + 1
        val data = db.record(index)
        val name = number.toString().padStart(5, '0')
        if (data.ascii(0, 4) == "FONT") {
          font(data)?.let { (extension, bytes) -> files[number] = EpubFile("fonts/font$name.$extension", "font/$extension", bytes) }
          continue
        }
        val (extension, type) = resourceType(data) ?: continue
        files[number] = EpubFile("images/image$name.$extension", type, data)
      }
      return Resources(files)
    }
  }
}

/** A font record, compressed and with its start obfuscated, as fonts are in KF8 books. */
private fun font(data: ByteArray): Pair<String, ByteArray>? {
  if (data.size < 24) return null
  val flags = data.u32(8).toInt()
  val start = data.u32(12).toInt()
  val keyLength = data.u32(16).toInt()
  val keyStart = data.u32(20).toInt()
  if (start !in 0..data.size) return null
  var font = data.copyOfRange(start, data.size)
  if (flags and 2 != 0 && keyLength > 0 && keyStart + keyLength <= data.size) {
    for (n in 0 until minOf(font.size, 1040)) {
      font[n] = (font[n].toInt() xor data[keyStart + n % keyLength].toInt()).toByte()
    }
  }
  if (flags and 1 != 0) {
    font = InflaterInputStream(font.inputStream()).use { it.readBytes() }
  }
  val magic = font.ascii(0, 4)
  return when {
    magic == "\u0000\u0001\u0000\u0000" || magic == "true" || magic == "ttcf" -> "ttf" to font
    magic == "OTTO" -> "otf" to font
    else -> null
  }
}
