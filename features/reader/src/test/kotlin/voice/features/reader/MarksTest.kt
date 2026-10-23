package voice.features.reader

import kotlinx.coroutines.test.runTest
import voice.core.data.EbookPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarksTest {

  private val book = ReaderContent.Epub(
    title = "Book",
    chapters = listOf("The cat sat.\nA Cat ran.", "No pets here."),
    chapterTitles = listOf(null, null),
  )

  @Test
  fun `a word is found around a letter in it`() {
    val text = "Mr. Bennet’s wife"
    assertEquals("Bennet’s", text.substring(wordAt(text, text.indexOf('n'))))
  }

  @Test
  fun `punctuation is a word of its own`() {
    assertEquals(2..2, wordAt("Mr. Bennet", 2))
  }

  @Test
  fun `a bookmark is on the page that holds its place`() {
    val page = Page(section = 1, start = 10, end = 20)
    assertTrue(page.contains(EbookPosition(1, 10)))
    assertTrue(page.contains(EbookPosition(1, 19)))
    assertFalse(page.contains(EbookPosition(1, 20)))
    assertFalse(page.contains(EbookPosition(0, 15)))
  }

  @Test
  fun `a bookmark on a pdf page is on the whole page`() {
    assertTrue(Page(section = 3).contains(EbookPosition(3, 0)))
    assertFalse(Page(section = 3).contains(EbookPosition(4, 0)))
  }

  @Test
  fun `search finds every match ignoring case`() = runTest {
    val results = searchBook(book, "cat")
    assertEquals(
      listOf(BookRange(0, 4, 7), BookRange(0, 15, 18)),
      results.map { it.match },
    )
    val second = results[1]
    assertEquals(
      "Cat",
      second.snippet.substring(second.snippetMatchStart, second.snippetMatchStart + 3),
    )
  }

  @Test
  fun `search stops at the limit`() = runTest {
    assertEquals(1, searchBook(book, "cat", limit = 1).size)
  }

  @Test
  fun `the text of a range comes from its chapter`() {
    assertEquals("pets", book.text(BookRange(1, 3, 7)))
  }
}
