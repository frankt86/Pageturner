package voice.features.reader

import voice.core.data.EbookPosition
import voice.core.opds.komga.RemotePlace
import kotlin.test.Test
import kotlin.test.assertEquals

class RemotePlacesTest {

  private val chapters = listOf(3000, 0, 2000)

  @Test
  fun `the place from the server lands in the chapter at that share of the text`() {
    assertEquals(EbookPosition(0, 0), ebookPositionAt(chapters, 0.0))
    assertEquals(EbookPosition(0, 1500), ebookPositionAt(chapters, 0.3))
    assertEquals(EbookPosition(2, 500), ebookPositionAt(chapters, 0.7))
  }

  @Test
  fun `the end of the book is the last character`() {
    assertEquals(EbookPosition(2, 1999), ebookPositionAt(chapters, 1.0))
    assertEquals(EbookPosition(2, 1999), ebookPositionAt(chapters, 1.5))
  }

  @Test
  fun `a book without text starts at the beginning`() {
    assertEquals(EbookPosition(), ebookPositionAt(emptyList(), 0.5))
    assertEquals(EbookPosition(), ebookPositionAt(listOf(0, 0), 0.5))
  }

  @Test
  fun `the place goes to the old book engine and back`() {
    val content = ReaderContent.Epub(
      title = "Book",
      chapters = listOf("a".repeat(3000), "b".repeat(2000)),
      chapterTitles = listOf(null, null),
    )
    val place = RemotePlace(modified = 1, totalProgression = 0.8, href = null, progression = null, page = null, completed = false)
    assertEquals(EbookPosition(1, 1000), place.position(content))
  }

  private fun place(totalProgression: Double) =
    RemotePlace(modified = 1, totalProgression = totalProgression, href = null, progression = null, page = null, completed = false)

  @Test
  fun `a newer place before the first turn is gone to`() {
    assertEquals(RemotePlaceAction.Jump, remotePlaceAction(place(0.6), progressAtOpen = 0.2F, turnedPage = false))
  }

  @Test
  fun `a newer place after a turn waits for the next time`() {
    assertEquals(RemotePlaceAction.KeepForNextTime, remotePlaceAction(place(0.6), progressAtOpen = 0.2F, turnedPage = true))
  }

  @Test
  fun `the same page is only noted until the reader moves on`() {
    assertEquals(RemotePlaceAction.Note, remotePlaceAction(place(0.201), progressAtOpen = 0.2F, turnedPage = false))
    assertEquals(RemotePlaceAction.Ignore, remotePlaceAction(place(0.201), progressAtOpen = 0.2F, turnedPage = true))
  }
}
