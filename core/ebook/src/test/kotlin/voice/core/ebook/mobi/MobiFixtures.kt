package voice.core.ebook.mobi

import java.io.ByteArrayOutputStream

internal val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

internal class Bytes {
  private val out = ByteArrayOutputStream()
  val size: Int get() = out.size()

  fun u8(value: Int) {
    out.write(value)
  }

  fun u16(value: Int) {
    u8(value ushr 8 and 0xFF)
    u8(value and 0xFF)
  }

  fun u32(value: Long) {
    u16((value ushr 16).toInt() and 0xFFFF)
    u16(value.toInt() and 0xFFFF)
  }

  fun bytes(value: ByteArray) {
    out.write(value)
  }

  fun text(value: String) {
    bytes(value.toByteArray())
  }

  fun pad(until: Int) {
    while (size < until) u8(0)
  }

  fun build(): ByteArray = out.toByteArray()
}

internal fun bytes(write: Bytes.() -> Unit): ByteArray = Bytes().apply(write).build()

internal fun palmDb(
  records: List<ByteArray>,
  name: String = "Test_Book",
  type: String = "BOOKMOBI",
): ByteArray = bytes {
  text(name.take(31))
  pad(60)
  text(type)
  pad(76)
  u16(records.size)
  var offset = 78 + records.size * 8L + 2
  records.forEachIndexed { index, record ->
    u32(offset)
    u32(index.toLong() * 2)
    offset += record.size
  }
  u16(0)
  records.forEach { bytes(it) }
}

internal const val NULL = 0xFFFFFFFFL

internal fun record0(
  textLength: Int,
  textRecords: Int,
  compression: Int = Compression.NONE,
  encryption: Int = 0,
  version: Int = 6,
  fields: Map<Int, Long> = emptyMap(),
  exth: List<Pair<Int, ByteArray>> = emptyList(),
  fullName: String = "Full Name",
): ByteArray {
  val headerLength = 264
  val exthBytes = bytes {
    if (exth.isNotEmpty()) {
      text("EXTH")
      u32(12L + exth.sumOf { 8 + it.second.size })
      u32(exth.size.toLong())
      exth.forEach { (type, data) ->
        u32(type.toLong())
        u32(8L + data.size)
        bytes(data)
      }
    }
  }
  val nameOffset = 16 + headerLength + exthBytes.size
  val values = mapOf(
    28 to 65001L,
    36 to version.toLong(),
    108 to NULL,
    112 to NULL,
    116 to 0L,
    0xC0 to NULL,
    0xF4 to NULL,
    0xF8 to NULL,
    0xFC to NULL,
    0x104 to NULL,
  ) + fields + mapOf(
    84 to nameOffset.toLong(),
    88 to fullName.toByteArray().size.toLong(),
    128 to if (exth.isEmpty()) 0L else 0x40L,
  )
  return bytes {
    u16(compression)
    u16(0)
    u32(textLength.toLong())
    u16(textRecords)
    u16(4096)
    u16(encryption)
    u16(0)
    text("MOBI")
    u32(headerLength.toLong())
    var at = 24
    while (at < 16 + headerLength) {
      if (at == 0xF0) {
        u16(0)
        u16(fields[0xF2]?.toInt() ?: 0)
      } else {
        u32(values[at] ?: 0L)
      }
      at += 4
    }
    bytes(exthBytes)
    text(fullName)
    pad(nameOffset + fullName.length + 4)
  }
}

internal fun string(value: String) = value.toByteArray()

internal fun int(value: Int) = bytes { u32(value.toLong()) }

internal fun vwi(value: Int): ByteArray {
  val groups = mutableListOf<Int>()
  var rest = value
  do {
    groups.add(0, rest and 0x7F)
    rest = rest ushr 7
  } while (rest != 0)
  groups[groups.lastIndex] = groups.last() or 0x80
  return ByteArray(groups.size) { groups[it].toByte() }
}

/** A tag definition: tag, values per entry, mask. */
internal class Tag(
  val tag: Int,
  val values: Int,
  val mask: Int,
)

/** The main and the entry record of an index with one control byte, plus its string record if there are [strings]. */
internal fun index(
  tags: List<Tag>,
  entries: List<Pair<String, ByteArray>>,
  strings: ByteArray? = null,
): List<ByteArray> {
  val headerLength = 0xC0
  val main = bytes {
    text("INDX")
    u32(headerLength.toLong())
    pad(24)
    u32(1)
    pad(52)
    u32(if (strings == null) 0 else 1)
    pad(headerLength)
    text("TAGX")
    u32(12L + (tags.size + 1) * 4)
    u32(1)
    tags.forEach {
      u8(it.tag)
      u8(it.values)
      u8(it.mask)
      u8(0)
    }
    u32(1)
  }
  val starts = mutableListOf<Int>()
  var idxt = 0
  val sub = bytes {
    text("INDX")
    u32(headerLength.toLong())
    pad(headerLength)
    entries.forEach { (name, data) ->
      starts += size
      u8(name.length)
      text(name)
      bytes(data)
    }
    idxt = size
    text("IDXT")
    starts.forEach { u16(it) }
  }
  bytes {
    u32(idxt.toLong())
    u32(entries.size.toLong())
  }.copyInto(sub, destinationOffset = 20)
  return listOfNotNull(main, sub, strings)
}

internal fun entry(
  control: Int,
  vararg values: Int,
): ByteArray = bytes {
  u8(control)
  values.forEach { bytes(vwi(it)) }
}
