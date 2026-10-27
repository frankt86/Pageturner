package voice.core.opds.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Opds2ParserTest {

  private val feed = parseFeed(resource("opds2.json"), "https://library.example.com/opds/v2/catalog", "application/opds+json")

  @Test
  fun feed() {
    assertEquals("Example Library", feed.title)
    assertEquals("https://library.example.com/opds/v2/catalog?page=2", feed.nextUrl)
    assertEquals(OpdsSearch.Template("https://library.example.com/opds/v2/search{?query}"), feed.search)
  }

  @Test
  fun navigationWithGroupsThatHaveTheirOwnFeed() {
    assertEquals(
      listOf(
        OpdsNavigation("New Publications", "https://library.example.com/opds/v2/new", null),
        OpdsNavigation("Popular Publications", "https://library.example.com/opds/v2/popular", null),
        OpdsNavigation("Science Fiction", "https://library.example.com/opds/v2/scifi", null),
      ),
      feed.navigation,
    )
  }

  @Test
  fun publications() {
    assertEquals(listOf("Pride and Prejudice", "Moby-Dick"), feed.publications.map { it.title })
    val pride = feed.publications[0]
    assertEquals("https://example.org/books/1342", pride.id)
    assertEquals(listOf("Jane Austen"), pride.authors)
    assertEquals("https://library.example.com/covers/1342.jpg", pride.coverUrl)
    assertEquals(
      listOf(OpdsAcquisition("https://library.example.com/books/1342.epub", "application/epub+zip")),
      pride.acquisitions,
    )

    val moby = feed.publications[1]
    assertEquals(listOf("Herman Melville"), moby.authors)
    assertEquals("Call me Ishmael.", moby.summary)
    assertEquals("https://library.example.com/covers/moby-dick-large.jpg", moby.coverUrl)
    assertEquals("https://library.example.com/covers/moby-dick-small.jpg", moby.thumbnailUrl)
    assertEquals(OpdsFormat.Pdf, moby.preferredAcquisition?.format)
  }

  @Test
  fun searchUrl() {
    assertEquals(
      "https://library.example.com/opds/v2/search?query=moby%20dick",
      expandSearchTemplate((feed.search as OpdsSearch.Template).template, " moby dick "),
    )
  }

  @Test
  fun jsonThatIsNoFeed() {
    assertFailsWith<OpdsParseException> {
      parseFeed("""{"error": "unauthorized"}""", "https://example.com", "application/json")
    }
  }
}
