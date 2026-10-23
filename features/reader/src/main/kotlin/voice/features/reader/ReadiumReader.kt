@file:OptIn(ExperimentalReadiumApi::class)

package voice.features.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.toUri
import voice.core.data.ReaderFont
import voice.core.data.ReaderSettings
import voice.core.data.ReaderTheme
import voice.core.logging.api.Logger
import voice.core.ui.bookText
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import voice.core.strings.R as StringsR

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
  jumps: Flow<Locator>,
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
        listener = object : EpubNavigatorFragment.Listener {
          override fun onExternalLinkActivated(url: AbsoluteUrl) {
            openInBrowser(context, url)
          }
        },
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
    jumps.collect { locator -> current.go(locator) }
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

private fun openInBrowser(
  context: Context,
  url: AbsoluteUrl,
) {
  val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
  try {
    context.startActivity(intent)
  } catch (e: ActivityNotFoundException) {
    // not the exception, its message holds the link
    Logger.w("No app opens the link")
  }
}

/** The page bar of the new book engine, with Readium's positions as the pages. */
@Composable
internal fun BoxScope.ReadiumPageBar(
  book: ReadiumBook,
  visible: Boolean,
  onJump: (Locator) -> Unit,
) {
  if (book.positions.isEmpty()) return
  BottomBar(visible = visible) {
    val current = book.positionIndex(book.locator)
    PageBar(
      pageCount = book.positions.size,
      currentIndex = current,
      chapterTitle = { index -> book.chapterTitle(book.positions[index]).orEmpty() },
      pagesLeftInChapter = book.pagesLeftInChapter(current),
      onJump = { index -> onJump(book.positions[index]) },
    )
  }
}

@Composable
internal fun ReadiumContentsSheet(
  book: ReadiumBook,
  onJump: (Locator) -> Unit,
  onBookmarkDelete: (Locator) -> Unit,
  onDismiss: () -> Unit,
) {
  val tabs = listOf(ContentsTab.Contents, ContentsTab.Bookmarks)
  var selectedTab by rememberSaveable { mutableStateOf(ContentsTab.Contents) }
  ModalBottomSheet(onDismissRequest = onDismiss) {
    PrimaryTabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
      tabs.forEach { tab ->
        Tab(
          selected = tab == selectedTab,
          onClick = { selectedTab = tab },
          text = { Text(tabTitle(tab)) },
        )
      }
    }
    when (selectedTab) {
      ContentsTab.Bookmarks -> ReadiumBookmarks(book, onJump, onBookmarkDelete)
      else -> ReadiumChapters(book, onJump)
    }
  }
}

@Composable
private fun ReadiumChapters(
  book: ReadiumBook,
  onJump: (Locator) -> Unit,
) {
  val currentChapter = book.locator?.let { book.chapterTitle(it) }
  LazyColumn {
    itemsIndexed(book.tableOfContents) { index, entry ->
      val depth = entry.first
      val link = entry.second
      val current = link.title != null && link.title == currentChapter
      Text(
        text = link.title ?: stringResource(StringsR.string.reader_chapter, index + 1),
        style = MaterialTheme.typography.bodyLarge.bookText(),
        fontWeight = if (current) FontWeight.Bold else null,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { book.publication.locatorFromLink(link)?.let(onJump) }
          .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
          .padding(start = 24.dp + 16.dp * depth, end = 24.dp, top = 14.dp, bottom = 14.dp),
      )
    }
  }
}

@Composable
private fun ReadiumBookmarks(
  book: ReadiumBook,
  onJump: (Locator) -> Unit,
  onBookmarkDelete: (Locator) -> Unit,
) {
  if (book.bookmarks.isEmpty()) {
    EmptyMarks(stringResource(StringsR.string.reader_bookmarks_empty))
    return
  }
  val deleteLabel = stringResource(StringsR.string.reader_bookmark_delete)
  val pageCount = book.positions.size
  LazyColumn {
    items(book.bookmarks) { bookmark ->
      val page = book.positionIndex(bookmark) + 1
      MarkRow(
        title = book.chapterTitle(bookmark) ?: stringResource(StringsR.string.reader_page, page, pageCount),
        text = AnnotatedString(stringResource(StringsR.string.reader_page, page, pageCount)),
        deleteLabel = deleteLabel,
        onClick = { onJump(bookmark) },
        onDelete = { onBookmarkDelete(bookmark) },
      )
    }
  }
}
