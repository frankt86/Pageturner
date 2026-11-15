package voice.core.opds

import android.app.Application
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import voice.core.datastore.VoiceDataStoreFactory
import voice.core.initializer.AppInitializer

internal val defaultCatalogs: List<OpdsCatalog> =
  listOf(
    OpdsCatalog(
      id = "default-gutenberg",
      name = "Project Gutenberg",
      url = "https://www.gutenberg.org/ebooks.opds/",
    ),
    OpdsCatalog(
      id = "default-open-library",
      name = "Open Library",
      url = "https://openlibrary.org/opds/",
    ),
    OpdsCatalog(
      id = "default-gallica",
      name = "Gallica",
      url = "https://gallica.bnf.fr/opds",
    ),
    OpdsCatalog(
      id = "default-wolne-lektury",
      name = "Wolne Lektury",
      url = "https://wolnelektury.pl/opds/",
    ),
    OpdsCatalog(
      id = "default-textos-info",
      name = "textos.info",
      url = "https://textos.info/opds",
    ),
  )

/**
 * The ids of the [defaultCatalogs] that were added once, so one the listener removed stays away.
 */
@SingleIn(AppScope::class)
public class SeededCatalogsStore(store: DataStore<List<String>>) :
  DataStore<List<String>> by store {

  @Inject
  public constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.create(
      serializer = ListSerializer(String.serializer()),
      defaultValue = emptyList(),
      fileName = "opdsSeededCatalogs",
    ),
  )
}

@Inject
public class DefaultCatalogs(
  private val catalogStore: OpdsCatalogStore,
  private val seededStore: SeededCatalogsStore,
) {

  internal suspend fun add(defaults: List<OpdsCatalog> = defaultCatalogs) {
    val seeded = seededStore.data.first().toSet()
    val missing = defaults.filter { it.id !in seeded }
    if (missing.isEmpty()) return
    catalogStore.updateData { catalogs ->
      val sites = catalogs.mapNotNull { it.site() }.toSet()
      catalogs + missing.filter { it.site() !in sites }
    }
    seededStore.updateData { it + missing.map(OpdsCatalog::id) }
  }
}

private fun OpdsCatalog.site(): String? =
  feedUrl.toHttpUrlOrNull()?.host?.removePrefix("www.")?.removePrefix("m.")

@ContributesIntoSet(AppScope::class)
@Inject
public class DefaultCatalogsInitializer(private val defaultCatalogs: DefaultCatalogs) :
  AppInitializer {

  override fun onAppStart(application: Application) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { defaultCatalogs.add() }
  }
}
