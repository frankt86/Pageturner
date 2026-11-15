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

  @Test
  fun `english feeds of gutenberg, wikisource and internet archive searches keep untagged books`() {
    assert(
      isEnglishOnServer(
        "https://www.gutenberg.org/ebooks/search.opds/?query=l.en&sort_order=downloads",
      ),
    )
    assert(isEnglishOnServer("http://m.gutenberg.org/ebooks/search.opds/?query=l.en%20verne"))
    assert(isEnglishOnServer("https://ws-export.wmcloud.org/opds/en/Ready_for_export.xml"))
    assert(
      isEnglishOnServer(
        "https://archive.org/services/opds/catalog?type=search" +
          "&query=%28verne%29+AND+language%3A%28eng+OR+English%29&page=2",
      ),
    )
  }

  @Test
  fun `other feeds drop untagged books`() {
    assert(!isEnglishOnServer("https://www.gutenberg.org/ebooks/search.opds/?query=verne"))
    assert(!isEnglishOnServer("https://ws-export.wmcloud.org/opds/fr/Ready_for_export.xml"))
    assert(
      !isEnglishOnServer(
        "https://archive.org/services/opds/catalog?type=browse&section=ebooks&item=open-access",
      ),
    )
    assert(!isEnglishOnServer("https://unglue.it/api/opds/all/?order_by=popular"))
  }
}
