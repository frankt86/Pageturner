package voice.core.opds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DefaultCatalogsTest {

  private val gutenberg =
    OpdsCatalog(
      id = "default-gutenberg",
      name = "Project Gutenberg",
      url = GUTENBERG_URL,
    )
  private val library =
    OpdsCatalog(id = "default-library", name = "Library", url = "https://library.example.org/opds")
  private val catalogStore = OpdsCatalogStore(MemoryDataStore(emptyList()))
  private val seededStore = SeededCatalogsStore(MemoryDataStore(emptyList()))
  private val defaults = DefaultCatalogs(catalogStore, seededStore)

  @Test
  fun `a new install gets every default catalog`() = runTest {
    defaults.add(listOf(gutenberg, library))

    assertEquals(listOf(gutenberg, library), catalogStore.data.first())
    assertEquals(listOf(ENGLISH_MIGRATION, gutenberg.id, library.id), seededStore.data.first())
  }

  @Test
  fun `defaults come after the catalogs of an existing user`() = runTest {
    val own = OpdsCatalog(id = "1", name = "Home", url = "http://nas.local:8083/opds")
    catalogStore.updateData { listOf(own) }

    defaults.add(listOf(gutenberg))

    assertEquals(listOf(own, gutenberg), catalogStore.data.first())
  }

  @Test
  fun `a removed default catalog does not come back`() = runTest {
    defaults.add(listOf(gutenberg))
    catalogStore.updateData { emptyList() }

    defaults.add(listOf(gutenberg))

    assertEquals(emptyList<OpdsCatalog>(), catalogStore.data.first())
  }

  @Test
  fun `a catalog the user already added is not added twice`() = runTest {
    val own = OpdsCatalog(id = "1", name = "Gutenberg", url = "https://m.gutenberg.org/ebooks.opds")
    catalogStore.updateData { listOf(own) }

    defaults.add(listOf(gutenberg, library))

    assertEquals(listOf(own, library), catalogStore.data.first())
    assertEquals(listOf(ENGLISH_MIGRATION, gutenberg.id, library.id), seededStore.data.first())
  }

  @Test
  fun `a later default catalog is added once`() = runTest {
    defaults.add(listOf(gutenberg))
    defaults.add(listOf(gutenberg, library))
    defaults.add(listOf(gutenberg, library))

    assertEquals(listOf(gutenberg, library), catalogStore.data.first())
  }

  @Test
  fun `the seeded Wolne Lektury is removed once`() = runTest {
    val seededWolne =
      OpdsCatalog(
        id = WOLNE_LEKTURY_ID,
        name = "Wolne Lektury",
        url = "https://wolnelektury.pl/opds/",
      )
    val ownWolne =
      OpdsCatalog(id = "1", name = "Wolne Lektury", url = "https://wolnelektury.pl/opds/")
    catalogStore.updateData { listOf(seededWolne, ownWolne) }
    seededStore.updateData { listOf(WOLNE_LEKTURY_ID) }

    defaults.add(emptyList())

    assertEquals(listOf(ownWolne), catalogStore.data.first())

    catalogStore.updateData { listOf(seededWolne) }
    defaults.add(emptyList())

    assertEquals(listOf(seededWolne), catalogStore.data.first())
  }

  @Test
  fun `the seeded Gutenberg moves to the books in English`() = runTest {
    catalogStore.updateData { listOf(gutenberg.copy(url = GUTENBERG_OLD_URL)) }
    seededStore.updateData { listOf(gutenberg.id) }

    defaults.add(listOf(gutenberg))

    assertEquals(listOf(gutenberg), catalogStore.data.first())
  }

  @Test
  fun `a Gutenberg address the user changed stays`() = runTest {
    val changed = gutenberg.copy(url = "https://www.gutenberg.org/ebooks/search.opds/?query=l.de")
    catalogStore.updateData { listOf(changed) }
    seededStore.updateData { listOf(gutenberg.id) }

    defaults.add(listOf(gutenberg))

    assertEquals(listOf(changed), catalogStore.data.first())
  }

  @Test
  fun `default catalogs need no sign in`() {
    assert(defaultCatalogs.all { it.username == null && it.password == null })
    assertEquals(defaultCatalogs.size, defaultCatalogs.map { it.id }.distinct().size)
  }
}
