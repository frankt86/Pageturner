@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.opds.feed

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavEntry
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import voice.core.common.rootGraphAs
import voice.core.data.BookId
import voice.core.opds.feed.OpdsNavigation
import voice.core.ui.CenteredContent
import voice.core.ui.ShapedIcon
import voice.core.ui.VoiceTheme
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.core.ui.plus
import voice.core.ui.segmentedShape
import voice.features.opds.folder.DownloadFolderSheet
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR

@ContributesTo(AppScope::class)
interface OpdsFeedGraph {
  val opdsFeedViewModelFactory: OpdsFeedViewModel.Factory
}

@BindingContainer
@ContributesTo(AppScope::class)
object OpdsFeedProvider {

  @Provides
  @IntoSet
  fun opdsFeedNavEntryProvider(): NavEntryProvider<*> = NavEntryProvider<Destination.OpdsFeed> { key ->
    NavEntry(key) {
      OpdsFeedScreen(key)
    }
  }
}

@Composable
private fun OpdsFeedScreen(destination: Destination.OpdsFeed) {
  val viewModel = retain(destination) {
    rootGraphAs<OpdsFeedGraph>().opdsFeedViewModelFactory.create(destination)
  }
  OpdsFeedScreen(
    viewState = viewModel.viewState(),
    listener = viewModel,
  )
}

interface FeedListener {
  fun onBack()
  fun onRetry()
  fun onSearch(query: String)
  fun onLoadMore()
  fun onNavigationClick(item: OpdsNavigation)
  fun onPublicationClick(id: String)
  fun onSheetDismiss()
  fun onDownload(id: String)
  fun onCancelDownload(id: String)
  fun onRead(uri: Uri)
  fun onDownloadAudiobook(id: String)
  fun onCancelAudiobookDownload(id: String)
  fun onListen(bookId: BookId)
  fun onAskedForNotifications()
  fun onDownloadFolderChoose(uri: Uri)
  fun onDownloadFolderPick(uri: Uri)
  fun onDownloadFolderDismiss()
}

@Composable
private fun OpdsFeedScreen(
  viewState: OpdsFeedViewState,
  listener: FeedListener,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = viewState.title.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
          IconButton(onClick = listener::onBack) {
            Icon(
              modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1F else 1F },
              imageVector = VoiceIcons.ArrowBack,
              contentDescription = stringResource(StringsR.string.common_action_back),
            )
          }
        },
      )
    },
  ) { contentPadding ->
    when (viewState.status) {
      FeedStatus.Loading -> Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(contentPadding),
        contentAlignment = Alignment.Center,
      ) {
        LoadingIndicator(Modifier.size(56.dp))
      }
      FeedStatus.Failed -> FeedError(
        onRetry = listener::onRetry,
        modifier = Modifier.padding(contentPadding),
      )
      FeedStatus.Loaded -> FeedList(
        viewState = viewState,
        listener = listener,
        contentPadding = contentPadding,
      )
    }
    // the download of an audiobook shows its progress in a notification, which needs the permission since Android 13
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = {},
    )
    val context = LocalContext.current
    val downloadFolder = viewState.downloadFolder
    // the book's sheet comes back once the folder is chosen, with the download running
    if (downloadFolder != null) {
      DownloadFolderSheet(
        state = downloadFolder,
        onChoose = listener::onDownloadFolderChoose,
        onPick = listener::onDownloadFolderPick,
        onDismiss = listener::onDownloadFolderDismiss,
      )
    } else {
      viewState.selected?.let { selected ->
        PublicationSheet(
          publication = selected,
          onDismiss = listener::onSheetDismiss,
          onDownload = { listener.onDownload(selected.id) },
          onCancelDownload = { listener.onCancelDownload(selected.id) },
          onRead = listener::onRead,
          onDownloadAudiobook = {
            if (viewState.askForNotifications && !context.canPostNotifications()) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              listener.onAskedForNotifications()
            }
            listener.onDownloadAudiobook(selected.id)
          },
          onCancelAudiobookDownload = { listener.onCancelAudiobookDownload(selected.id) },
          onListen = listener::onListen,
        )
      }
    }
  }
}

