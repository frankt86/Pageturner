package voice.core.opds

import kotlin.test.Test
import kotlin.test.assertEquals

class EnglishSearchTest {

  @Test
  fun `gutenberg searches the books in english`() {
    assertEquals(
      "l.en verne",
      englishSearchQuery(
        "http://m.gutenberg.org/ebooks/search.opds/?query={searchTerms}",
        " verne",
      ),
    )
  }

  @Test
  fun `the internet archive searches the books in english`() {
    assertEquals(
      "(jules verne) AND language:(eng OR English)",
      englishSearchQuery(
        "https://archive.org/services/opds/catalog{?query}&type=search",
        "jules verne",
      ),
    )
  }

  @Test
  fun `other catalogs get the search as it is`() {
    assertEquals("verne", englishSearchQuery("https://example.com/opds/search{?query}", "verne"))
  }
}
