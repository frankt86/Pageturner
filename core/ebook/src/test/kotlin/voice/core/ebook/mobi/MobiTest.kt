package voice.core.ebook.mobi

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobiTest {

  @Test
  fun `palmdoc copies literals, spaces and back references`() {
    val data = byteArrayOf(0x03, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte(), 0xE5.toByte(), 0x80.toByte(), 0x28)
    assertEquals("abcd ebcd", palmDocDecompress(data).decodeToString())
  }

  @Test
  fun `palmdoc back references can overlap what they copy`() {
    val data = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 0x80.toByte(), 0x12)
    assertEquals("abababa", palmDocDecompress(data).decodeToString())
  }

  @Test
  fun `huffman codes pick phrases from the dictionary`() {
    val huff = bytes {
      text("HUFF")
      u32(0x18)
      u32(24)
      u32(24 + 1024)
      pad(24)
      // every byte is a terminal code of 8 bits that picks the phrase 255 minus its value
      repeat(256) { u32((255L shl 8) or 0x80 or 8) }
      repeat(64) { u32(0) }
    }
    val phrases = listOf("Hello ", "world")
    val cdic = bytes {
      text("CDIC")
      u32(0x10)
      u32(phrases.size.toLong())
      u32(8)
      var offset = phrases.size * 2
      phrases.forEach {
        u16(offset)
        offset += 2 + it.length
      }
      phrases.forEach {
        u16(0x8000 or it.length)
        text(it)
      }
    }
    val reader = HuffCdic(huff, listOf(cdic))
    assertEquals("Hello world", reader.decompress(byteArrayOf(255.toByte(), 254.toByte())).decodeToString())
  }

  @Test
  fun `trailing entries and multibyte bytes are cut off the text`() {
    val record = "text".toByteArray() + byteArrayOf(0x01, 0x00, 0x00, 0x83.toByte())
    assertEquals(3, trailingSize(record, flags = 0b10))
    assertEquals(5, trailingSize(record, flags = 0b11))
  }

  @Test
  fun `headers and exth metadata are read`() {
    val record = record0(
      textLength = 10,
      textRecords = 1,
      compression = Compression.PALM_DOC,
      fields = mapOf(108 to 5L, 0xF2 to 3L),
      exth = listOf(
        Exth.AUTHOR to string("Mara Quill"),
        Exth.AUTHOR to string("Ada Lin"),
        Exth.UPDATED_TITLE to string("The Quiet Orbit"),
        Exth.ASIN to string("B00TEST"),
        Exth.COVER_OFFSET to int(2),
        Exth.LANGUAGE to string("en"),
      ),
    )
    val header = MobiHeader(record, start = 0)
    assertEquals(Compression.PALM_DOC, header.compression)
    assertEquals(1, header.textRecordCount)
    assertEquals(6, header.version)
    assertEquals(5, header.firstResource)
    assertEquals(3, header.extraFlags)
    assertEquals(NO_INDEX, header.ncxIndex)
    assertFalse(header.isKf8)
    assertEquals("Full Name", header.fullName)
    val metadata = header.metadata("Test_Book")
    assertEquals("The Quiet Orbit", metadata.title)
    assertEquals("Mara Quill, Ada Lin", metadata.author)
    assertEquals("B00TEST", metadata.identifier)
    assertEquals(2, metadata.coverOffset)
    assertEquals("en", metadata.language)
  }

  @Test
  fun `the full name is the title without an updated title`() {
    val metadata = MobiHeader(record0(textLength = 0, textRecords = 0), start = 0).metadata("Test_Book")
    assertEquals("Full Name", metadata.title)
    assertNull(metadata.author)
  }

  @Test
  fun `record indices of a kf8 header count from its own first record`() {
    val header = MobiHeader(record0(textLength = 0, textRecords = 0, version = 8, fields = mapOf(108 to 4L, 0xC0 to 2L)), start = 10)
    assertTrue(header.isKf8)
    assertEquals(14, header.firstResource)
    assertEquals(12, header.fdstRecord)
  }

  @Test
  fun `encrypted books are refused`() {
    val book = palmDb(listOf(record0(textLength = 4, textRecords = 1, encryption = 2), "text".toByteArray()))
    assertFailsWith<KindleDrmException> { convertKindleToEpub(book, ByteArrayOutputStream()) }
    assertTrue(readKindleMetadata(book.inputStream()).drmProtected)
  }

  @Test
  fun `topaz and other formats are refused`() {
    val book = palmDb(listOf(record0(textLength = 0, textRecords = 0)), type = "TPZ0TPZ0")
    assertFailsWith<KindleFormatException> { convertKindleToEpub(book, ByteArrayOutputStream()) }
  }

  @Test
  fun `metadata and cover are read from the headers`() {
    val metadata = readKindleMetadata(mobi6().inputStream())
    assertEquals("The Quiet Orbit", metadata.title)
    assertEquals("Mara Quill", metadata.author)
    assertContentEquals(jpeg, metadata.cover)
    assertFalse(metadata.drmProtected)
    assertNull(readKindleMetadata(mobi6().inputStream(), withCover = false).cover)
  }

  @Test
  fun `a mobi 6 book converts into an epub`() {
    val files = convert(mobi6())
    assertEquals("mimetype", files.keys.first())
    assertEquals("application/epub+zip", files.getValue("mimetype").decodeToString())
    val opf = files.getValue("OEBPS/content.opf").decodeToString()
    assertTrue("<dc:title>The Quiet Orbit</dc:title>" in opf)
    assertTrue("<dc:creator>Mara Quill</dc:creator>" in opf)
    assertTrue("""href="images/image00001.jpg" media-type="image/jpeg" properties="cover-image"""" in opf)
    assertContentEquals(jpeg, files["OEBPS/images/image00001.jpg"])

    val first = files.getValue("OEBPS/text/part0000.xhtml").decodeToString()
    val second = files.getValue("OEBPS/text/part0001.xhtml").decodeToString()
    assertTrue("Launch" in first && "Drift" !in first)
    assertTrue(Regex("""href="part0001.xhtml#filepos\d+"""").containsMatchIn(first))
    assertTrue("Drift" in second && """src="../images/image00001.jpg"""" in second)
    assertFalse("mbp:" in first + second)
    val nav = files.getValue("OEBPS/nav.xhtml").decodeToString()
    assertTrue("Launch" in nav && "Drift" in nav)
    listOf("OEBPS/text/part0000.xhtml", "OEBPS/text/part0001.xhtml", "OEBPS/nav.xhtml", "OEBPS/toc.ncx", "OEBPS/content.opf")
      .forEach { assertWellFormed(files.getValue(it)) }
  }

  @Test
  fun `a kf8 book converts into an epub`() {
    val files = convert(kf8())
    val first = files.getValue("OEBPS/text/part0000.xhtml").decodeToString()
    val second = files.getValue("OEBPS/text/part0001.xhtml").decodeToString()
    assertTrue("""<p id="start">Hello""" in first)
    assertTrue("""href="part0001.xhtml#aid-5"""" in first)
    assertTrue("""src="../images/image00001.jpg"""" in first)
    assertTrue("""href="../styles/flow0001.css"""" in first)
    assertTrue("""<body>""" in first)
    assertTrue("""<h2 id="aid-5">Chapter two</h2>""" in second)
    assertEquals("body { background: url(../images/image00001.jpg) }", files.getValue("OEBPS/styles/flow0001.css").decodeToString())
    val nav = files.getValue("OEBPS/nav.xhtml").decodeToString()
    assertTrue("""<a href="text/part0000.xhtml#start">Chapter one</a>""" in nav)
    assertTrue("""<a href="text/part0001.xhtml#aid-5">Chapter two</a>""" in nav)
    assertTrue("<dc:title>Kindle Eight</dc:title>" in files.getValue("OEBPS/content.opf").decodeToString())
    files.filterKeys { it.endsWith("xhtml") || it.endsWith("opf") || it.endsWith("ncx") }.values.forEach(::assertWellFormed)
  }

  private fun convert(book: ByteArray): Map<String, ByteArray> {
    val out = ByteArrayOutputStream()
    convertKindleToEpub(book, out)
    val files = linkedMapOf<String, ByteArray>()
    ZipInputStream(out.toByteArray().inputStream()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        if (entry.name == "mimetype") assertEquals(ZipEntry.STORED, entry.method)
        files[entry.name] = zip.readBytes()
      }
    }
    return files
  }

  private fun assertWellFormed(xml: ByteArray) {
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val _ = factory.newDocumentBuilder().parse(xml.inputStream())
  }
}

internal fun mobi6(): ByteArray {
  val template = "<html><head><guide></guide></head><body><h1>Launch</h1><p>First line with " +
    "<a filepos=0000000000>a link</a>&nbsp;here.</p><mbp:pagebreak/><mbp:pagebreak/><h1>Drift</h1>" +
    "<p>Second <img recindex=\"00001\"> line.</p></body></html>"
  val target = template.indexOf("<h1>Drift")
  val text = template.replace("0000000000", target.toString().padStart(10, '0')).toByteArray()
  val record0 = record0(
    textLength = text.size,
    textRecords = 1,
    fields = mapOf(108 to 2L),
    exth = listOf(
      Exth.AUTHOR to string("Mara Quill"),
      Exth.UPDATED_TITLE to string("The Quiet Orbit"),
      Exth.COVER_OFFSET to int(0),
    ),
  )
  return palmDb(listOf(record0, text, jpeg, "FLIS".toByteArray()))
}

internal fun kf8(): ByteArray {
  val head = """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>T</title>"""
  val skeleton0 = head + """<link href="kindle:flow:0001?mime=text/css" rel="stylesheet" type="text/css"/></head><body aid="0"></body></html>"""
  val fragment0 = """<p id="start">Hello <a href="kindle:pos:fid:0001:off:0000000000">next</a></p><img src="kindle:embed:0001?mime=image/jpeg"/>"""
  val skeleton1 = "$head</head><body></body></html>"
  val fragment1 = """<h2 aid="5">Chapter two</h2><p>Bye</p>"""
  val css = "body { background: url(kindle:embed:0001?mime=image/jpeg) }"
  val skeleton1Start = skeleton0.length + fragment0.length
  val pages = skeleton0 + fragment0 + skeleton1 + fragment1
  val text = (pages + css).toByteArray()

  val fdst = bytes {
    text("FDST")
    u32(12)
    u32(2)
    u32(0)
    u32(pages.length.toLong())
    u32(pages.length.toLong())
    u32(text.size.toLong())
  }
  val skeletons = index(
    tags = listOf(Tag(1, 1, 0x03), Tag(6, 2, 0x0C)),
    entries = listOf(
      "SKEL0000000000" to entry(0x05, 1, 0, skeleton0.length),
      "SKEL0000000001" to entry(0x05, 1, skeleton1Start, skeleton1.length),
    ),
  )
  val fragments = index(
    tags = listOf(Tag(3, 1, 0x01), Tag(4, 1, 0x02), Tag(6, 2, 0x04)),
    entries = listOf(
      skeleton0.indexOf("</body>").toString().padStart(10, '0') to entry(0x07, 0, 0, 0, fragment0.length),
      (skeleton1Start + skeleton1.indexOf("</body>")).toString().padStart(10, '0') to entry(0x07, 1, 1, 0, fragment1.length),
    ),
  )
  val labels = listOf("Chapter one", "Chapter two")
  val strings = bytes {
    labels.forEach {
      bytes(vwi(it.length))
      text(it)
    }
  }
  val ncx = index(
    tags = listOf(Tag(3, 1, 0x01), Tag(4, 1, 0x02), Tag(6, 2, 0x04)),
    entries = listOf(
      "0" to entry(0x07, 0, 0, 0, 0),
      "1" to entry(0x07, 1 + labels[0].length, 0, 1, 0),
    ),
    strings = strings,
  )
  // 0 header, 1 text, 2 image, 3 FDST, 4-5 skeletons, 6-7 fragments, 8-10 NCX
  val record0 = record0(
    textLength = text.size,
    textRecords = 1,
    version = 8,
    fields = mapOf(108 to 2L, 0xC0 to 3L, 0xFC to 4L, 0xF8 to 6L, 0xF4 to 8L),
    exth = listOf(Exth.UPDATED_TITLE to string("Kindle Eight"), Exth.COVER_OFFSET to int(0)),
  )
  return palmDb(listOf(record0, text, jpeg, fdst) + skeletons + fragments + ncx)
}