@Composable
private fun FeedList(
  viewState: OpdsFeedViewState,
  listener: FeedListener,
  contentPadding: PaddingValues,
) {
  val listState = rememberLazyListState()
  val currentListener by rememberUpdatedState(listener)
  val canLoadMore = viewState.hasMore && !viewState.loadingMore && !viewState.loadMoreFailed
  LaunchedEffect(listState, canLoadMore) {
    if (!canLoadMore) return@LaunchedEffect
    snapshotFlow {
      val layout = listState.layoutInfo
      val last = layout.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
      last >= layout.totalItemsCount - 6
    }
      .distinctUntilChanged()
      .filter { it }
      .collect { currentListener.onLoadMore() }
  }
  CenteredContent(Modifier.fillMaxSize()) { horizontalPadding ->
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = contentPadding + PaddingValues(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      if (viewState.canSearch) {
        item(key = "search") {
          SearchField(
            searching = viewState.searching,
            failed = viewState.searchFailed,
            onSearch = listener::onSearch,
            modifier = Modifier.padding(bottom = 14.dp),
          )
        }
      }
      itemsIndexed(viewState.navigation, key = { index, _ -> "navigation$index" }) { index, item ->
        NavigationRow(
          item = item,
          shape = segmentedShape(index, viewState.navigation.size),
          onClick = { listener.onNavigationClick(item) },
        )
      }
      if (viewState.navigation.isNotEmpty() && viewState.publications.isNotEmpty()) {
        item(key = "divider") { Spacer(Modifier.height(14.dp)) }
      }
      itemsIndexed(viewState.publications, key = { _, publication -> "publication" + publication.id }) { index, publication ->
        PublicationRow(
          publication = publication,
          shape = segmentedShape(index, viewState.publications.size),
          onClick = { listener.onPublicationClick(publication.id) },
        )
      }
      item(key = "footer") {
        FeedFooter(
          viewState = viewState,
          onLoadMore = listener::onLoadMore,
        )
      }
    }
  }
}

