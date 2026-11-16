package voice.core.ebook.mobi

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import voice.core.ebook.parseEpub

@RunWith(AndroidJUnit4::class)
class KindleEpubTest {

  @Test
  fun `a converted mobi book opens like an epub`() {
    val epub = ByteArrayOutputStream().also { convertKindleToEpub(mobi6(), it) }.toByteArray()
    val book = parseEpub(epub.inputStream())
    assertEquals("The Quiet Orbit", book.title)
    assertEquals("Mara Quill", book.author)
    assertContentEquals(jpeg, parseEpub(epub.inputStream(), withText = false).cover)
    assertEquals(listOf("Launch", "Drift"), book.chapters.map { it.title })
    val (first, second) = book.chapters.map { it.text }
    assertTrue(first.startsWith("Launch\n\nFirst line with a link"))
    assertTrue(second.startsWith("Drift\n\nSecond"))
  }

  @Test
  fun `a converted azw3 book opens like an epub`() {
    val epub = ByteArrayOutputStream().also { convertKindleToEpub(kf8(), it) }.toByteArray()
    val book = parseEpub(epub.inputStream())
    assertEquals("Kindle Eight", book.title)
    assertContentEquals(jpeg, parseEpub(epub.inputStream(), withText = false).cover)
    assertEquals(listOf("Chapter one", "Chapter two"), book.chapters.map { it.title })
    assertEquals(listOf("Hello next", "Chapter two\n\nBye"), book.chapters.map { it.text.trim() })
  }

  @Test
  fun `deleting the converted copy of a book keeps the copies of other books`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val (deleted, kept) =
      listOf("deleted", "kept").map { name ->
        Uri.fromFile(File(context.filesDir, "$name.mobi").apply { writeBytes(mobi6()) })
      }
    val deletedEpub = kindleEpub(context, deleted)
    val keptEpub = kindleEpub(context, kept)

    deleteKindleEpub(context, deleted)

    assertFalse(deletedEpub.exists())
    assertTrue(keptEpub.exists())
  }
}
