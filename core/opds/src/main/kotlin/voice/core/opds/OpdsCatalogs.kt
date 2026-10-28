package voice.core.opds

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import voice.core.audiobookshelf.login.isLocalHost

@Inject
public class OpdsCatalogs(
  private val store: OpdsCatalogStore,
  private val client: OpdsClient,
) {

  public val all: Flow<List<OpdsCatalog>> get() = store.data

  public suspend fun catalog(id: String): OpdsCatalog? = store.data.first().firstOrNull { it.id == id }

  /**
   * Saves the catalog once its feed answers, so a typo shows right away and not on the first visit. An address without
   * http or https is tried with both, and a server address without a path also on the paths catalogs usually live on.
   */
  public suspend fun save(catalog: OpdsCatalog): FeedResult {
    val base = catalog.copy(
      name = catalog.name.trim(),
      username = catalog.username?.trim()?.takeIf { it.isNotEmpty() },
      password = catalog.password?.takeIf { catalog.username?.isNotBlank() == true && it.isNotEmpty() },
    )
    var failure: FeedResult = FeedResult.Unreachable
    val unreachableOrigins = mutableSetOf<String>()
    for (url in catalogUrlCandidates(catalog.url, catalog.type, withPassword = base.username != null)) {
      val origin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}:${it.port}" } ?: continue
      // a host that doesn't answer won't on another path either, which saves waiting for every timeout
      if (origin in unreachableOrigins) continue
      val candidate = base.copy(url = url)
      when (val result = client.feed(candidate)) {
        is FeedResult.Success -> {
          store(candidate, result)
          return result
        }
        FeedResult.Unreachable -> unreachableOrigins += origin
        // the server is there, so its answer says more than a host that wasn't reached
        FeedResult.WrongCredentials -> failure = result
        FeedResult.NotACatalog -> if (failure == FeedResult.Unreachable) failure = result
      }
    }
    return failure
  }

  private suspend fun store(
    catalog: OpdsCatalog,
    result: FeedResult.Success,
  ) {
    val named = if (catalog.name.isEmpty()) {
      catalog.copy(name = result.feed.title ?: catalog.url.substringAfter("://"))
    } else {
      catalog
    }
    store.updateData { catalogs ->
      if (catalogs.any { it.id == named.id }) {
        catalogs.map { if (it.id == named.id) named else it }
      } else {
        catalogs + named
      }
    }
  }

  public suspend fun remove(id: String) {
    store.updateData { catalogs -> catalogs.filterNot { it.id == id } }
  }
}

/**
 * The addresses to try for what was typed, the most likely first. Servers at home often only speak http, so for them
 * http goes first. A server out on the internet is only tried over http when the address says so or no password is
 * sent, so a password never leaves the phone unencrypted by a guess.
 */
internal fun catalogUrlCandidates(
  input: String,
  type: CatalogType,
  withPassword: Boolean,
): List<String> {
  val trimmed = input.trim()
  if (trimmed.isEmpty()) return emptyList()
  val withSchemes = if ("://" in trimmed) {
    listOf(trimmed)
  } else {
    val host = trimmed.substringBefore('/').substringBeforeLast(':')
    when {
      isLocalHost(host) -> listOf("http://$trimmed", "https://$trimmed")
      withPassword -> listOf("https://$trimmed")
      else -> listOf("https://$trimmed", "http://$trimmed")
    }
  }
  return withSchemes
    .map { normalizeCatalogUrl(it, type) }
    .flatMap { url ->
      val path = url.toHttpUrlOrNull()?.encodedPath ?: return@flatMap emptyList()
      if (type == CatalogType.Opds && path.trimEnd('/').isEmpty()) {
        // the address of the server, not of its catalog: Calibre and Calibre-Web serve it on /opds, Komga on its path
        val root = url.trimEnd('/')
        listOf(url, "$root/opds", "$root/opds/v1.2/catalog")
      } else {
        listOf(url)
      }
    }
    .distinct()
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
