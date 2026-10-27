package voice.features.opds.feed

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.opds.FeedResult
import voice.core.opds.OpdsCatalog
import voice.core.opds.OpdsCatalogs
import voice.core.opds.OpdsClient
import voice.core.opds.download.OpdsDownloadState
import voice.core.opds.download.OpdsDownloads
import voice.core.opds.download.OpdsEntryKey
import voice.core.opds.feed.OpdsNavigation
import voice.core.opds.feed.OpdsPublication
import voice.core.opds.feed.OpdsSearch
import voice.navigation.Destination
import voice.navigation.Navigator

@AssistedInject
class OpdsFeedViewModel(
  private val catalogs: OpdsCatalogs,
  private val client: OpdsClient,
  private val downloads: OpdsDownloads,
  private val navigator: Navigator,
  dispatcherProvider: DispatcherProvider,
  @Assisted
  private val destination: Destination.OpdsFeed,
) : FeedListener {

  private val scope = MainScope(dispatcherProvider)
  private var loadJob: Job? = null

  private var catalog by mutableStateOf<OpdsCatalog?>(null)
  private var status by mutableStateOf(FeedStatus.Loading)
  private var feedTitle by mutableStateOf<String?>(null)
  private var navigation by mutableStateOf<List<OpdsNavigation>>(emptyList())
  private var publications by mutableStateOf<List<OpdsPublication>>(emptyList())
  private var search by mutableStateOf<OpdsSearch?>(null)
  private var nextUrl by mutableStateOf<String?>(null)
  private var loadingMore by mutableStateOf(false)
  private var loadMoreFailed by mutableStateOf(false)
  private var searching by mutableStateOf(false)
  private var searchFailed by mutableStateOf(false)
  private var selectedId by mutableStateOf<String?>(null)

  @Composable
  internal fun viewState(): OpdsFeedViewState {
    LaunchedEffect(Unit) {
      if (loadJob == null) load()
    }
    val downloaded = remember { downloads.downloaded(destination.catalogId) }.collectAsState(initial = emptyMap()).value
    val downloadStates = downloads.states.collectAsState().value
    val current = catalog
    val items = publications.map { publication ->
      publication.toViewState(
        catalog = current,
        downloaded = downloaded[publication.id],
        state = downloadStates[OpdsEntryKey(destination.catalogId, publication.id)],
      )
    }
    return OpdsFeedViewState(
      title = destination.title ?: feedTitle ?: current?.name,
      status = status,
      navigation = navigation,
      publications = items,
      canSearch = search != null,
      searching = searching,
      searchFailed = searchFailed,
      hasMore = nextUrl != null,
      loadingMore = loadingMore,
      loadMoreFailed = loadMoreFailed,
      selected = items.firstOrNull { it.id == selectedId },
    )
  }

  private fun load() {
    loadJob = scope.launch {
      status = FeedStatus.Loading
      val found = catalogs.catalog(destination.catalogId)
      catalog = found
      if (found == null) {
        status = FeedStatus.Failed
        return@launch
      }
      val result = client.feed(found, destination.url ?: found.feedUrl)
      if (result is FeedResult.Success) {
        feedTitle = result.feed.title
        navigation = result.feed.navigation
        publications = result.feed.publications.distinctBy { it.id }
        search = result.feed.search
        nextUrl = result.feed.nextUrl
        status = FeedStatus.Loaded
      } else {
        status = FeedStatus.Failed
      }
    }
  }

  override fun onRetry() {
    loadJob?.cancel()
    load()
  }

  override fun onLoadMore() {
    val url = nextUrl ?: return
    val current = catalog ?: return
    if (loadingMore) return
    loadingMore = true
    loadMoreFailed = false
    scope.launch {
      val result = client.feed(current, url)
      if (result is FeedResult.Success) {
        val known = publications.mapTo(HashSet()) { it.id }
        publications = publications + result.feed.publications.filter { it.id !in known }.distinctBy { it.id }
        navigation = navigation + result.feed.navigation
        // a server that links a page to itself would never end
        nextUrl = result.feed.nextUrl?.takeIf { it != url }
      } else {
        loadMoreFailed = true
      }
      loadingMore = false
    }
  }

  override fun onSearch(query: String) {
    val currentSearch = search ?: return
    val current = catalog ?: return
    if (query.isBlank() || searching) return
    searching = true
    searchFailed = false
    scope.launch {
      val url = client.searchUrl(current, currentSearch, query)
      searching = false
      if (url == null) {
        searchFailed = true
      } else {
        navigator.goTo(Destination.OpdsFeed(catalogId = current.id, url = url, title = query.trim()))
      }
    }
  }

  override fun onNavigationClick(item: OpdsNavigation) {
    navigator.goTo(Destination.OpdsFeed(catalogId = destination.catalogId, url = item.url, title = item.title))
  }

  override fun onPublicationClick(id: String) {
    selectedId = id
  }

  override fun onSheetDismiss() {
    selectedId = null
  }

  override fun onDownload(id: String) {
    val current = catalog ?: return
    val publication = publications.firstOrNull { it.id == id } ?: return
    downloads.download(current, publication)
  }

  override fun onCancelDownload(id: String) {
    downloads.cancel(OpdsEntryKey(destination.catalogId, id))
  }

  override fun onRead(uri: Uri) {
    selectedId = null
    navigator.goTo(Destination.Reader(uri))
  }

  override fun onBack() {
    navigator.goBack()
  }

  @AssistedFactory
  interface Factory {
    fun create(destination: Destination.OpdsFeed): OpdsFeedViewModel
  }
}

