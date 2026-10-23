package voice.features.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ScrollTest {

  @Test
  fun `each paragraph of each chapter is an item`() {
    val chapters = listOf("First one.\n\nSecond one.", "", "\n\nThird one.\n")
    assertEquals(
      listOf(Page(0, 0, 10), Page(0, 12, 23), Page(2, 2, 12)),
      paragraphs(chapters),
    )
  }

  @Test
  fun `blank lines are no paragraphs`() {
    assertEquals(listOf(Page(0, 7, 10)), paragraphs(listOf("  \n\n  \nOne")))
  }

  @Test
  fun `a fling forward goes on to the next screen`() {
    assertEquals(900F, snapDistance(dragged = 100F, step = 1000F, velocity = 2000F, minVelocity = 500F))
  }

  @Test
  fun `a fling back goes back to the screen before`() {
    assertEquals(-100F, snapDistance(dragged = 100F, step = 1000F, velocity = -2000F, minVelocity = 500F))
  }

  @Test
  fun `a slow swipe goes to the closer screen`() {
    assertEquals(400F, snapDistance(dragged = 600F, step = 1000F, velocity = 0F, minVelocity = 500F))
    assertEquals(-300F, snapDistance(dragged = 300F, step = 1000F, velocity = 0F, minVelocity = 500F))
  }
}
