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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import kotlinx.coroutines.delay
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
import org.readium.r2.navigator.epub.css.Length
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.ImageFilter
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.toUri
import voice.core.data.HighlightColor
import voice.core.data.PageTransition
import voice.core.data.ProgressVisibility
import voice.core.data.ReaderImageFilter
import voice.core.data.ReaderPages
import voice.core.data.ReaderSettings
import voice.core.data.ReaderTextAlign
import voice.core.data.ReaderTheme
import voice.core.data.ReadingPace
import voice.core.logging.api.Logger
import voice.core.ui.bookText
import org.readium.r2.navigator.epub.css.Color as ReadiumCssColor
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
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
  val pageColors = settings.pageColors()
  val preferences = settings.epubPreferences(pageColors)
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
    HighlightColor.entries.map { highlightColorName(it, settings.highlightNames) } +
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
        backColor = pageColors?.paper ?: MaterialTheme.colorScheme.surface,
        onDone = turns::onCurlDone,
        onCancel = turns::onCurlCancel,
        modifier = Modifier.fillMaxSize(),
      )
    }
    PaperTexture(
      intensity = settings.paperTexture,
      color = pageColors?.ink ?: MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.fillMaxSize(),
    )
    // while the web view has no focus, the volume keys come to Compose
    if (volumeKeysTurnPages) {
      VolumeKeyPageTurns { step -> turns.turn(step) }
    }
  }
  // Readium takes these only when its pages are made, so changing them makes them anew
  // colors wait until they stop changing, so dragging a color slider doesn't make the pages anew all the time
  val link = settled(pageColors?.link)
  val selectionColor = settled(pageColors?.selection)
  val cssProperties = RsProperties(
    // about 65 characters per line
    maxLineLength = if (settings.shortLines) Length.Rem(32.0) else null,
    colGap = settings.pageGap?.let { Length.Px(it.toDouble()) },
    linkColor = link?.let { ReadiumCssColor.Int(it.toArgb()) },
    visitedColor = link?.let { ReadiumCssColor.Int(it.toArgb()) },
    selectionBackgroundColor = selectionColor?.let { ReadiumCssColor.Int(it.toArgb()) },
  )
  // Readium draws highlights at 0.3 where the old engine draws them at 0.45
  val highlightAlpha = settled(settings.highlightOpacity) * 0.3 / 0.45
  val customFont = settings.customFont
  DisposableEffect(book, activity, container, cssProperties, customFont, highlightAlpha) {
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
          readiumCssRsProperties = cssProperties,
          decorationTemplates = HtmlDecorationTemplates.defaultTemplates(alpha = highlightAlpha),
          selectionActionModeCallback = selectionCallback,
          shouldApplyInsetsPadding = false,
        ).apply { addReaderFonts(customFont) },
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
  val markTint = (pageColors?.highlight ?: MaterialTheme.colorScheme.primaryContainer).toArgb()
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
private fun <T> settled(value: T): T {
  var settled by remember { mutableStateOf(value) }
  LaunchedEffect(value) {
    delay(600)
    settled = value
  }
  return settled
}

