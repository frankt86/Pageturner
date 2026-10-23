@file:OptIn(ExperimentalReadiumApi::class)

package voice.features.reader

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import voice.core.data.Ebook
import voice.core.data.ReaderFont
import voice.core.data.ReaderSettings
import voice.core.data.ReaderTheme
import voice.core.logging.api.Logger
import voice.core.ui.bookText
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import voice.core.strings.R as StringsR

/** An EPUB opened with Readium, the new book engine. */
internal class ReadiumBook(
  val publication: Publication,
  /** Where to start when the pages are shown again, e.g. after unfolding. */
  var locator: Locator?,
) {
  val navigatorFactory = EpubNavigatorFactory(publication)
}

internal suspend fun openReadiumBook(
  context: Context,
  uri: Uri,
  ebook: Ebook,
): ReadiumBook? {
  val url = uri.toAbsoluteUrl() ?: return null
  val httpClient = DefaultHttpClient()
  val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
  val asset = assetRetriever.retrieve(url).getOrElse { error ->
    Logger.w("Readium could not read the book: ${error::class.simpleName}")
    return null
  }
  val opener = PublicationOpener(
    publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null),
  )
  val publication = opener.open(asset, allowUserInteraction = false).getOrElse { error ->
    Logger.w("Readium could not open the book: ${error::class.simpleName}")
    asset.close()
    return null
  }
  if (!publication.conformsTo(Publication.Profile.EPUB)) {
    publication.close()
    return null
  }
  // without a place from the new engine, start about where the old one was
  val locator = ebook.readiumLocator?.toLocator()
    ?: ebook.progress.takeIf { it > 0F }?.let { publication.locateProgression(it.toDouble()) }
  return ReadiumBook(publication, locator)
}

private fun String.toLocator(): Locator? {
  return try {
    Locator.fromJSON(JSONObject(this))
  } catch (e: JSONException) {
    // not the exception, its message holds the JSON with the chapter title
    Logger.w("Could not read the saved place")
    null
  }
}

internal fun Locator.toJsonString(): String = toJSON().toString()

/**
 * After the activity is recreated, e.g. when folding, Android puts the book's pages back before the reader
 * can open the book again. They come back empty and the reader replaces them.
 */
fun readerFragmentFactory(): FragmentFactory = EpubNavigatorFragment.createDummyFactory()

private const val NAVIGATOR_TAG = "readium_navigator"

@Composable
internal fun ReadiumPages(
  book: ReadiumBook,
  settings: ReaderSettings,
  jumps: Flow<Link>,
  onLocatorChange: (Locator) -> Unit,
  onPageTurn: () -> Unit,
  onCenterTap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activity = LocalActivity.current as? FragmentActivity ?: return
  val context = LocalContext.current
  val preferences = settings.epubPreferences()
  val latestPreferences by rememberUpdatedState(preferences)
  val latestOnCenterTap by rememberUpdatedState(onCenterTap)
  val latestOnLocatorChange by rememberUpdatedState(onLocatorChange)
  val latestOnPageTurn by rememberUpdatedState(onPageTurn)
  var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
  val container = remember(context) {
    FragmentContainerView(context).apply {
      id = View.generateViewId()
      // the volume keys turn the pages in Compose, which only works while the web view can't take the focus
      descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
  }
  Box(modifier) {
    AndroidView(factory = { container }, modifier = Modifier.fillMaxSize())
    if (settings.volumeKeysTurnPages) {
      VolumeKeyPageTurns { step ->
        val current = navigator
        if (step > 0) current?.goForward() else current?.goBackward()
      }
    }
  }
  DisposableEffect(book, activity, container) {
    val fragmentManager = activity.supportFragmentManager
    fragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { stale ->
      fragmentManager.commitNow(allowStateLoss = true) { remove(stale) }
    }
    val fragment = book.navigatorFactory
      .createFragmentFactory(
        initialLocator = book.locator,
        initialPreferences = latestPreferences,
        configuration = EpubNavigatorFragment.Configuration(shouldApplyInsetsPadding = false),
      )
      .instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
    // the left and right edges turn the pages, so a tap anywhere else is in the middle
    fragment.addInputListener(DirectionalNavigationAdapter(fragment))
    fragment.addInputListener(
      object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
          latestOnCenterTap()
          return true
        }
      },
    )
    fragmentManager.commitNow(allowStateLoss = true) { add(container, fragment, NAVIGATOR_TAG) }
    navigator = fragment
    onDispose {
      navigator = null
      // when the state is saved, the activity is recreated and readerFragmentFactory takes over
      if (!fragmentManager.isStateSaved) {
        fragmentManager.commitNow { remove(fragment) }
      }
    }
  }
  LaunchedEffect(navigator, preferences) {
    navigator?.submitPreferences(preferences)
  }
  LaunchedEffect(navigator) {
    val current = navigator ?: return@LaunchedEffect
    current.currentLocator
      .onEach { latestOnLocatorChange(it) }
      .map { it.locations.totalProgression }
      .distinctUntilChanged()
      .drop(1)
      .collect { latestOnPageTurn() }
  }
  LaunchedEffect(navigator, jumps) {
    val current = navigator ?: return@LaunchedEffect
    jumps.collect { link -> current.go(link) }
  }
}

@Composable
private fun ReaderSettings.epubPreferences(): EpubPreferences {
  val colors = theme.pageColors()
  val paper = colors?.paper ?: MaterialTheme.colorScheme.surface
  val ink = colors?.ink ?: MaterialTheme.colorScheme.onSurface
  return EpubPreferences(
    backgroundColor = ReadiumColor(paper.toArgb()),
    textColor = ReadiumColor(ink.toArgb()),
    theme = when {
      theme == ReaderTheme.Sepia -> ReadiumTheme.SEPIA
      paper.luminance() < 0.5F -> ReadiumTheme.DARK
      else -> ReadiumTheme.LIGHT
    },
    fontFamily = when (font) {
      ReaderFont.Serif -> ReadiumFontFamily.SERIF
      ReaderFont.Sans -> ReadiumFontFamily.SANS_SERIF
    },
    // the web view's text is 16 px at 100 %
    fontSize = textSize / 16.0,
    lineHeight = lineSpacing.toDouble(),
    // Readium's margins are 20 px at 1
    pageMargins = margin / 20.0,
    // the line spacing only applies without the publisher's own
    publisherStyles = false,
    scroll = false,
  )
}

@Composable
internal fun ReadiumContentsSheet(
  book: ReadiumBook,
  onLinkClick: (Link) -> Unit,
  onDismiss: () -> Unit,
) {
  val entries = remember(book) {
    val publication = book.publication
    publication.tableOfContents.ifEmpty { publication.readingOrder }.flattenWithDepth()
  }
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Text(
      text = stringResource(StringsR.string.reader_contents),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    LazyColumn {
      itemsIndexed(entries) { index, entry ->
        val depth = entry.first
        val link = entry.second
        Text(
          text = link.title ?: stringResource(StringsR.string.reader_chapter, index + 1),
          style = MaterialTheme.typography.bodyLarge.bookText(),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onLinkClick(link) }
            .padding(start = 24.dp + 16.dp * depth, end = 24.dp, top = 14.dp, bottom = 14.dp),
        )
      }
    }
  }
}

private fun List<Link>.flattenWithDepth(depth: Int = 0): List<Pair<Int, Link>> {
  return flatMap { link -> listOf(depth to link) + link.children.flattenWithDepth(depth + 1) }
}