private fun OpdsPublication.toViewState(
  catalog: OpdsCatalog?,
  downloaded: Uri?,
  state: OpdsDownloadState?,
): PublicationViewState {
  val acquisition = preferredAcquisition
  return PublicationViewState(
    id = id,
    title = title,
    authors = authors.joinToString(", ").ifEmpty { null },
    summary = summary,
    thumbnail = (thumbnailUrl ?: coverUrl)?.let { CoverImage(it, catalog?.authorizationFor(it)) },
    cover = (coverUrl ?: thumbnailUrl)?.let { CoverImage(it, catalog?.authorizationFor(it)) },
    download = when {
      downloaded != null -> EntryDownload.Downloaded(downloaded)
      state is OpdsDownloadState.Downloading -> EntryDownload.Downloading(state.progress)
      state == OpdsDownloadState.Failed -> EntryDownload.Failed
      acquisition != null -> EntryDownload.Available(acquisition.label)
      else -> EntryDownload.Unavailable(acquisitions.map { it.label }.distinct().joinToString(", "))
    },
  )
}

internal enum class FeedStatus {
  Loading,
  Loaded,
  Failed,
}

internal data class OpdsFeedViewState(
  val title: String?,
  val status: FeedStatus,
  val navigation: List<OpdsNavigation>,
  val publications: List<PublicationViewState>,
  val canSearch: Boolean,
  val searching: Boolean,
  val searchFailed: Boolean,
  val hasMore: Boolean,
  val loadingMore: Boolean,
  val loadMoreFailed: Boolean,
  val selected: PublicationViewState?,
)

internal data class PublicationViewState(
  val id: String,
  val title: String,
  val authors: String?,
  val summary: String?,
  val thumbnail: CoverImage?,
  val cover: CoverImage?,
  val download: EntryDownload,
)

/** An image of the catalog, which may need the password to load. */
internal data class CoverImage(
  val url: String,
  val authorization: String?,
)

internal sealed interface EntryDownload {
  /** [format] is what downloads, like EPUB. */
  data class Available(val format: String) : EntryDownload
  data class Downloading(val progress: Float?) : EntryDownload
  data object Failed : EntryDownload
  data class Downloaded(val uri: Uri) : EntryDownload

  /** Comes only in formats Pageturner can't read, like CBZ. */
  data class Unavailable(val formats: String) : EntryDownload
}