@Composable
private fun FeedFooter(
  viewState: OpdsFeedViewState,
  onLoadMore: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    when {
      viewState.loadingMore -> LoadingIndicator(Modifier.size(40.dp))
      viewState.loadMoreFailed -> TextButton(onClick = onLoadMore) {
        Text(stringResource(StringsR.string.audiobookshelf_settings_try_again))
      }
      viewState.navigation.isEmpty() && viewState.publications.isEmpty() -> Text(
        text = stringResource(StringsR.string.history_empty_title),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SearchField(
  searching: Boolean,
  failed: Boolean,
  onSearch: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var query by rememberSaveable { mutableStateOf("") }
  OutlinedTextField(
    modifier = modifier.fillMaxWidth(),
    value = query,
    onValueChange = { query = it },
    singleLine = true,
    shape = CircleShape,
    placeholder = { Text(stringResource(StringsR.string.opds_feed_search)) },
    leadingIcon = { Icon(VoiceIcons.Search, contentDescription = null) },
    trailingIcon = if (searching) {
      { LoadingIndicator(Modifier.size(32.dp)) }
    } else {
      null
    },
    isError = failed,
    supportingText = if (failed) {
      { Text(stringResource(StringsR.string.opds_feed_error)) }
    } else {
      null
    },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
  )
}

@Composable
private fun NavigationRow(
  item: OpdsNavigation,
  shape: RoundedCornerShape,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(colors.surfaceContainer, shape)
      .clickable(onClick = onClick)
      .heightIn(min = 64.dp)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ShapedIcon(
      icon = VoiceIcons.CollectionsBookmark,
      shape = MaterialShapes.Cookie6Sided,
      containerColor = colors.secondaryContainer,
      contentColor = colors.onSecondaryContainer,
      size = 40.dp,
    )
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1F)) {
      Text(
        text = item.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      item.summary?.let { summary ->
        Text(
          text = summary,
          style = MaterialTheme.typography.bodyMedium,
          color = colors.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Spacer(Modifier.width(8.dp))
    Icon(
      modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1F else 1F },
      imageVector = VoiceIcons.ChevronRight,
      contentDescription = null,
      tint = colors.onSurfaceVariant,
    )
  }
}

@Composable
private fun PublicationRow(
  publication: PublicationViewState,
  shape: RoundedCornerShape,
  onClick: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(colors.surfaceContainer, shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CatalogCover(
      image = publication.thumbnail,
      modifier = Modifier
        .width(52.dp)
        .height(76.dp)
        .clip(RoundedCornerShape(8.dp)),
    )
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1F)) {
      Text(
        text = publication.title,
        style = MaterialTheme.typography.titleMedium.bookText(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      publication.authors?.let { authors ->
        Text(
          text = authors,
          style = MaterialTheme.typography.bodyMedium.bookText(),
          color = colors.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (publication.isDownloading) {
      Spacer(Modifier.width(8.dp))
      LoadingIndicator(Modifier.size(32.dp))
    } else if (publication.isDownloaded) {
      Spacer(Modifier.width(8.dp))
      Icon(
        imageVector = VoiceIcons.DownloadDone,
        contentDescription = stringResource(StringsR.string.opds_entry_downloaded),
        tint = colors.primary,
      )
    }
  }
}

@Composable
internal fun CatalogCover(
  image: CoverImage?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val request = remember(image, context) {
    ImageRequest.Builder(context)
      .data(image?.url)
      .apply { image?.authorization?.let { addHeader("Authorization", it) } }
      .crossfade(true)
      .build()
  }
  AsyncImage(
    modifier = modifier,
    model = request,
    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHighest),
    error = painterResource(UiR.drawable.album_art),
    contentScale = ContentScale.Crop,
    contentDescription = null,
  )
}

@Composable
private fun FeedError(
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    ShapedIcon(
      icon = VoiceIcons.CloudOff,
      shape = MaterialShapes.Cookie9Sided,
      containerColor = colors.errorContainer,
      contentColor = colors.onErrorContainer,
      size = 72.dp,
    )
    Spacer(Modifier.height(20.dp))
    Text(
      text = stringResource(StringsR.string.opds_feed_error),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = onRetry) {
      Text(stringResource(StringsR.string.audiobookshelf_settings_try_again))
    }
  }
}

@Composable
@Preview
private fun OpdsFeedPreview() {
  VoiceTheme {
    OpdsFeedScreen(
      viewState = OpdsFeedViewState(
        title = "Recently added",
        status = FeedStatus.Loaded,
        navigation = listOf(
          OpdsNavigation(title = "Authors", url = "https://example.com/authors", summary = "Books by author"),
          OpdsNavigation(title = "Series", url = "https://example.com/series", summary = null),
        ),
        publications = listOf(
          PublicationViewState(
            id = "1",
            title = "Persuasion",
            authors = "Jane Austen",
            summary = null,
            thumbnail = null,
            cover = null,
            download = EntryDownload.Downloaded,
          ),
          PublicationViewState(
            id = "2",
            title = "The Left Hand of Darkness",
            authors = "Ursula K. Le Guin",
            summary = null,
            thumbnail = null,
            cover = null,
            download = EntryDownload.Available("EPUB"),
          ),
          PublicationViewState(
            id = "3",
            title = "The Time Machine",
            authors = "H. G. Wells",
            summary = null,
            thumbnail = null,
            cover = null,
            download = null,
            audiobook = EntryDownload.Downloading(progress = 0.4F),
          ),
        ),
        canSearch = true,
        searching = false,
        searchFailed = false,
        hasMore = false,
        loadingMore = false,
        loadMoreFailed = false,
        selected = null,
      ),
      listener = PreviewListener,
    )
  }
}

@Composable
@Preview
private fun OpdsFeedErrorPreview() {
  VoiceTheme {
    OpdsFeedScreen(
      viewState = OpdsFeedViewState(
        title = "Calibre-Web",
        status = FeedStatus.Failed,
        navigation = emptyList(),
        publications = emptyList(),
        canSearch = false,
        searching = false,
        searchFailed = false,
        hasMore = false,
        loadingMore = false,
        loadMoreFailed = false,
        selected = null,
      ),
      listener = PreviewListener,
    )
  }
}

private object PreviewListener : FeedListener {
  override fun onBack() {}
  override fun onRetry() {}
  override fun onSearch(query: String) {}
  override fun onLoadMore() {}
  override fun onNavigationClick(item: OpdsNavigation) {}
  override fun onPublicationClick(id: String) {}
  override fun onSheetDismiss() {}
  override fun onDownload(id: String) {}
  override fun onCancelDownload(id: String) {}
  override fun onRead(uri: Uri) {}
  override fun onDownloadAudiobook(id: String) {}
  override fun onCancelAudiobookDownload(id: String) {}
  override fun onListen(bookId: BookId) {}
  override fun onAskedForNotifications() {}
  override fun onDownloadFolderChoose(uri: Uri) {}
  override fun onDownloadFolderPick(uri: Uri) {}
  override fun onDownloadFolderDismiss() {}
}

private fun Context.canPostNotifications(): Boolean {
  return Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
