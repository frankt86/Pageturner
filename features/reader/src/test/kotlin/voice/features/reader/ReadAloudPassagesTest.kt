package voice.features.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ReadAloudPassagesTest {

  @Test
  fun `a sentence ends at its full stop`() {
    val text = "It was a dark night. The rain fell."
    assertEquals("It was a dark night.", text.substring(nextSentence(text, 0)!!))
    assertEquals("The rain fell.", text.substring(nextSentence(text, 20)!!))
  }

  @Test
  fun `a closing quote stays with its sentence`() {
    val text = "She said, \"Come in now!\" He came."
    assertEquals("She said, \"Come in now!\"", text.substring(nextSentence(text, 0)!!))
  }

  @Test
  fun `a short sentence stays with the next one`() {
    val text = "Mr. Smith came home late."
    assertEquals(text, text.substring(nextSentence(text, 0)!!))
  }

  @Test
  fun `a sentence doesn't go past its paragraph`() {
    val text = "A paragraph with no end\nThe next one."
    assertEquals("A paragraph with no end", text.substring(nextSentence(text, 0)!!))
  }

  @Test
  fun `a long sentence is cut like a chunk`() {
    val text = "word ".repeat(100)
    assertEquals(nextChunk(text, 0), nextSentence(text, 0))
  }

  @Test
  fun `a full stop inside a word doesn't end a sentence`() {
    val text = "The file is called notes.txt and it is long."
    assertEquals(text, text.substring(nextSentence(text, 0)!!))
  }

  @Test
  fun `a long sentence is cut after a comma`() {
    val text =
      "Alice was beginning to get very tired of sitting by her sister on the bank, and of having " +
        "nothing to do."
    val first = nextSentence(text, 0)!!
    assertEquals(
      "Alice was beginning to get very tired of sitting by her sister on the bank,",
      text.substring(first),
    )
    assertEquals(
      "and of having nothing to do.",
      text.substring(nextSentence(text, first.last + 1)!!),
    )
  }

  @Test
  fun `a long sentence without a comma stays whole`() {
    val text = "word ".repeat(30).trim() + ". Next."
    assertEquals("word ".repeat(30).trim() + ".", text.substring(nextSentence(text, 0)!!))
  }

  @Test
  fun `nothing is left after the last sentence`() {
    assertNull(nextSentence("All done.  ", 9))
  }

  @Test
  fun `every passage is spoken with the one after it`() = runTest {
    val spoken = mutableListOf<Pair<String, String?>>()
    val hadText =
      speakInTurn(flowOf(Passage("One.", 1), Passage("Two.", 2), Passage("Three.", 3))) {
        passage,
        next ->
        spoken += passage.text to next
        true
      }
    assertTrue(hadText)
    assertEquals(listOf("One." to "Two.", "Two." to "Three.", "Three." to null), spoken)
  }

  @Test
  fun `the next passage is known before the current one is spoken`() = runTest {
    val events = mutableListOf<String>()
    val passages = flow {
      events += "made 1"
      emit(Passage("One.", 1))
      events += "made 2"
      emit(Passage("Two.", 2))
    }
    val _ =
      speakInTurn(passages) { passage, _ ->
        events += "spoke ${passage.place}"
        true
      }
    assertEquals(listOf("made 1", "made 2", "spoke 1", "spoke 2"), events)
  }

  @Test
  fun `speaking stops when the voice fails`() = runTest {
    var made = 0
    val passages = flow {
      repeat(5) {
        made++
        emit(Passage("Passage $it.", it))
      }
    }
    val spoken = mutableListOf<Int>()
    val hadText =
      speakInTurn(passages) { passage, _ ->
        spoken += passage.place
        passage.place < 1
      }
    assertTrue(hadText)
    assertEquals(listOf(0, 1), spoken)
    assertEquals(3, made)
  }

  @Test
  fun `a book without text has nothing to read`() = runTest {
    var called = false
    val hadText =
      speakInTurn(emptyFlow<Passage<Int>>()) { _, _ ->
        called = true
        true
      }
    assertFalse(hadText)
    assertFalse(called)
  }
}
