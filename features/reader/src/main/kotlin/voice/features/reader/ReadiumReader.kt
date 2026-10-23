@file:OptIn(ExperimentalReadiumApi::class)

package voice.features.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.toUri
import voice.core.data.HighlightColor
import voice.core.data.PageTransition
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
  volumeKeysTurnPages: Boolean,
  transition: PageTransition,
  jumps: Flow<Locator>,
  onLocatorChange: (Locator) -> Unit,
  onPageTurn: () -> Unit,
  onCenterTap: () -> Unit,
  onHighlightAdd: (Locator, HighlightColor) -> Unit,
  onNoteAdd: (Locator) -> Unit,
  onHighlightClick: (LocatedHighlight) -> Unit,
  modifier: Modifier = Modifier,
) {
  val activity = LocalActivity.current as? FragmentActivity ?: return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val preferences = settings.epubPreferences()
  val latestPreferences by rememberUpdatedState(preferences)
  val latestBook by rememberUpdatedState(book)
  val latestOnCenterTap by rememberUpdatedState(onCenterTap)
  val latestOnLocatorChange by rememberUpdatedState(onLocatorChange)
  val latestOnPageTurn by rememberUpdatedState(onPageTurn)
  val latestOnHighlightAdd by rememberUpdatedState(onHighlightAdd)
  val latestOnNoteAdd by rememberUpdatedState(onNoteAdd)
  val latestOnHighlightClick by rememberUpdatedState(onHighlightClick)
  val latestVolumeKeysTurnPages by rememberUpdatedState(volumeKeysTurnPages)
  val selectionLabels by rememberUpdatedState(
    HighlightColor.entries.map { highlightColorName(it) } +
      stringResource(StringsR.string.reader_highlight_note) +
      stringResource(StringsR.string.reader_look_up),
  )
  var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
  val turns = remember { ReadiumTurns(scope) }
  val container = remember(context) { NavigatorContainer(context) }
  SideEffect {
    turns.transition = transition
    // Readium slides the pages itself
    container.interceptDrags = transition != PageTransition.Slide
    container.onDrag = turns::onDrag
    container.onVolumeKey = { step ->
      if (latestVolumeKeysTurnPages) {
        if (step != 0) turns.turn(step)
        true
      } else {
        false
      }
    }
  }
  Box(modifier) {
    AndroidView(factory = { container }, modifier = Modifier.fillMaxSize())
    turns.cover?.let { TurnCover(it, Modifier.fillMaxSize()) }
    turns.curl?.let { turn ->
      CurlOverlay(
        turn = turn,
        backColor = settings.theme.pageColors()?.paper ?: MaterialTheme.colorScheme.surface,
        onDone = turns::onCurlDone,
        onCancel = turns::onCurlCancel,
        modifier = Modifier.fillMaxSize(),
      )
    }
    // while the web view has no focus, the volume keys come to Compose
    if (volumeKeysTurnPages) {
      VolumeKeyPageTurns { step -> turns.turn(step) }
    }
  }
  DisposableEffect(book, activity, container) {
    val fragmentManager = activity.supportFragmentManager
    fragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { stale ->
      fragmentManager.commitNow(allowStateLoss = true) { remove(stale) }
    }
    var created: EpubNavigatorFragment? = null
    val selectionCallback = object : ActionMode.Callback {
      override fun onCreateActionMode(
        mode: ActionMode,
        menu: Menu,
      ): Boolean {
        selectionLabels.forEachIndexed { index, label -> menu.add(Menu.NONE, index, index, label) }
        return true
      }

      override fun onPrepareActionMode(
        mode: ActionMode,
        menu: Menu,
      ): Boolean = false

      override fun onActionItemClicked(
        mode: ActionMode,
        item: MenuItem,
      ): Boolean {
        val action = item.itemId
        scope.launch {
          val selection = created?.currentSelection()
          mode.finish()
          created?.clearSelection()
          val locator = selection?.locator ?: return@launch
          when (val color = HighlightColor.entries.getOrNull(action)) {
            null -> if (action == HighlightColor.entries.size) {
              latestOnNoteAdd(locator)
            } else {
              locator.text.highlight?.let { lookUp(context, it) }
            }
            else -> latestOnHighlightAdd(locator, color)
          }
        }
        return true
      }

      override fun onDestroyActionMode(mode: ActionMode) {}
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
        configuration = EpubNavigatorFragment.Configuration(
          selectionActionModeCallback = selectionCallback,
          shouldApplyInsetsPadding = false,
        ),
      )
      .instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
    created = fragment
    // the arrow keys of a keyboard turn the pages too
    fragment.addInputListener(DirectionalNavigationAdapter(fragment, tapEdges = emptySet()))
    fragment.addInputListener(
      object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
          val width = fragment.publicationView.width
          when {
            event.point.x > width * 2 / 3 -> turns.turnTowards(right = true)
            event.point.x < width / 3 -> turns.turnTowards(right = false)
            else -> latestOnCenterTap()
          }
          return true
        }
      },
    )
    fragment.addDecorationListener(
      HIGHLIGHTS,
      object : DecorableNavigator.Listener {
        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
          val highlight = latestBook.highlights.firstOrNull { it.id == event.decoration.id } ?: return false
          latestOnHighlightClick(highlight)
          return true
        }
      },
    )
    fragmentManager.commitNow(allowStateLoss = true) { add(container.fragments, fragment, NAVIGATOR_TAG) }
    navigator = fragment
    turns.navigator = fragment
    turns.view = container
    turns.window = activity.window
    onDispose {
      navigator = null
      turns.navigator = null
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
  val highlights = book.highlights
  LaunchedEffect(navigator, highlights) {
    navigator?.applyDecorations(
      highlights.map { highlight ->
        Decoration(
          id = highlight.id,
          locator = highlight.locator,
          style = Decoration.Style.Highlight(tint = highlight.color.ink().toArgb()),
        )
      },
      HIGHLIGHTS,
    )
  }
  val markTint = (settings.theme.pageColors()?.highlight ?: MaterialTheme.colorScheme.primaryContainer).toArgb()
  val searchMatch = book.searchMatch
  LaunchedEffect(navigator, searchMatch) {
    navigator?.applyDecorations(
      listOfNotNull(searchMatch?.let { Decoration(id = "match", locator = it, style = Decoration.Style.Highlight(tint = markTint)) }),
      SEARCH,
    )
  }
  val readingAloud = book.readingAloud
  LaunchedEffect(navigator, readingAloud) {
    val current = navigator ?: return@LaunchedEffect
    current.applyDecorations(
      listOfNotNull(readingAloud?.let { Decoration(id = "spoken", locator = it, style = Decoration.Style.Highlight(tint = markTint)) }),
      READ_ALOUD,
    )
    // turns the page when the reading goes on on the next one
    if (readingAloud != null) current.go(readingAloud)
  }
}

