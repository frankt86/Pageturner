package voice.features.reader

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaturalSpeakersTest {

  @Test
  fun `american english starts with heart`() {
    val speakers = naturalSpeakers(Locale.US)
    assertEquals("Heart", speakers.first().name)
    assertTrue(speakers.any { it.language == "en-gb" })
  }

  @Test
  fun `british english starts with emma`() {
    assertEquals("Emma", naturalSpeakers(Locale.UK).first().name)
    assertEquals("Emma", naturalSpeakers(Locale.forLanguageTag("en-AU")).first().name)
  }

  @Test
  fun `british voices use the british english espeak knows`() {
    assertEquals(setOf("en"), naturalSpeakers(Locale.UK).filter { it.language == "en-gb" }.map { it.espeakLanguage }.toSet())
  }

  @Test
  fun `portuguese is spoken the brazilian way`() {
    assertEquals("pt-br", naturalSpeakers(Locale.forLanguageTag("pt-PT")).first().language)
  }

  @Test
  fun `a language the voice doesn't speak has no speakers`() {
    assertTrue(naturalSpeakers(Locale.GERMANY).isEmpty())
  }

  @Test
  fun `every speaker is listed once`() {
    val all = listOf("en", "es", "fr", "hi", "it", "ja", "pt", "zh").flatMap { naturalSpeakers(Locale.forLanguageTag(it)) }
    val ids = all.map { it.id }
    assertEquals(ids.size, ids.distinct().size)
  }
}
