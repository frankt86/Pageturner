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
import voice.core.opds.librivox.LIBRIVOX_URL

private const val GUTENBERG_ID = "default-gutenberg"
internal const val GUTENBERG_OLD_URL = "https://www.gutenberg.org/ebooks.opds/"

// the most downloaded books in English, as the start page of Gutenberg mixes all languages
internal const val GUTENBERG_URL =
  "https://www.gutenberg.org/ebooks/search.opds/?query=l.en&sort_order=downloads"
internal const val WOLNE_LEKTURY_ID = "default-wolne-lektury"

// marks in the seeded ids that the catalogs of earlier versions were brought up to English
internal const val ENGLISH_MIGRATION = "migration-english-catalogs"

internal val defaultCatalogs: List<OpdsCatalog> =
  listOf(
    OpdsCatalog(
      id = GUTENBERG_ID,
      name = "Project Gutenberg",
      url = GUTENBERG_URL,
    ),
    OpdsCatalog(
      id = "default-internet-archive",
      name = "Internet Archive",
      url = "https://archive.org/services/opds",
    ),
    OpdsCatalog(
      id = "default-open-library",
      name = "Open Library",
      url = "https://openlibrary.org/opds/",
    ),
    OpdsCatalog(
      id = "default-unglue-it",
      name = "Unglue.it",
      url = "https://unglue.it/api/opds/",
    ),
    OpdsCatalog(
      id = "default-wikisource",
      name = "Wikisource",
      url = "https://ws-export.wmcloud.org/opds/en/Ready_for_export.xml",
    ),
    OpdsCatalog(
      id = "default-oapen",
      name = "OAPEN Library",
      url = "https://library.oapen.org/opds",
    ),
    OpdsCatalog(
      id = "default-doab",
      name = "Directory of Open Access Books",
      url = "https://directory.doabooks.org/opds",
    ),
    OpdsCatalog(
      id = "default-librivox",
      name = "LibriVox",
      url = LIBRIVOX_URL,
      type = CatalogType.LibriVox,
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
    migrateToEnglish()
    val seeded = seededStore.data.first().toSet()
    val missing = defaults.filter { it.id !in seeded }
    if (missing.isEmpty()) return
    catalogStore.updateData { catalogs ->
      val sites = catalogs.mapNotNull { it.site() }.toSet()
      catalogs + missing.filter { it.site() !in sites }
    }
    seededStore.updateData { it + missing.map(OpdsCatalog::id) }
  }

  // earlier versions added the Polish Wolne Lektury and the Gutenberg start page in all languages
  private suspend fun migrateToEnglish() {
    if (ENGLISH_MIGRATION in seededStore.data.first()) return
    catalogStore.updateData { catalogs ->
      catalogs
        .filterNot { it.id == WOLNE_LEKTURY_ID }
        .map {
          if (it.id == GUTENBERG_ID && it.url == GUTENBERG_OLD_URL) it.copy(url = GUTENBERG_URL)
          else it
        }
    }
    seededStore.updateData { it + ENGLISH_MIGRATION }
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
