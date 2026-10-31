package voice.core.ebook.mobi

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import voice.core.ebook.parseEpub
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class KindleEpubTest {

  @Test
  fun `a converted mobi book opens like an epub`() {
    val epub = ByteArrayOutputStream().also { convertKindleToEpub(mobi6(), it) }.toByteArray()
    val book = parseEpub(epub.inputStream())
    assertEquals("The Quiet Orbit", book.title)
    assertEquals("Mara Quill", book.author)
    assertContentEquals(jpeg, book.cover)
    val (first, second) = book.chapters.map { it.text }
    assertTrue(first.startsWith("Launch\n\nFirst line with a link"))
    assertTrue(second.startsWith("Drift\n\nSecond"))
    val (first, second) = book.chapters.map { it.text }
    assertTrue(first.startsWith("Launch\n\nFirst line with a link"))
    assertTrue(second.startsWith("Drift\n\nSecond"))
  }

  @Test
  fun `a converted azw3 book opens like an epub`() {
    val epub = ByteArrayOutputStream().also { convertKindleToEpub(kf8(), it) }.toByteArray()
    val book = parseEpub(epub.inputStream())
    assertEquals("Kindle Eight", book.title)
    assertContentEquals(jpeg, book.cover)
    assertEquals(listOf("Chapter one", "Chapter two"), book.chapters.map { it.title })
    assertEquals(listOf("Hello next", "Chapter two\n\nBye"), book.chapters.map { it.text.trim() })
  }
}
