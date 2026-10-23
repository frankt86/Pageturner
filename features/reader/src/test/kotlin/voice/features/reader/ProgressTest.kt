package voice.features.reader

import voice.core.data.ReadingPace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class ProgressTest {

  private val content = ReaderContent.Epub(
    title = "Book",
    chapters = listOf("a".repeat(3000), "b".repeat(2000)),
    chapterTitles = listOf(null, null),
  )

  private val pages = listOf(
    Page(section = 0, start = 0, end = 1000),
    Page(section = 0, start = 1000, end = 2000),
    Page(section = 0, start = 2000, end = 3000),
    Page(section = 1, start = 0, end = 1000),
    Page(section = 1, start = 1000, end = 2000),
  )

  // 1,000 characters a minute
  private val pace = ReadingPace(characters = 10_000.0, millis = 10 * 60_000L)

  @Test
  fun `the progress counts the chapter and the book from the pages on screen`() {
    val progress = content.readingProgress(pages, index = 1, pagesPerSpread = 1, pace = pace)
    assertEquals(2, progress.page)
    assertEquals(2, progress.chapterPage)
    assertEquals(3, progress.chapterPageCount)
    assertEquals(1, progress.pagesLeftInChapter)
    assertEquals(3, progress.pagesLeftInBook)
    assertEquals(1.minutes, progress.chapterTimeLeft)
    assertEquals(3.minutes, progress.bookTimeLeft)
    assertEquals(5, progress.standardPages)
  }

  @Test
  fun `the last page of a chapter has no time left in it`() {
    val progress = content.readingProgress(pages, index = 2, pagesPerSpread = 1, pace = pace)
    assertNull(progress.chapterTimeLeft)
    assertEquals(2.minutes, progress.bookTimeLeft)
  }

  @Test
  fun `the pace follows the last hour of reading`() {
    val pace = ReadingPace(characters = 60_000.0, millis = 60 * 60_000L)
      .plus(characters = 6_000.0, millis = 60 * 60_000L)
    assertEquals(60 * 60_000L, pace.millis)
    assertEquals(550.0, pace.charactersPerMinute, absoluteTolerance = 0.01)
  }

  @Test
  fun `standard pages are 1024 characters each`() {
    assertEquals(1, standardPages(1024))
    assertEquals(2, standardPages(1025))
  }
}
