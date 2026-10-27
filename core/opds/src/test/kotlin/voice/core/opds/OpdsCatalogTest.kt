package voice.core.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpdsCatalogTest {

  @Test
  fun komgaCatalogLivesOnTheServer() {
    val catalog = OpdsCatalog(id = "1", name = "Komga", url = "https://komga.example.com/", type = CatalogType.Komga)
    assertEquals("https://komga.example.com/opds/v1.2/catalog", catalog.feedUrl)
  }

  @Test
  fun normalizeUrl() {
    assertEquals("https://books.example.com/opds", normalizeCatalogUrl(" books.example.com/opds ", CatalogType.Opds))
    assertEquals("http://192.168.1.2:8083/opds", normalizeCatalogUrl("http://192.168.1.2:8083/opds", CatalogType.Opds))
    assertEquals(
      "https://komga.example.com",
      normalizeCatalogUrl("https://komga.example.com/opds/v1.2/catalog", CatalogType.Komga),
    )
  }

  @Test
  fun passwordOnlyGoesToTheCatalogsHost() {
    val catalog = OpdsCatalog(
      id = "1",
      name = "Books",
      url = "https://books.example.com/opds",
      username = "paul",
      password = "secret",
    )
    assertEquals("Basic cGF1bDpzZWNyZXQ=", catalog.authorizationFor("https://books.example.com/opds/download/1/epub"))
    assertNull(catalog.authorizationFor("https://covers.example.org/1.jpg"))
    assertNull(catalog.copy(username = null).authorizationFor("https://books.example.com/opds"))
  }
}
