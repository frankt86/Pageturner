package voice.core.opds

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Inject
public class OpdsCatalogs(
  private val store: OpdsCatalogStore,
  private val client: OpdsClient,
) {

  public val all: Flow<List<OpdsCatalog>> get() = store.data

  public suspend fun catalog(id: String): OpdsCatalog? = store.data.first().firstOrNull { it.id == id }

  /** Saves the catalog once its feed answers, so a typo shows right away and not on the first visit. */
  public suspend fun save(catalog: OpdsCatalog): FeedResult {
    val normalized = catalog.copy(
      name = catalog.name.trim(),
      url = normalizeCatalogUrl(catalog.url, catalog.type),
      username = catalog.username?.trim()?.takeIf { it.isNotEmpty() },
      password = catalog.password?.takeIf { catalog.username?.isNotBlank() == true && it.isNotEmpty() },
    )
    val result = client.feed(normalized)
    if (result is FeedResult.Success) {
      val named = if (normalized.name.isEmpty()) {
        normalized.copy(name = result.feed.title ?: normalized.url.substringAfter("://"))
      } else {
        normalized
      }
      store.updateData { catalogs ->
        if (catalogs.any { it.id == named.id }) {
          catalogs.map { if (it.id == named.id) named else it }
        } else {
          catalogs + named
        }
      }
    }
    return result
  }

  public suspend fun remove(id: String) {
    store.updateData { catalogs -> catalogs.filterNot { it.id == id } }
  }
}

internal fun normalizeCatalogUrl(
  input: String,
  type: CatalogType,
): String {
  val trimmed = input.trim()
  val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
  return when (type) {
    CatalogType.Opds -> withScheme
    // people paste the catalog address that Komga shows, instead of the one of the server
    CatalogType.Komga -> withScheme.trimEnd('/')
      .removeSuffix("/opds/v1.2/catalog")
      .removeSuffix("/opds/v2/catalog")
      .trimEnd('/')
  }
}
