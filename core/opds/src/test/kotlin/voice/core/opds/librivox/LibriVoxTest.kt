package voice.core.opds.librivox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import okhttp3.HttpUrl.Companion.toHttpUrl
import voice.core.opds.CatalogType
import voice.core.opds.OpdsCatalog
import voice.core.opds.feed.AudiobookPackaging
import voice.core.opds.feed.inEnglish
import voice.core.opds.feed.resource

class LibriVoxTest {

  private val books = parseLibriVoxBooks(resource("librivox.json"))
  private val feed = libriVoxFeed(books, nextUrl = null)

  @Test
  fun `every book of the answer is read`() {
    assertEquals(listOf("22824", "22889", "23016", "3650"), books.map { it.id })
  }

  @Test
  fun `only finished books are listed`() {
    assertEquals(
      listOf("Absolute Evil", "Kabouters in het Bosch", "Ophelia, the Rose of Elsinore"),
      feed.publications.map { it.title },
    )
  }

  @Test
  fun `a book in another language is left out of the books in English`() {
    val dutch = books.single { it.id == "22889" }
    assertEquals(listOf("dutch"), dutch.toPublication()?.languages)
    assertEquals(
      listOf("Absolute Evil", "Ophelia, the Rose of Elsinore"),
      feed.inEnglish(keepUntagged = false).publications.map { it.title },
    )
    assertEquals(listOf("en"), dutch.copy(language = "English").toPublication()?.languages)
  }

  @Test
  fun `a book that is still recorded is left out`() {
    assertNull(books.single { it.id == "23016" }.toPublication())
  }

  @Test
  fun `a book maps to an mp3 audiobook with a file per section`() {
    val book = feed.publications.single { it.id == "22824" }
    assertEquals(listOf("Julian Hawthorne"), book.authors)
    assertEquals(4811.seconds, book.duration)
    assertEquals(
      listOf("Patty H", "Jersey City Frankie", "Rohit Gokhale", "dc", "Christine Rottger"),
      book.narrators,
    )
    assertEquals("https://archive.org/services/img/absoluteevil_2608_librivox", book.coverUrl)
    assert(book.summary!!.startsWith("A descendant of New England witches"))
    val audiobook = book.audiobook!!
    assertEquals(AudiobookPackaging.Files, audiobook.packaging)
    assertEquals("MP3", audiobook.format)
    assertEquals(
      listOf(
        "Chapters I thru IV",
        "Chapters V and VI",
        "Chapters VII and VIII",
        "Chapter IX",
        "Chapter X",
      ),
      audiobook.files.map { it.title },
    )
    assertEquals(
      "https://www.archive.org/download/absoluteevil_2608_librivox/" +
        "absoluteevil_01_hawthorne_64kb.mp3",
      audiobook.files.first().url,
    )
  }

  @Test
  fun `sections without titles still download as one file each`() {
    val book = feed.publications.single { it.id == "3650" }
    assertEquals(listOf("Mary Cowden Clarke"), book.authors)
    val files = book.audiobook!!.files
    assertEquals(7, files.size)
    assertEquals((1..7).map { it.toString() }, files.map { it.title })
  }

  @Test
  fun `sections play in the order of their numbers`() {
    val book = books.single { it.id == "22824" }
    val publication = book.copy(sections = book.sections.reversed()).toPublication()!!
    assertEquals("Chapters I thru IV", publication.audiobook!!.files.first().title)
  }

  @Test
  fun `the summary is plain text`() {
    val book =
      books
        .single { it.id == "22824" }
        .copy(description = "<p>A <i>haunted</i> island.<br />By Brian</p>")
        .toPublication()!!
    assertEquals("A haunted island.\nBy Brian", book.summary)
  }

  @Test
  fun `an answer without books has none`() {
    assertEquals(
      emptyList<LibriVoxBook>(),
      parseLibriVoxBooks("""{"error":"Audiobooks could not be found"}"""),
    )
  }

  @Test
  fun `a search looks for the title and for the last name`() {
    val urls = libriVoxSearchUrls(" The Count  of Monte Cristo ").map { it.toHttpUrl() }
    assertEquals("^Count of Monte Cristo", urls[0].queryParameter("title"))
    assertEquals("Cristo", urls[1].queryParameter("author"))
    assertEquals(emptyList<String>(), libriVoxSearchUrls("  "))
  }

  @Test
  fun `the catalog opens with the newest finished books`() {
    val catalog =
      OpdsCatalog(
        id = "default-librivox",
        name = "LibriVox",
        url = LIBRIVOX_URL,
        type = CatalogType.LibriVox,
      )
    val url = catalog.feedUrl.toHttpUrl()
    assertEquals("desc", url.queryParameter("sort_order"))
    assertEquals("1", url.queryParameter("since"))
    assertEquals("0", url.queryParameter("offset"))
  }
}