@Composable
private fun ReaderSettings.epubPreferences(colors: PageColors?): EpubPreferences {
  val paper = colors?.paper ?: MaterialTheme.colorScheme.surface
  val ink = colors?.ink ?: MaterialTheme.colorScheme.onSurface
  val sepia = shownTheme() == ReaderTheme.Sepia
  return EpubPreferences(
    backgroundColor = ReadiumColor(paper.toArgb()),
    textColor = ReadiumColor(ink.toArgb()),
    theme = when {
      sepia -> ReadiumTheme.SEPIA
      paper.luminance() < 0.5F -> ReadiumTheme.DARK
      else -> ReadiumTheme.LIGHT
    },
    // only applies to dark pages
    imageFilter = when (imageFilter) {
      ReaderImageFilter.Original -> null
      ReaderImageFilter.Dimmed -> ImageFilter.DARKEN
      ReaderImageFilter.Inverted -> ImageFilter.INVERT
    },
    fontFamily = readiumFontFamily(),
    // the web view's text is 16 px at 100 %
    fontSize = textSize / 16.0,
    // Readium's weight is a multiple of the regular one
    fontWeight = fontWeight?.takeUnless { publisherStyles }?.let { it / 400.0 },
    // the settings below only apply without the publisher's styles
    lineHeight = lineSpacing.toDouble(),
    // Readium halves it
    letterSpacing = letterSpacing?.let { it * 2.0 },
    wordSpacing = wordSpacing?.toDouble(),
    paragraphSpacing = paragraphSpacing?.let { it * lineSpacing.toDouble() },
    paragraphIndent = paragraphIndent?.toDouble(),
    textAlign = when (textAlign) {
      ReaderTextAlign.Left -> ReadiumTextAlign.LEFT
      ReaderTextAlign.Right -> ReadiumTextAlign.RIGHT
      ReaderTextAlign.Justify -> ReadiumTextAlign.JUSTIFY
      // Readium can't center the text
      ReaderTextAlign.Center, null -> null
    },
    // at their defaults, these leave it to the book as before
    hyphens = hyphens.takeIf { it },
    ligatures = ligatures.takeUnless { it },
    // Readium's margins are 20 px at 1
    pageMargins = margin / 20.0,
    publisherStyles = publisherStyles,
    scroll = false,
    columnCount = when (pages) {
      ReaderPages.Auto -> ColumnCount.AUTO
      ReaderPages.One -> ColumnCount.ONE
      ReaderPages.Two -> ColumnCount.TWO
    },
    // the same choice for books with fixed pages
    spread = when (pages) {
      ReaderPages.Auto -> null
      ReaderPages.One -> Spread.NEVER
      ReaderPages.Two -> Spread.ALWAYS
    },
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
  settings: ReaderSettings,
  characters: Int,
  pace: ReadingPace,
  visible: Boolean,
  onJump: (Locator) -> Unit,
) {
  if (book.positions.isEmpty()) return
  BottomBar(visible = visible && settings.progressVisibility != ProgressVisibility.Hidden) {
    val current = book.positionIndex(book.locator)
    val chapters = remember(book) { book.chapterStarts.map { fractionOf(it, book.positions.size) } }
    val marks = if (settings.progressMarks) {
      val bookmarks = book.bookmarks
      val highlights = book.highlights
      remember(book, chapters, bookmarks, highlights) {
        BarMarks(
          chapters = chapters,
          bookmarks = bookmarks.map { fractionOf(book.positionIndex(it), book.positions.size) },
          highlights = highlights.map { fractionOf(book.positionIndex(it.locator), book.positions.size) },
        )
      }
    } else {
      null
    }
    PageBar(
      pageCount = book.positions.size,
      currentIndex = current,
      chapterTitle = { index -> book.chapterTitle(book.positions[index]).orEmpty() },
      progress = { index ->
        book.readingProgress(
          index = index,
          locator = book.locator.takeIf { index == current },
          bodyOnly = settings.progressBodyOnly,
          characters = characters,
          pace = pace,
        )
      },
      style = settings.progressBar,
      chapters = chapters,
      marks = marks,
      onJump = { index -> onJump(book.positions[index]) },
    )
  }
}

/** The chapter and what [ReaderSettings.footer] picks under the pages, with a thin line of the book's progress. */
@Composable
internal fun ReadiumFooter(
  book: ReadiumBook,
  settings: ReaderSettings,
  characters: Int,
  pace: ReadingPace,
  modifier: Modifier = Modifier,
) {
  if (book.positions.isEmpty()) return
  val current = book.positionIndex(book.locator)
  val progress = book.readingProgress(
    index = current,
    locator = book.locator,
    bodyOnly = settings.progressBodyOnly,
    characters = characters,
    pace = pace,
  )
  val chapters = remember(book) { book.chapterStarts.map { fractionOf(it, book.positions.size) } }
  ReaderPageTheme(settings.pageColors()) {
    Column(
      modifier
        .background(MaterialTheme.colorScheme.surface)
        .padding(horizontal = settings.margin.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .height(28.dp),
      ) {
        Text(
          text = book.locator?.let { book.chapterTitle(it) }.orEmpty(),
          style = MaterialTheme.typography.labelMedium.bookText(),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(1F)
            .padding(end = 12.dp),
        )
        footerText(settings.footer, progress)?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      ProgressLine(
        fraction = progress.bookFraction,
        style = settings.progressBar,
        chapters = chapters,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp),
      )
    }
  }
}

@Composable
internal fun ReadiumContentsSheet(
  book: ReadiumBook,
  onJump: (Locator) -> Unit,
  onBookmarkDelete: (Locator) -> Unit,
  onHighlightDelete: (LocatedHighlight) -> Unit,
  highlightOpacity: Float,
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
      ContentsTab.Highlights -> ReadiumHighlights(book, highlightOpacity, onJump, onHighlightDelete)
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
  opacity: Float,
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
          withStyle(SpanStyle(background = highlight.color.background(opacity))) { append(quote) }
        },
        note = highlight.note,
        deleteLabel = deleteLabel,
        onClick = { onJump(highlight.locator) },
        onDelete = { onHighlightDelete(highlight) },
      )
    }
  }
}
