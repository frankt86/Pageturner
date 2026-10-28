package voice.core.opds.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class Opds1ParserTest {

  @Test
  fun calibreWebNavigation() {
    val feed = parseFeed(resource("calibre-web-root.xml"), "https://books.example.com/opds", "application/atom+xml")

    assertEquals("Calibre-Web", feed.title)
    assertEquals(
      listOf(
        OpdsNavigation("Alphabetical Books", "https://books.example.com/opds/books", "Books sorted alphabetically"),
        OpdsNavigation("Recently added Books", "https://books.example.com/opds/new", "The latest Books"),
        OpdsNavigation("Authors", "https://books.example.com/opds/author", "Books ordered by Author"),
      ),
      feed.navigation,
    )
    assertEquals(emptyList(), feed.publications)
    assertNull(feed.nextUrl)
    assertEquals(OpdsSearch.Description("https://books.example.com/opds/osd"), feed.search)
  }

  @Test
  fun calibreWebAcquisition() {
    val feed = parseFeed(resource("calibre-web-books.xml"), "https://books.example.com/opds/new", null)

    assertEquals("https://books.example.com/opds/new?offset=60", feed.nextUrl)
    assertEquals(2, feed.publications.size)
    val book = feed.publications[0]
    assertEquals("urn:uuid:5b3a1f0e-4c2d-4a9b-9f1e-7d8c6b5a4e3f", book.id)
    assertEquals("The Left Hand of Darkness", book.title)
    assertEquals(listOf("Ursula K. Le Guin"), book.authors)
    assertEquals(
      "A lone human emissary arrives on Winter, an alien world.\nIts people can choose & change their gender.",
      book.summary,
    )
    assertEquals("https://books.example.com/opds/cover/12", book.coverUrl)
    assertEquals("https://books.example.com/opds/cover/12", book.thumbnailUrl)
    assertEquals(
      OpdsAcquisition("https://books.example.com/opds/download/12/epub/", "application/epub+zip", "EPUB", 421337),
      book.preferredAcquisition,
    )
    assertEquals("MOBI", book.acquisitions[0].label)

    val comic = feed.publications[1]
    assertEquals(listOf("Alan Moore", "Dave Gibbons"), comic.authors)
    assertNull(comic.preferredAcquisition)
    assertEquals("CBZ", comic.acquisitions.single().label)
  }

  @Test
  fun komgaNavigation() {
    val feed = parseFeed(resource("komga-root.xml"), "https://komga.example.com/opds/v1.2/catalog", "application/atom+xml")

    assertEquals("Komga OPDS catalog", feed.title)
    assertEquals(
      listOf("Keep Reading", "All series", "All libraries"),
      feed.navigation.map { it.title },
    )
    assertEquals("https://komga.example.com/opds/v1.2/books/ondeck", feed.navigation[0].url)
    assertEquals(OpdsSearch.Description("https://komga.example.com/opds/v1.2/search"), feed.search)
  }

  @Test
  fun komgaBooks() {
    val feed = parseFeed(resource("komga-books.xml"), "https://komga.example.com/opds/v1.2/series/0B5MNCGK7S9GV", null)

    assertEquals("https://komga.example.com/opds/v1.2/series/0B5MNCGK7S9GV?page=1", feed.nextUrl)
    val (first, second) = feed.publications
    assertEquals("0B5MNCH8QG7AX", first.id)
    assertEquals("The first Discworld novel.", first.summary)
    assertEquals("https://komga.example.com/opds/v1.2/books/0B5MNCH8QG7AX/thumbnail/small", first.thumbnailUrl)
    assertEquals(OpdsFormat.Epub, first.preferredAcquisition?.format)
    assertNull(second.summary)
    assertEquals(emptyList(), second.authors)
    assertEquals(
      "https://komga.example.com/opds/v1.2/books/0B5MNCHBG9ZQ1/file/The%20Light%20Fantastic.pdf",
      second.preferredAcquisition?.url,
    )
    assertEquals(OpdsFormat.Pdf, second.preferredAcquisition?.format)
  }

  @Test
  fun standardEbooks() {
    val feed = parseFeed(resource("standard-ebooks.xml"), "https://standardebooks.org/feeds/opds/new-releases", null)

    assertEquals(OpdsSearch.Description("https://standardebooks.org/ebooks/opensearch"), feed.search)
    val book = feed.publications.single()
    assertEquals("Persuasion", book.title)
    assertEquals(listOf("Jane Austen"), book.authors)
    assertEquals(
      "Anne Elliot is persuaded to break off her engagement, and meets him again years later.",
      book.summary,
    )
    assertEquals(4, book.acquisitions.size)
    assertEquals(
      "https://standardebooks.org/ebooks/jane-austen/persuasion/downloads/jane-austen_persuasion.epub",
      book.preferredAcquisition?.url,
    )
    assertEquals(listOf("EPUB", "EPUB", "KEPUB", "MOBI"), book.acquisitions.map { it.label })
  }

  @Test
  fun openSearchDescription() {
    assertEquals(
      "https://books.example.com/opds/search/{searchTerms}",
      parseOpenSearchTemplate(resource("calibre-web-osd.xml"), "https://books.example.com/opds/osd"),
    )
    assertEquals(
      "https://komga.example.com/opds/v1.2/search?search={searchTerms}",
      parseOpenSearchTemplate(resource("komga-osd.xml"), "https://komga.example.com/opds/v1.2/search"),
    )
  }

  @Test
  fun htmlPageIsNoCatalog() {
    assertFailsWith<OpdsParseException> {
      parseFeed("<!DOCTYPE html><html><body>Login</body></html>", "https://example.com", "text/html")
    }
  }

  @Test
  fun searchTemplates() {
    assertEquals(
      "https://example.com/opds/search/war%20and%20peace",
      expandSearchTemplate("https://example.com/opds/search/{searchTerms}", "war and peace"),
    )
    assertEquals(
      "https://example.com/search?q=Br%C3%BCcke&page=",
      expandSearchTemplate("https://example.com/search?q={searchTerms}&page={startPage?}", "Brücke"),
    )
    assertEquals(
      "https://example.com/opds/v2/search?query=dune",
      expandSearchTemplate("https://example.com/opds/v2/search{?query,author,title}", "dune"),
    )
  }

  @Test
  fun templateKeepsItsPlaceholdersWhenResolved() {
    assertEquals(
      "https://example.com/opds/search/{searchTerms}",
      resolveTemplate("https://example.com/opds/new", "search/{searchTerms}"),
    )
  }

  @Test
  fun html() {
    assertEquals("Tom &amp; Jerry — a story", htmlToText("<b>Tom &amp;amp; Jerry</b> &mdash; a&nbsp;story"))
  }
}

internal fun resource(name: String): String {
  return checkNotNull(Opds1ParserTest::class.java.getResource("/opds/$name")).readText()
}
