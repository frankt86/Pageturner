package voice.features.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import voice.core.data.EbookPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

  @Test
  fun `without a paragraph gap the text stays one paragraph`() {
    assertTrue(paragraphStyles("One.\n\nTwo.", TextUnit.Unspecified, continued = true).isEmpty())
  }

  @Test
  fun `the line breaks between paragraphs take the gap`() {
    assertEquals(
      listOf(AnnotatedString.Range(ParagraphStyle(lineHeight = 4.sp), 4, 6)),
      paragraphStyles("One.\n\nTwo.", 4.sp, continued = false),
    )
  }

  @Test
  fun `a paragraph from the page before has no indent`() {
    assertEquals(
      listOf(
        AnnotatedString.Range(ParagraphStyle(textIndent = TextIndent.None), 0, 4),
        AnnotatedString.Range(ParagraphStyle(lineHeight = 4.sp), 4, 6),
      ),
      paragraphStyles("end.\n\nNext", 4.sp, continued = true),
    )
  }

  @Test
  fun `a page that ends inside a paragraph is followed by the next word`() {
    val chapter = "one two three"
    assertEquals("two", pageTail(chapter, 4))
  }

  @Test
  fun `a page that ends inside a word is followed by the rest of it`() {
    assertEquals("tion", pageTail("attention please", 5))
  }

  @Test
  fun `a page that ends with its paragraph is followed by nothing`() {
    assertEquals("", pageTail("One.\n\nTwo.", 5))
    assertEquals("", pageTail("One.", 4))
  }

  @Test
  fun `the chapters not measured yet get as much text on a page as the measured one`() {
    val chapters = listOf("a".repeat(250), "b".repeat(200), "", "c".repeat(90))
    val measured = listOf(Page(1, 0, 100), Page(1, 100, 200))
    assertEquals(
      listOf(
        Page(0, 0, 83),
        Page(0, 83, 166),
        Page(0, 166, 250),
        Page(1, 0, 100),
        Page(1, 100, 200),
        Page(3, 0, 90),
      ),
      estimatedPages(chapters, section = 1, measured = measured),
    )
  }

  @Test
  fun `estimated pages cover every chapter to its end`() {
    val chapters = listOf("x".repeat(10_001), "y".repeat(3))
    val pages = estimatedPages(chapters, section = 1, measured = emptyList())
    assertEquals(Page(0, 0, pages.first().end), pages.first())
    assertEquals(10_001, pages.filter { it.section == 0 }.last().end)
    assertTrue(pages.zipWithNext().all { (a, b) -> a.section != b.section || a.end == b.start })
  }
}
