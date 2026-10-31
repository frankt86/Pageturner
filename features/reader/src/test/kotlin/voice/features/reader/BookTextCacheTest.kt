package voice.features.reader

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookTextCacheTest {

  @get:Rule
  val folder = TemporaryFolder()

  private val text = BookText(
    title = "The Quiet Orbit",
    chapters = listOf("First line.\n\nSecond line.", "x".repeat(100_000), "Ünïcödé ✓"),
    chapterTitles = listOf("Launch", null, "Drift & Return"),
  )

  @Test
  fun `the key changes with the file`() {
    val key = bookTextKey("content://book", size = 10, modified = 20)
    assertNotEquals(key, bookTextKey("content://book", size = 11, modified = 20))
    assertNotEquals(key, bookTextKey("content://book", size = 10, modified = 21))
    assertNotEquals(key, bookTextKey("content://other", size = 10, modified = 20))
    assertEquals(key, bookTextKey("content://book", size = 10, modified = 20))
  }

  @Test
  fun `a file that doesn't tell its size or change has no key`() {
    assertNull(bookTextKey("content://book", size = null, modified = 20))
    assertNull(bookTextKey("content://book", size = 10, modified = null))
    assertNull(bookTextKey("content://book", size = 10, modified = 0))
  }

  @Test
  fun `the text comes back as it was kept`() {
    val file = bookTextFile(folder.root, "content://book")
    writeBookText(file, "key", text)
    val read = readBookText(file, "key")!!
    assertEquals(text.title, read.title)
    assertEquals(text.chapters, read.chapters)
    assertEquals(text.chapterTitles, read.chapterTitles)
  }

  @Test
  fun `the text of a changed file is read anew`() {
    val file = bookTextFile(folder.root, "content://book")
    writeBookText(file, "old", text)
    assertNull(readBookText(file, "new"))
  }

  @Test
  fun `a broken or missing cache is read anew`() {
    val file = bookTextFile(folder.root, "content://book")
    assertNull(readBookText(file, "key"))
    writeBookText(file, "key", text)
    file.writeBytes(file.readBytes().copyOf(200))
    assertNull(readBookText(file, "key"))
  }

  @Test
  fun `only the most recently used texts are kept`() {
    repeat(4) { index ->
      bookTextFile(folder.root, "content://$index").apply {
        writeBookText(this, "key", text)
        setLastModified(1_000_000L * (index + 1))
      }
    }
    pruneBookTexts(folder.root, keep = 2)
    val kept = folder.root.listFiles()!!.toSet()
    assertEquals(setOf(bookTextFile(folder.root, "content://2"), bookTextFile(folder.root, "content://3")), kept)
    assertTrue(kept.all { it.exists() })
  }
}
