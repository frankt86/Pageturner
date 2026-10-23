package voice.features.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderNavigationTest {

  private val pages = listOf(
    Page(section = 0, start = 0, end = 10),
    Page(section = 0, start = 10, end = 20),
    Page(section = 0, start = 20, end = 30),
    Page(section = 1, start = 0, end = 10),
    Page(section = 1, start = 10, end = 20),
  )

  @Test
  fun `the volume keys go one page on or back`() {
    assertEquals(2, pages.spreadStart(index = 1, pagesPerSpread = 1, step = 1))
    assertEquals(0, pages.spreadStart(index = 1, pagesPerSpread = 1, step = -1))
  }

  @Test
  fun `with two pages side by side the volume keys go a whole spread`() {
    assertEquals(2, pages.spreadStart(index = 1, pagesPerSpread = 2, step = 1))
    assertEquals(2, pages.spreadStart(index = 4, pagesPerSpread = 2, step = -1))
  }

  @Test
  fun `there is no page before the first or after the last`() {
    assertNull(pages.spreadStart(index = 0, pagesPerSpread = 1, step = -1))
    assertNull(pages.spreadStart(index = 4, pagesPerSpread = 1, step = 1))
  }

  @Test
  fun `the progress counts the pages on screen as read`() {
    assertEquals(0.2F, pages.progressAt(index = 0, pagesPerSpread = 1))
    assertEquals(0.4F, pages.progressAt(index = 0, pagesPerSpread = 2))
    assertEquals(1F, pages.progressAt(index = 4, pagesPerSpread = 2))
  }

  @Test
  fun `the pages left are those of the chapter after the ones on screen`() {
    assertEquals(2, pages.pagesLeftInChapter(index = 0, pagesPerSpread = 1))
    assertEquals(0, pages.pagesLeftInChapter(index = 2, pagesPerSpread = 1))
    assertEquals(1, pages.pagesLeftInChapter(index = 3, pagesPerSpread = 1))
    assertEquals(1, pages.pagesLeftInChapter(index = 0, pagesPerSpread = 2))
    assertEquals(0, pages.pagesLeftInChapter(index = 4, pagesPerSpread = 2))
  }
}
