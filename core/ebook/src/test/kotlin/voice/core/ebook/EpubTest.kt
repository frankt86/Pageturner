package voice.core.ebook

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class EpubTest {

  private val cover = byteArrayOf(1, 2, 3)

  private fun epub(): ByteArray {
    val files = mapOf(
      "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
      "OEBPS/content.opf" to """
        <package xmlns="http://www.idpf.org/2007/opf">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>The Quiet Orbit</dc:title>
            <dc:creator>Mara Quill</dc:creator>
            <meta name="cover" content="cover"/>
            <meta name="calibre:series" content="Orbits"/>
            <meta name="calibre:series_index" content="1"/>
          </metadata>
          <manifest>
            <item id="cover" href="images/cover.jpg" media-type="image/jpeg"/>
            <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            <item id="one" href="text/one.xhtml" media-type="application/xhtml+xml"/>
            <item id="two" href="text/two%20b.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine toc="ncx"><itemref idref="one"/><itemref idref="two"/></spine>
        </package>
      """.trimIndent(),
      "OEBPS/toc.ncx" to """
        <ncx><navMap>
          <navPoint><navLabel><text>Launch</text></navLabel><content src="text/one.xhtml"/></navPoint>
          <navPoint><navLabel><text>Drift &amp; Return</text></navLabel><content src="text/two%20b.xhtml#start"/></navPoint>
        </navMap></ncx>
      """.trimIndent(),
      "OEBPS/text/one.xhtml" to
        "<html><head><title>x</title><style>p{}</style></head><body><p>First line.</p><p>Second line.</p></body></html>",
      "OEBPS/text/two b.xhtml" to "<html><body><p>Third line.</p></body></html>",
    )
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
      files.forEach { (name, content) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
      }
      zip.putNextEntry(ZipEntry("OEBPS/images/cover.jpg"))
      zip.write(cover)
    }
    return bytes.toByteArray()
  }

  @Test
  fun `reads metadata, chapters and their titles without the cover`() {
    val book = parseEpub(epub().inputStream())
    assertEquals("The Quiet Orbit", book.title)
    assertEquals("Mara Quill", book.author)
    assertNull(book.cover)
    assertEquals(listOf("Launch", "Drift & Return"), book.chapters.map { it.title })
    assertEquals(listOf("First line.\n\nSecond line.", "Third line."), book.chapters.map { it.text })
  }

  @Test
  fun `without text only metadata is read`() {
    val book = parseEpub(epub().inputStream(), withText = false)
    assertEquals("The Quiet Orbit", book.title)
    assertEquals(emptyList(), book.chapters)
    assertContentEquals(cover, book.cover)
    assertEquals("Orbits", book.series)
    assertEquals("1", book.seriesPart)
  }

  @Test
  fun `series from calibre`() {
    val opf = parseOpf(
      opf(
        """
        <meta name="calibre:series" content="Foundation"/>
        <meta name="calibre:series_index" content="2.0"/>
        """,
      ),
    )
    assertEquals("Foundation", opf.series)
    assertEquals("2", opf.seriesPart)
  }

  @Test
  fun `series from an EPUB 3 collection`() {
    val opf = parseOpf(
      opf(
        """
        <meta property="belongs-to-collection" id="c02">Reading list</meta>
        <meta refines="#c02" property="collection-type">set</meta>
        <meta property="belongs-to-collection" id="c01">The Stormlight Archive</meta>
        <meta refines="#c01" property="collection-type">series</meta>
        <meta refines="#c01" property="group-position">2.5</meta>
        <meta name="calibre:series" content="Stormlight"/>
        <meta name="calibre:series_index" content="3.0"/>
        """,
      ),
    )
    assertEquals("The Stormlight Archive", opf.series)
    assertEquals("2.5", opf.seriesPart)
  }

  @Test
  fun `a collection that isn't a series is no series`() {
    val opf = parseOpf(
      opf(
        """
        <meta property="belongs-to-collection" id="c01">Classics</meta>
        <meta refines="#c01" property="group-position">4</meta>
        """,
      ),
    )
    assertNull(opf.series)
    assertNull(opf.seriesPart)
  }

  @Test
  fun `a series without a part`() {
    val opf = parseOpf(
      opf(
        """
        <meta property="belongs-to-collection" id="c01">Discworld</meta>
        <meta refines="#c01" property="collection-type">series</meta>
        """,
      ),
    )
    assertEquals("Discworld", opf.series)
    assertNull(opf.seriesPart)
  }

  @Test
  fun `an index without a series is ignored`() {
    val opf = parseOpf(opf("""<meta name="calibre:series_index" content="1.0"/>"""))
    assertNull(opf.series)
    assertNull(opf.seriesPart)
  }

  private fun opf(meta: String): String {
    return """
      <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
          <dc:title>The Quiet Orbit</dc:title>
          <dc:creator id="author">Mara Quill</dc:creator>
          <meta refines="#author" property="role" scheme="marc:relators">aut</meta>
          $meta
        </metadata>
        <manifest/>
        <spine/>
      </package>
    """.trimIndent()
  }
}
