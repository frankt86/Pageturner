package voice.core.opds

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import voice.core.datastore.VoiceDataStoreFactory

@Serializable
public enum class CatalogType {
  Opds,

  /** A Komga server. Its catalog lives at a fixed path, and its own api can sync the progress later on. */
  Komga,
}

@Serializable
public data class OpdsCatalog(
  val id: String,
  val name: String,
  /** What the listener typed: the catalog for [CatalogType.Opds], the server for [CatalogType.Komga]. */
  val url: String,
  val type: CatalogType = CatalogType.Opds,
  val username: String? = null,
  val password: String? = null,
) {

  val feedUrl: String
    get() = when (type) {
      CatalogType.Opds -> url
      CatalogType.Komga -> url.trimEnd('/') + "/opds/v1.2/catalog"
    }

  /**
   * The Basic auth header for [requestUrl]. Only the catalog's own host gets the password, not the image hosts or
   * download mirrors a feed may link to.
   */
  public fun authorizationFor(requestUrl: String): String? {
    val user = username?.takeIf { it.isNotEmpty() } ?: return null
    val host = feedUrl.toHttpUrlOrNull()?.host ?: return null
    if (requestUrl.toHttpUrlOrNull()?.host != host) return null
    return Credentials.basic(user, password.orEmpty())
  }
}

/** The catalogs the listener added. The passwords are kept like the Audiobookshelf login, in the app's own files. */
@SingleIn(AppScope::class)
public class OpdsCatalogStore(store: DataStore<List<OpdsCatalog>>) : DataStore<List<OpdsCatalog>> by store {

  @Inject
  public constructor(factory: VoiceDataStoreFactory) : this(
    factory.create(
      serializer = ListSerializer(OpdsCatalog.serializer()),
      defaultValue = emptyList(),
      fileName = "opdsCatalogs",
    ),
  )
}