private const val HIGHLIGHTS = "highlights"
private const val SEARCH = "search"
private const val READ_ALOUD = "read_aloud"

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
  onHighlightDelete: (LocatedHighlight) -> Unit,
  onDismiss: () -> Unit,
) {
  val tabs = ContentsTab.entries
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
      ContentsTab.Contents -> ReadiumChapters(book, onJump)
      ContentsTab.Bookmarks -> ReadiumBookmarks(book, onJump, onBookmarkDelete)
      ContentsTab.Highlights -> ReadiumHighlights(book, onJump, onHighlightDelete)
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

@Composable
private fun ReadiumHighlights(
  book: ReadiumBook,
  onJump: (Locator) -> Unit,
  onHighlightDelete: (LocatedHighlight) -> Unit,
) {
  if (book.highlights.isEmpty()) {
    EmptyMarks(stringResource(StringsR.string.reader_highlights_empty))
    return
  }
  val deleteLabel = stringResource(StringsR.string.reader_highlight_delete)
  LazyColumn {
    items(book.highlights) { highlight ->
      val quote = highlight.locator.text.highlight.orEmpty().replace('\n', ' ')
      MarkRow(
        title = book.chapterTitle(highlight.locator).orEmpty(),
        text = buildAnnotatedString {
          withStyle(SpanStyle(background = highlight.color.background())) { append(quote) }
        },
        note = highlight.note,
        deleteLabel = deleteLabel,
        onClick = { onJump(highlight.locator) },
        onDelete = { onHighlightDelete(highlight) },
      )
    }
  }
}
