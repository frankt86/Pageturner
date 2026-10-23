package voice.core.ebook

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class EpubTest {

  private val cover = byteArrayOf(1, 2, 3)

  private fun epub(): ByteArray {
    val files = mapOf(
      "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
      "OEBPS/content.opf" to """
        <package>
          <metadata>
            <dc:title>The Quiet Orbit</dc:title>
            <dc:creator>Mara Quill</dc:creator>
            <meta name="cover" content="cover"/>
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
  fun `reads metadata, chapters and their titles`() {
    val book = parseEpub(epub().inputStream())
    assertEquals("The Quiet Orbit", book.title)
    assertEquals("Mara Quill", book.author)
    assertContentEquals(cover, book.cover)
    assertEquals(listOf("Launch", "Drift & Return"), book.chapters.map { it.title })
    assertEquals(listOf("First line.\n\nSecond line.", "Third line."), book.chapters.map { it.text })
  }

  @Test
  fun `without text only metadata is read`() {
    val book = parseEpub(epub().inputStream(), withText = false)
    assertEquals("The Quiet Orbit", book.title)
    assertEquals(emptyList(), book.chapters)
    assertContentEquals(cover, book.cover)
  }
}
