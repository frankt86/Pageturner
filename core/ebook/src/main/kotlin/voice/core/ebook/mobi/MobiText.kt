package voice.core.ebook.mobi

import java.io.ByteArrayOutputStream

/** The LZ77 variant of PalmDOC books. */
internal fun palmDocDecompress(data: ByteArray): ByteArray {
  var buffer = ByteArray(data.size * 2 + 16)
  var length = 0
  fun put(byte: Int) {
    if (length == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
    buffer[length++] = byte.toByte()
  }
  var i = 0
  while (i < data.size) {
    val c = data.u8(i++)
    when {
      c in 1..8 -> repeat(c) { if (i < data.size) put(data.u8(i++)) }
      c < 0x80 -> put(c)
      c >= 0xC0 -> {
        put(' '.code)
        put(c xor 0x80)
      }
      else -> {
        if (i >= data.size) break
        val pair = (c shl 8) or data.u8(i++)
        val distance = (pair shr 3) and 0x7FF
        val count = (pair and 7) + 3
        if (distance == 0 || distance > length) continue
        // the copy can overlap what it writes, so it goes byte by byte
        repeat(count) { put(buffer[length - distance].toInt()) }
      }
    }
  }
  return buffer.copyOf(length)
}

/** The Huffman and dictionary compression of larger MOBI books, from the HUFF record and the CDIC records after it. */
internal class HuffCdic(
  huff: ByteArray,
  cdics: List<ByteArray>,
) {

  private val codeLength = IntArray(256)
  private val terminal = BooleanArray(256)
  private val tableMax = LongArray(256)
  private val minCode = LongArray(33)
  private val maxCode = LongArray(33)
  private val phrases = mutableListOf<ByteArray>()
  private val unpacked = mutableListOf<Boolean>()

  init {
    if (huff.ascii(0, 4) != "HUFF" || huff.u32(4) != 0x18L) throw KindleFormatException("No HUFF record")
    val table1 = huff.u32(8).toInt()
    val table2 = huff.u32(12).toInt()
    for (i in 0 until 256) {
      val value = huff.u32(table1 + i * 4)
      val length = (value and 0x1F).toInt()
      codeLength[i] = length
      terminal[i] = value and 0x80 != 0L
      tableMax[i] = (((value ushr 8) + 1) shl (32 - length)) - 1
    }
    maxCode[0] = (1L shl 32) - 1
    for (length in 1..32) {
      minCode[length] = huff.u32(table2 + (length - 1) * 8) shl (32 - length)
      maxCode[length] = ((huff.u32(table2 + (length - 1) * 8 + 4) + 1) shl (32 - length)) - 1
    }
    cdics.forEach { cdic ->
      if (cdic.ascii(0, 4) != "CDIC" || cdic.u32(4) != 0x10L) throw KindleFormatException("No CDIC record")
      val total = cdic.u32(8).toInt()
      val bits = cdic.u32(12).toInt()
      val count = minOf(1 shl bits, total - phrases.size)
      for (j in 0 until count) {
        val offset = cdic.u16(16 + j * 2)
        val header = cdic.u16(16 + offset)
        val start = 18 + offset
        phrases += cdic.copyOfRange(start, start + (header and 0x7FFF))
        unpacked += header and 0x8000 != 0
      }
    }
  }

  fun decompress(data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(data.size * 3)
    unpack(data, out)
    return out.toByteArray()
  }

  private fun unpack(
    data: ByteArray,
    out: ByteArrayOutputStream,
  ) {
    var bitsLeft = data.size * 8L
    val padded = data.copyOf(data.size + 8)
    var position = 0
    var window = padded.u64(position)
    var n = 32
    while (true) {
      if (n <= 0) {
        position += 4
        window = padded.u64(position)
        n += 32
      }
      val code = (window ushr n) and 0xFFFFFFFFL
      val index = (code ushr 24).toInt()
      var length = codeLength[index]
      var max = tableMax[index]
      if (!terminal[index]) {
        while (length < 32 && code < minCode[length]) length++
        max = maxCode[length]
      }
      if (length == 0) throw KindleFormatException("Broken Huffman table")
      n -= length
      bitsLeft -= length
      if (bitsLeft < 0) break
      val phrase = ((max - code) ushr (32 - length)).toInt()
      if (phrase !in phrases.indices) throw KindleFormatException("Broken Huffman data")
      if (!unpacked[phrase]) {
        // a phrase is compressed itself, and is kept unpacked once it was used
        unpacked[phrase] = true
        phrases[phrase] = decompress(phrases[phrase])
      }
      out.write(phrases[phrase])
    }
  }
}

private fun ByteArray.u64(at: Int): Long = (u32(at) shl 32) or u32(at + 4)

/**
 * The size of the bytes at the end of a text record that aren't text, like the multibyte overlap or indexing data.
 * Each bit in [flags] above the lowest one adds an entry whose size is written backwards at the end.
 */
internal fun trailingSize(
  record: ByteArray,
  flags: Int,
): Int {
  var size = 0
  var test = flags ushr 1
  while (test != 0) {
    if (test and 1 != 0) size += trailingEntrySize(record, record.size - size)
    test = test ushr 1
  }
  if (flags and 1 != 0 && record.size - size - 1 >= 0) {
    size += (record[record.size - size - 1].toInt() and 3) + 1
  }
  return size.coerceAtMost(record.size)
}

private fun trailingEntrySize(
  record: ByteArray,
  end: Int,
): Int {
  var result = 0
  var shift = 0
  var position = end
  while (position > 0) {
    val value = record.u8(position - 1)
    result = result or ((value and 0x7F) shl shift)
    shift += 7
    position--
    if (value and 0x80 != 0 || shift >= 28) break
  }
  return result
}

/** The decompressed text of the book that [header] starts, with the trailing bytes of each record removed. */
internal fun readText(
  db: PalmDb,
  header: MobiHeader,
): ByteArray {
  if (header.encryption != 0) throw KindleDrmException()
  val huff = if (header.compression == Compression.HUFF_CDIC) {
    HuffCdic(
      huff = db.record(header.huffRecord),
      cdics = (1 until header.huffCount).map { db.record(header.huffRecord + it) },
    )
  } else {
    null
  }
  val out = ByteArrayOutputStream()
  for (i in 1..header.textRecordCount) {
    val record = db.record(header.start + i)
    val text = record.copyOf(record.size - trailingSize(record, header.extraFlags))
    out.write(
      when (header.compression) {
        Compression.NONE -> text
        Compression.PALM_DOC -> palmDocDecompress(text)
        Compression.HUFF_CDIC -> huff!!.decompress(text)
        else -> throw KindleFormatException("Unknown compression ${header.compression}")
      },
    )
  }
  return out.toByteArray()
}
