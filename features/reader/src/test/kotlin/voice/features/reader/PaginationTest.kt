package voice.features.reader

import voice.core.data.EbookPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaginationTest {

  @Test
  fun `a chunk is the next paragraph`() {
    val text = "First one.\n\nSecond one."
    assertEquals("First one.", text.substring(nextChunk(text, 0)!!))
    assertEquals("Second one.", text.substring(nextChunk(text, 10)!!))
  }

  @Test
  fun `nothing is left after the last paragraph`() {
    assertNull(nextChunk("Only one.\n\n", 9))
  }

  @Test
  fun `a long paragraph is cut after a sentence`() {
    val text = "One two. Three four five."
    assertEquals("One two.", text.substring(nextChunk(text, 0, maxLength = 12)!!))
  }

  @Test
  fun `a question ends a sentence too`() {
    val text = "Who is there? Nobody at all."
    assertEquals("Who is there?", text.substring(nextChunk(text, 0, maxLength = 16)!!))
  }

  @Test
  fun `a long sentence is cut between words`() {
    val text = "one two three four"
    assertEquals("one two", text.substring(nextChunk(text, 0, maxLength = 10)!!))
  }

  @Test
  fun `a long paragraph without sentences is cut at the limit`() {
    val text = "abcdefghij"
    assertEquals("abcd", text.substring(nextChunk(text, 0, maxLength = 4)!!))
  }

  @Test
  fun `the page holding a position is found`() {
    val pages = listOf(
      Page(section = 0, start = 0, end = 10),
      Page(section = 0, start = 10, end = 20),
      Page(section = 1, start = 0, end = 10),
    )
    assertEquals(0, pages.indexOfPosition(EbookPosition(section = 0, offset = 0)))
    assertEquals(1, pages.indexOfPosition(EbookPosition(section = 0, offset = 15)))
    assertEquals(2, pages.indexOfPosition(EbookPosition(section = 1, offset = 3)))
    assertEquals(2, pages.indexOfPosition(EbookPosition(section = 7, offset = 0)))
  }
}
