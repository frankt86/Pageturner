package voice.core.opds.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguagesTest {

  @Test
  fun `every way of writing English is en`() {
    listOf("en", "eng", "en-US", "en_GB", "EN-gb", "English", " english ").forEach {
      assertEquals("en", languageCode(it))
    }
  }

  @Test
  fun `other languages keep their code`() {
    assertEquals("fr", languageCode("fr-CA"))
    assertEquals("de", languageCode("de"))
    assertEquals("dutch", languageCode("Dutch"))
  }

  @Test
  fun `an unknown language counts as none`() {
    assertNull(languageCode(""))
    assertNull(languageCode("und"))
    assertEquals(emptyList<String>(), languageCodes(listOf("und", " ")))
    assertEquals(listOf("en"), languageCodes(listOf("en", "eng", "en-US")))
  }

  @Test
  fun `opds 1 reads dc and dcterms language`() {
    val xml =
      """
      <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/elements/1.1/"
        xmlns:dcterms="http://purl.org/dc/terms/">
        <entry>
          <id>1</id><title>English</title><dcterms:language>en</dcterms:language>
          <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/1.epub"/>
        </entry>
        <entry>
          <id>2</id><title>French</title><dc:language> fre </dc:language>
          <dc:language>fr-FR</dc:language>
          <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/2.epub"/>
        </entry>
        <entry>
          <id>3</id><title>None</title>
          <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/3.epub"/>
        </entry>
      </feed>
      """
        .trimIndent()
    val feed = parseOpds1(xml, "https://example.com/opds")
    assertEquals(
      listOf(listOf("en"), listOf("fre", "fr"), emptyList()),
      feed.publications.map { it.languages },
    )
  }

  @Test
  fun `opds 2 reads a language string or array`() {
    val json =
      """
      {
        "metadata": { "title": "Books" },
        "publications": [
          { "metadata": { "title": "One", "language": "eng" }, "links": [${link(1)}] },
          { "metadata": { "title": "Two", "language": ["fr", "en-GB"] }, "links": [${link(2)}] },
          { "metadata": { "title": "Three" }, "links": [${link(3)}] }
        ]
      }
      """
        .trimIndent()
    val feed = parseOpds2(json, "https://example.com/opds")
    assertEquals(
      listOf(listOf("en"), listOf("fr", "en"), emptyList()),
      feed.publications.map { it.languages },
    )
  }

  @Test
  fun `english keeps books in english and without a language and drops the others`() {
    val navigation =
      OpdsNavigation(title = "Shelf", url = "https://example.com/shelf", summary = null)
    val feed =
      OpdsFeed(
        title = null,
        navigation = listOf(navigation),
        publications =
          listOf(
            publication("english", listOf("en")),
            publication("french", listOf("fr")),
            publication("unknown", emptyList()),
            publication("both", listOf("fr", "en")),
          ),
        nextUrl = "https://example.com/next",
        search = null,
      )

    val english = feed.inEnglish()

    assertEquals(listOf("english", "unknown", "both"), english.publications.map { it.id })
    assertEquals(listOf(navigation), english.navigation)
    assertEquals(feed.nextUrl, english.nextUrl)
  }

  @Test
  fun `a language placeholder takes the language`() {
    assertEquals(
      "https://openlibrary.org/opds/search?query=verne&language=en",
      expandSearchTemplate(
        "https://openlibrary.org/opds/search{?query,language,mode,media_type,limit}",
        "verne",
        language = "en",
      ),
    )
    assertEquals(
      "https://openlibrary.org/opds/search?query=verne",
      expandSearchTemplate(
        "https://openlibrary.org/opds/search{?query,language,mode,media_type,limit}",
        "verne",
      ),
    )
  }

  private fun link(id: Int) =
    """{ "href": "/$id.epub", "type": "application/epub+zip", "rel": "http://opds-spec.org/acquisition" }"""

  private fun publication(
    id: String,
    languages: List<String>,
  ) =
    OpdsPublication(
      id = id,
      title = id,
      authors = emptyList(),
      summary = null,
      coverUrl = null,
      thumbnailUrl = null,
      acquisitions = emptyList(),
      languages = languages,
    )
}
