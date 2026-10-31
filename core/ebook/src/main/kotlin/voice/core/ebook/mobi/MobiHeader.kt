package voice.core.ebook.mobi

import java.io.IOException
import java.nio.charset.Charset

/** Thrown for a Kindle book that's encrypted. Pageturner never tries to remove DRM. */
class KindleDrmException : IOException("The book is protected with DRM")

/** Thrown for a file that isn't a Kindle book Pageturner can read, like Topaz or KFX. */
class KindleFormatException(message: String) : IOException(message)

internal const val NO_INDEX = -1

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

internal fun ByteArray.u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

internal fun ByteArray.u32(at: Int): Long {
  return (u8(at).toLong() shl 24) or (u8(at + 1).toLong() shl 16) or (u8(at + 2).toLong() shl 8) or u8(at + 3).toLong()
}

internal fun ByteArray.ascii(
  at: Int,
  length: Int,
): String {
  if (at < 0 || at + length > size) return ""
  return String(this, at, length, Charsets.ISO_8859_1)
}

/** The record list of a Palm database, which every MOBI, AZW and AZW3 file is. */
internal class PalmDb(private val bytes: ByteArray) {

  val name: String
  val type: String
  private val offsets: IntArray

  init {
    if (bytes.size < 78) throw KindleFormatException("Too short")
    name = bytes.ascii(0, 32).substringBefore('\u0000')
    type = bytes.ascii(60, 8)
    val count = bytes.u16(76)
    offsets = IntArray(count) { bytes.u32(78 + it * 8).toInt() }
  }

  val recordCount: Int get() = offsets.size

  fun record(index: Int): ByteArray {
    if (index !in offsets.indices) return ByteArray(0)
    val start = offsets[index].coerceIn(0, bytes.size)
    val end = (if (index + 1 < offsets.size) offsets[index + 1] else bytes.size).coerceIn(start, bytes.size)
    return bytes.copyOfRange(start, end)
  }
}

internal object Compression {
  const val NONE = 1
  const val PALM_DOC = 2
  const val HUFF_CDIC = 17480
}

/**
 * The headers in the first record of a book: the PalmDOC header, the MOBI header and the EXTH metadata. [start] is the
 * index of that record, which is not 0 for the KF8 part of a combined file. Record indices are made absolute with it.
 */
internal class MobiHeader(
  private val record: ByteArray,
  val start: Int,
) {

  val compression: Int = record.u16(0)
  val textRecordCount: Int = record.u16(8)
  val encryption: Int = record.u16(12)
  val hasMobiHeader: Boolean = record.ascii(16, 4) == "MOBI"
  private val headerLength: Int = if (hasMobiHeader) record.u32(20).toInt() else 0
  private val encoding: Int = field(28)?.toInt() ?: 1252
  val version: Int = field(36)?.toInt() ?: 0
  val firstResource: Int = recordIndex(108)
  val huffRecord: Int = recordIndex(112)
  val huffCount: Int = field(116)?.toInt() ?: 0
  private val exthFlags: Long = field(128) ?: 0

  /** Which kinds of trailing bytes follow the text in each text record. */
  val extraFlags: Int = if (headerLength >= 0xE4 && version >= 5) record.u16(0xF2) else 0
  val ncxIndex: Int = recordIndex(0xF4)
  val isKf8: Boolean = version >= 8
  val fdstRecord: Int = if (isKf8) recordIndex(0xC0) else NO_INDEX
  val fragmentIndex: Int = if (isKf8) recordIndex(0xF8) else NO_INDEX
  val skeletonIndex: Int = if (isKf8) recordIndex(0xFC) else NO_INDEX

  val charset: Charset = if (encoding == 65001) Charsets.UTF_8 else Charset.forName("windows-1252")

  val exth: Exth = if (hasMobiHeader && exthFlags and 0x40 != 0L) Exth.parse(record, 16 + headerLength) else Exth(emptyMap())

  val fullName: String? = run {
    val offset = field(84)?.toInt() ?: return@run null
    val length = field(88)?.toInt() ?: return@run null
    if (offset <= 0 || length <= 0 || offset + length > record.size) return@run null
    String(record, offset, length, charset).trim().takeIf { it.isNotEmpty() }
  }

  private fun field(at: Int): Long? {
    if (!hasMobiHeader || at + 4 > 16 + headerLength || at + 4 > record.size) return null
    return record.u32(at)
  }

  private fun recordIndex(at: Int): Int {
    val value = field(at) ?: return NO_INDEX
    if (value == 0xFFFFFFFFL) return NO_INDEX
    return value.toInt() + start
  }
}

/** The metadata records of a MOBI header, by their type. */
internal class Exth(private val records: Map<Int, List<ByteArray>>) {

  fun all(type: Int): List<ByteArray> = records[type].orEmpty()

  fun string(
    type: Int,
    charset: Charset,
  ): String? = all(type).firstOrNull()?.let { String(it, charset).trim() }?.takeIf { it.isNotEmpty() }

  fun int(type: Int): Int? = all(type).firstOrNull()?.takeIf { it.size >= 4 }?.u32(0)?.toInt()

  companion object {
    const val AUTHOR = 100
    const val ASIN = 113
    const val KF8_BOUNDARY = 121
    const val COVER_OFFSET = 201
    const val THUMBNAIL_OFFSET = 202
    const val SECOND_ASIN = 504
    const val UPDATED_TITLE = 503
    const val LANGUAGE = 524

    fun parse(
      record: ByteArray,
      at: Int,
    ): Exth {
      if (record.ascii(at, 4) != "EXTH") return Exth(emptyMap())
      val count = record.u32(at + 8).toInt()
      val records = mutableMapOf<Int, MutableList<ByteArray>>()
      var offset = at + 12
      repeat(count) {
        if (offset + 8 > record.size) return@repeat
        val type = record.u32(offset).toInt()
        val length = record.u32(offset + 4).toInt()
        if (length < 8 || offset + length > record.size) return Exth(records)
        records.getOrPut(type) { mutableListOf() } += record.copyOfRange(offset + 8, offset + length)
        offset += length
      }
      return Exth(records)
    }
  }
}

internal class KindleMetadataFields(
  val title: String?,
  val author: String?,
  val language: String?,
  val identifier: String?,
  /** Counted from the first resource, null without a cover. */
  val coverOffset: Int?,
)

internal fun MobiHeader.metadata(dbName: String): KindleMetadataFields {
  val authors = exth.all(Exth.AUTHOR).map { String(it, charset).trim() }.filter { it.isNotEmpty() }.distinct()
  return KindleMetadataFields(
    title = exth.string(Exth.UPDATED_TITLE, charset) ?: fullName ?: dbName.replace('_', ' ').trim().takeIf { it.isNotEmpty() },
    author = authors.joinToString(", ").ifEmpty { null },
    language = exth.string(Exth.LANGUAGE, charset),
    identifier = exth.string(Exth.ASIN, charset) ?: exth.string(Exth.SECOND_ASIN, charset),
    coverOffset = (exth.int(Exth.COVER_OFFSET) ?: exth.int(Exth.THUMBNAIL_OFFSET))?.takeIf { it >= 0 },
  )
}
