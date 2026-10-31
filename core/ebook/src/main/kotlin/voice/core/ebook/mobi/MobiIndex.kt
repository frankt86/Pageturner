package voice.core.ebook.mobi

/** An entry of an INDX index: its name and the values of its tags. */
internal class IndexEntry(
  val name: ByteArray,
  val tags: Map<Int, List<Int>>,
) {
  fun tag(
    tag: Int,
    position: Int = 0,
  ): Int? = tags[tag]?.getOrNull(position)
}

internal class MobiIndex(
  val entries: List<IndexEntry>,
  /** The strings the entries point to, by their offset. */
  val strings: Map<Int, ByteArray>,
)

private class TagDefinition(
  val tag: Int,
  val valuesPerEntry: Int,
  val mask: Int,
  val endFlag: Int,
)

/** Reads the index whose main INDX record is [index], like the skeleton, fragment and NCX tables. */
internal fun readIndex(
  db: PalmDb,
  index: Int,
): MobiIndex {
  if (index == NO_INDEX) return MobiIndex(emptyList(), emptyMap())
  val main = db.record(index)
  if (main.ascii(0, 4) != "INDX") return MobiIndex(emptyList(), emptyMap())
  val headerLength = main.u32(4).toInt()
  val recordCount = main.u32(24).toInt()
  val stringRecords = main.u32(52).toInt()

  val strings = mutableMapOf<Int, ByteArray>()
  for (j in 0 until stringRecords) {
    readStrings(db.record(index + recordCount + 1 + j)).forEach { (offset, text) -> strings[offset + j * 0x10000] = text }
  }

  var controlByteCount = 0
  val definitions = mutableListOf<TagDefinition>()
  if (main.ascii(headerLength, 4) == "TAGX") {
    val firstEntry = main.u32(headerLength + 4).toInt()
    controlByteCount = main.u32(headerLength + 8).toInt()
    for (offset in 12 until firstEntry step 4) {
      val at = headerLength + offset
      definitions += TagDefinition(main.u8(at), main.u8(at + 1), main.u8(at + 2), main.u8(at + 3))
    }
  }

  val entries = mutableListOf<IndexEntry>()
  for (i in index + 1..index + recordCount) {
    val record = db.record(i)
    if (record.ascii(0, 4) != "INDX") continue
    val idxt = record.u32(20).toInt()
    val count = record.u32(24).toInt()
    val starts = List(count) { record.u16(idxt + 4 + it * 2) } + idxt
    for (j in 0 until count) {
      val start = starts[j]
      val nameLength = record.u8(start)
      val name = record.copyOfRange(start + 1, start + 1 + nameLength)
      val tags = readTags(record, start + 1 + nameLength, controlByteCount, definitions)
      entries += IndexEntry(name, tags)
    }
  }
  return MobiIndex(entries, strings)
}

private fun readStrings(record: ByteArray): List<Pair<Int, ByteArray>> {
  val strings = mutableListOf<Pair<Int, ByteArray>>()
  var offset = 0
  while (offset < record.size) {
    if (record[offset].toInt() == 0) break
    val start = offset
    val (consumed, length) = variableWidthValue(record, offset)
    offset += consumed
    if (offset + length > record.size) break
    strings += start to record.copyOfRange(offset, offset + length)
    offset += length
  }
  return strings
}

/** A number written in 7 bit groups, the last one marked by its high bit. Returns the bytes read and the number. */
internal fun variableWidthValue(
  data: ByteArray,
  offset: Int,
): Pair<Int, Int> {
  var value = 0
  var consumed = 0
  while (offset + consumed < data.size) {
    val byte = data.u8(offset + consumed)
    consumed++
    value = (value shl 7) or (byte and 0x7F)
    if (byte and 0x80 != 0) break
  }
  return consumed to value
}

private fun readTags(
  data: ByteArray,
  start: Int,
  controlByteCount: Int,
  definitions: List<TagDefinition>,
): Map<Int, List<Int>> {
  class Found(
    val tag: Int,
    val valueCount: Int?,
    val valueBytes: Int?,
    val valuesPerEntry: Int,
  )
  val found = mutableListOf<Found>()
  var controlByte = 0
  var position = start + controlByteCount
  for (definition in definitions) {
    if (definition.endFlag == 1) {
      controlByte++
      continue
    }
    var mask = definition.mask
    var value = data.u8(start + controlByte) and mask
    if (value == 0) continue
    if (value == mask) {
      if (Integer.bitCount(mask) > 1) {
        // all bits set means the number of bytes of the values follows
        val (consumed, bytes) = variableWidthValue(data, position)
        position += consumed
        found += Found(definition.tag, null, bytes, definition.valuesPerEntry)
      } else {
        found += Found(definition.tag, 1, null, definition.valuesPerEntry)
      }
    } else {
      while (mask and 1 == 0) {
        mask = mask ushr 1
        value = value ushr 1
      }
      found += Found(definition.tag, value, null, definition.valuesPerEntry)
    }
  }
  return buildMap {
    found.forEach { tag ->
      val values = mutableListOf<Int>()
      if (tag.valueCount != null) {
        repeat(tag.valueCount * tag.valuesPerEntry) {
          val (consumed, value) = variableWidthValue(data, position)
          position += consumed
          values += value
        }
      } else {
        var consumedTotal = 0
        while (consumedTotal < tag.valueBytes!!) {
          val (consumed, value) = variableWidthValue(data, position)
          if (consumed == 0) break
          position += consumed
          consumedTotal += consumed
          values += value
        }
      }
      put(tag.tag, values)
    }
  }
}
