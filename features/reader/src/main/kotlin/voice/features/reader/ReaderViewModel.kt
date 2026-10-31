@file:OptIn(ExperimentalReadiumApi::class)

package voice.features.reader

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.search.search
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.EbookPosition
import voice.core.data.Highlight
import voice.core.data.HighlightColor
import voice.core.data.ReaderSettings
import voice.core.data.ReadingPace
import voice.core.data.ReadiumHighlight
import voice.core.data.store.EbooksStore
import voice.core.ebook.openSeekablePdf
import voice.core.ebook.parseEpub
import voice.core.logging.api.Logger
import voice.core.opds.komga.KomgaSync
import voice.core.opds.komga.RemotePlace
import voice.core.opds.komga.isSamePlaceAs
import voice.navigation.Navigator
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal sealed interface ReaderContent {
  val title: String
  val sectionCount: Int

  class Epub(
    override val title: String,
    val chapters: List<String>,
    val chapterTitles: List<String?>,
  ) : ReaderContent {
    override val sectionCount: Int get() = chapters.size
    val totalLength: Int = chapters.sumOf { it.length }
  }

  class Pdf(
    override val title: String,
    val pdf: PdfFile,
  ) : ReaderContent {
    override val sectionCount: Int get() = pdf.pageCount
  }
}

internal sealed interface ReaderState {
  data object Loading : ReaderState
  data object Failed : ReaderState
  data class Loaded(
    val content: ReaderContent,
    val position: EbookPosition,
    val readingAloud: Boolean,
    /** The part of the text read aloud right now, in the section of [position]. */
    val readingRange: IntRange?,
    val settings: ReaderSettings,
    val voice: VoiceState,
    val bookmarks: List<EbookPosition> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    /** Shown for a moment after jumping to a search result. */
    val searchMatch: BookRange? = null,
    /** The book in the new book engine, while it's on. */
    val readium: ReadiumBook? = null,
    val pace: ReadingPace = ReadingPace(),
    /** The file names of the fonts the reader added. */
    val fonts: List<String> = emptyList(),
    val fontImportFailed: Boolean = false,
    /** A themes file couldn't be written or had no themes. */
    val themesFileFailed: Boolean = false,
  ) : ReaderState
}

internal data class VoiceState(
  val natural: Boolean,
  val naturalReady: Boolean,
  /** 0 to 1 while the natural voice downloads. */
  val downloadProgress: Float?,
  val downloadFailed: Boolean,
  val speechRate: Float,
  /** The voices of the natural voice for the phone's language, empty if it doesn't speak it. */
  val speakers: List<NaturalSpeaker>,
  val speaker: NaturalSpeaker?,
)

internal enum class ReaderMessage {
  ReadAloudUnavailable,
  NothingToReadAloud,
  NaturalVoiceUnavailable,
}

@AssistedInject
class ReaderViewModel(
  private val store: EbooksStore,
  private val settingsStore: ReaderSettingsStore,
  private val naturalVoiceStore: ReaderNaturalVoiceStore,
  private val naturalSpeakerStore: ReaderNaturalSpeakerStore,
  private val speechRateStore: ReaderSpeechRateStore,
  private val paceStore: ReadingPaceStore,
  private val application: Application,
  private val navigator: Navigator,
  private val dispatcherProvider: DispatcherProvider,
  private val komgaSync: KomgaSync,
  @Assisted
  private val uri: Uri,
) {

  private val scope = MainScope(dispatcherProvider)
  private var content by mutableStateOf<ReaderContent?>(null)
  private var failed by mutableStateOf(false)
  private var position by mutableStateOf(EbookPosition())
  private var readAloudJob by mutableStateOf<Job?>(null)
  private var readAloud: ReadAloud? = null
  private var readingRange by mutableStateOf<IntRange?>(null)
  private var settings by mutableStateOf(ReaderSettings())
  private var fonts by mutableStateOf(emptyList<String>())
  private var fontImportFailed by mutableStateOf(false)
  private var themesFileFailed by mutableStateOf(false)
  private val neuralVoice by lazy { NeuralVoice(application) }
  private var naturalVoice by mutableStateOf(true)
  private var naturalReady by mutableStateOf(false)
  private var downloadProgress by mutableStateOf<Float?>(null)
  private var downloadFailed by mutableStateOf(false)
  private var speechRate by mutableFloatStateOf(1F)
  private val speakers = naturalSpeakers(Locale.getDefault())
  private var speakerId by mutableIntStateOf(-1)
  private val selectedSpeaker: NaturalSpeaker?
    get() = speakers.firstOrNull { it.id == speakerId } ?: speakers.firstOrNull()
  private var bookmarks by mutableStateOf(emptyList<EbookPosition>())
  private var highlights by mutableStateOf(emptyList<Highlight>())
  private var searchMatch by mutableStateOf<BookRange?>(null)
  private var searchMatchJob: Job? = null
  private var readium by mutableStateOf<ReadiumBook?>(null)
  private var useNaturalVoice = false
  private var pace by mutableStateOf(ReadingPace())
  private var lastProgress: Double? = null
  private var lastProgressAt = 0L

  private val messageChannel = Channel<ReaderMessage>(Channel.CONFLATED)
  internal val messages: Flow<ReaderMessage> = messageChannel.receiveAsFlow()

  init {
    scope.launch {
      settingsStore.data.collect { settings = it }
    }
    scope.launch {
      speechRateStore.data.collect { speechRate = it }
    }
    scope.launch {
      fonts = withContext(dispatcherProvider.io) { importedFonts(application) }
    }
    scope.launch {
      paceStore.data.collect { pace = it }
    }
    scope.launch {
      naturalSpeakerStore.data.collect { speakerId = it }
    }
    scope.launch {
      naturalReady = withContext(dispatcherProvider.io) { neuralVoice.isDownloaded }
      naturalVoice = naturalVoiceStore.data.first()
    }
    scope.launch {
      val stored = store.data.first().firstOrNull { it.uri == uri }
      if (stored == null) {
        failed = true
        return@launch
      }
      val newerPlace = scope.async { komgaSync.newerPlace(stored) }
      var remotePlace: RemotePlace? = null
      try {
        val loaded = withContext(dispatcherProvider.io) { load(stored) }
        // without a connection the book opens where it was here, after a moment at most
        val place = withTimeoutOrNull(REMOTE_PLACE_WAIT) { newerPlace.await() }
        val ebook = if (place != null) takePlace(stored, place, loaded) else stored
        remotePlace = place?.takeUnless { it.isSamePlaceAs(stored.progress) }
        position = ebook.position
        bookmarks = ebook.bookmarks
        highlights = ebook.highlights
        content = loaded
        store.updateData { ebooks ->
          ebooks.map { if (it.uri == uri) it.copy(lastOpened = System.currentTimeMillis()) else it }
        }
      } catch (e: Exception) {
        Logger.w(e, "Could not open a ${stored.format}")
        newerPlace.cancel()
        failed = true
        return@launch
      }
      if (stored.format == EbookFormat.Epub) {
        val _ = settingsStore.data.first { it.newBookEngine }
        val latest = store.data.first().firstOrNull { it.uri == uri } ?: stored
        readium = withContext(dispatcherProvider.io) { openReadiumBook(application, uri, latest, remotePlace) }
      }
    }
  }

  /** Moves to the place from the server, unless it's about where the book is already. */
  private suspend fun takePlace(
    ebook: Ebook,
    place: RemotePlace,
    content: ReaderContent,
  ): Ebook {
    val moved = !place.isSamePlaceAs(ebook.progress)
    val newPosition = if (moved) place.position(content) else ebook.position
    val progress = if (moved) place.totalProgression.toFloat() else ebook.progress
    fun Ebook.withPlace() = copy(
      position = newPosition,
      progress = progress,
      // the new book engine finds the place from the server or the progress
      readiumLocator = if (moved) null else readiumLocator,
      positionModified = place.modified,
      opdsSource = opdsSource?.copy(syncedPosition = place.modified),
    )
    store.updateData { ebooks -> ebooks.map { if (it.uri == uri) it.withPlace() else it } }
    return ebook.withPlace()
  }

  private fun load(ebook: Ebook): ReaderContent {
    val contentResolver = application.contentResolver
    return when (ebook.format) {
      EbookFormat.Epub -> {
        val epub = contentResolver.openInputStream(uri)!!.use { parseEpub(it) }
        ReaderContent.Epub(
          title = ebook.customName ?: epub.title ?: ebook.name,
          chapters = epub.chapters.map { it.text },
          chapterTitles = epub.chapters.map { it.title },
        )
      }
      EbookFormat.Pdf -> {
        ReaderContent.Pdf(title = ebook.title, pdf = PdfFile(openSeekablePdf(application, uri)))
      }
    }
  }

  internal fun state(): ReaderState {
    val content = content
    return when {
      failed -> ReaderState.Failed
      content == null -> ReaderState.Loading
      else -> ReaderState.Loaded(
        content = content,
        position = position,
        readingAloud = readAloudJob != null,
        readingRange = readingRange,
        settings = settings,
        voice = VoiceState(
          natural = naturalVoice,
          naturalReady = naturalReady,
          downloadProgress = downloadProgress,
          downloadFailed = downloadFailed,
          speechRate = speechRate,
          speakers = speakers,
          speaker = selectedSpeaker,
        ),
        bookmarks = bookmarks,
        highlights = highlights,
        searchMatch = searchMatch,
        // when the new engine can't open the book, the old one shows it
        readium = readium?.takeIf { settings.newBookEngine },
        pace = pace,
        fonts = fonts,
        fontImportFailed = fontImportFailed,
        themesFileFailed = themesFileFailed,
      )
    }
  }

  internal fun onReadiumBookmarkClick() {
    val book = readium ?: return
    val page = book.locator ?: return
    val onPage = book.bookmarks.filter { book.isOnPage(it, page) }
    book.bookmarks = if (onPage.isEmpty()) {
      (book.bookmarks + page).sortedBy { it.locations.totalProgression ?: 0.0 }
    } else {
      book.bookmarks - onPage.toSet()
    }
    saveReadiumBookmarks(book)
  }

  internal fun onReadiumJump() {
    stopReadingAloud()
  }

  internal fun onReadiumHighlightAdd(
    locator: Locator,
    color: HighlightColor,
  ): LocatedHighlight? {
    val book = readium ?: return null
    val highlight = LocatedHighlight(locator, color, note = null)
    book.highlights = (book.highlights + highlight).sortedBy { it.locator.locations.totalProgression ?: 0.0 }
    saveReadiumHighlights(book)
    return highlight
  }

  internal fun onReadiumHighlightChange(
    old: LocatedHighlight,
    color: HighlightColor,
    note: String?,
  ) {
    val book = readium ?: return
    book.highlights = book.highlights.map { if (it == old) it.copy(color = color, note = note) else it }
    saveReadiumHighlights(book)
  }

  internal fun onReadiumHighlightDelete(highlight: LocatedHighlight) {
    val book = readium ?: return
    book.highlights = book.highlights - highlight
    saveReadiumHighlights(book)
  }

  private fun saveReadiumHighlights(book: ReadiumBook) {
    val highlights = book.highlights.map { ReadiumHighlight(it.locator.toJsonString(), it.color, it.note) }
    scope.launch {
      store.updateData { ebooks ->
        ebooks.map { if (it.uri == uri) it.copy(readiumHighlights = highlights) else it }
      }
    }
  }

  internal suspend fun searchReadium(query: String): List<Locator> {
    val book = readium ?: return emptyList()
    return withContext(dispatcherProvider.io) {
      val iterator = book.publication.search(query) ?: return@withContext emptyList()
      val results = mutableListOf<Locator>()
      try {
        while (results.size < 500) {
          val page = iterator.next().getOrNull() ?: break
          results += page.locators
        }
      } finally {
        iterator.close()
      }
      results
    }
  }

  internal fun onReadiumSearchResultClick(locator: Locator) {
    val book = readium ?: return
    stopReadingAloud()
    searchMatchJob?.cancel()
    book.searchMatch = locator
    searchMatchJob = scope.launch {
      delay(3.seconds)
      book.searchMatch = null
    }
  }

  internal fun onReadiumBookmarkDelete(bookmark: Locator) {
    val book = readium ?: return
    book.bookmarks = book.bookmarks - bookmark
    saveReadiumBookmarks(book)
  }

  private fun saveReadiumBookmarks(book: ReadiumBook) {
    val bookmarks = book.bookmarks.map { it.toJsonString() }
    scope.launch {
      store.updateData { ebooks ->
        ebooks.map { if (it.uri == uri) it.copy(readiumBookmarks = bookmarks) else it }
      }
    }
  }

  internal fun onLocatorChange(locator: Locator) {
    val book = readium ?: return
    if (book.locator == locator) return
    // the place that shows when the pages are made isn't a move
    val moved = book.locator?.locations?.totalProgression != locator.locations.totalProgression
    book.locator = locator
    val json = locator.toJsonString()
    val progress = locator.locations.totalProgression?.toFloat()
    locator.locations.totalProgression?.let { measurePace(it) }
    val now = System.currentTimeMillis()
    scope.launch {
      store.updateData { ebooks ->
        ebooks.map { ebook ->
          if (ebook.uri == uri) {
            ebook.copy(
              readiumLocator = json,
              progress = progress ?: ebook.progress,
              positionModified = if (moved) now else ebook.positionModified,
            )
          } else {
            ebook
          }
        }
      }
    }
  }

  internal fun onPageTurned(
    page: Page,
    pageProgress: Float,
  ) {
    stopReadingAloud()
    val newPosition = EbookPosition(page.section, page.start)
    measurePace(progress(newPosition).toDouble())
    moveTo(newPosition, pageProgress)
  }

  /** The scrolled book shows [newPosition] at the top. Reading aloud moves the text itself, so it keeps its place. */
  internal fun onScrolled(newPosition: EbookPosition) {
    if (readAloudJob != null) return
    // scrolling moves a few lines at a time, the pace is measured over longer stretches
    if (SystemClock.elapsedRealtime() - lastProgressAt >= SCROLL_PACE_STEP.inWholeMilliseconds) {
      measurePace(progress(newPosition).toDouble())
    }
    moveTo(newPosition)
  }

  /** Learns how fast the reader reads from the turns forward, leaving out jumps and breaks. */
  private fun measurePace(progress: Double) {
    val content = content as? ReaderContent.Epub ?: return
    val now = SystemClock.elapsedRealtime()
    val previous = lastProgress
    val millis = now - lastProgressAt
    lastProgress = progress
    lastProgressAt = now
    if (previous == null || readAloudJob != null) return
    val characters = (progress - previous) * content.totalLength
    if (characters <= 0 || characters > MAX_CHARACTERS_PER_TURN || millis < 1_000 || millis > IDLE.inWholeMilliseconds) return
    scope.launch { paceStore.updateData { it.plus(characters, millis) } }
  }

  internal fun onChapterClick(section: Int) {
    stopReadingAloud()
    moveTo(EbookPosition(section, 0))
  }

  internal fun onBookmarkClick(visiblePages: List<Page>) {
    val page = visiblePages.firstOrNull() ?: return
    val onPage = bookmarks.filter { bookmark -> visiblePages.any { it.contains(bookmark) } }
    bookmarks = if (onPage.isEmpty()) {
      (bookmarks + EbookPosition(page.section, page.start)).sortedWith(positionOrder)
    } else {
      bookmarks - onPage.toSet()
    }
    saveMarks()
  }

  internal fun onBookmarkDelete(bookmark: EbookPosition) {
    bookmarks = bookmarks - bookmark
    saveMarks()
  }

  internal fun onMarkClick(position: EbookPosition) {
    stopReadingAloud()
    moveTo(position)
  }

  internal fun onHighlightAdd(
    range: BookRange,
    color: HighlightColor,
  ): Highlight {
    val highlight = Highlight(section = range.section, start = range.start, end = range.end, color = color)
    highlights = (highlights + highlight).sortedWith(compareBy<Highlight>({ it.section }, { it.start }))
    saveMarks()
    return highlight
  }

  internal fun onHighlightChange(
    old: Highlight,
    new: Highlight,
  ) {
    highlights = highlights.map { if (it == old) new else it }
    saveMarks()
  }

  internal fun onHighlightDelete(highlight: Highlight) {
    highlights = highlights - highlight
    saveMarks()
  }

  internal suspend fun search(query: String): List<SearchResult> {
    val content = content ?: return emptyList()
    return searchBook(content, query)
  }

  internal fun onSearchResultClick(result: SearchResult) {
    stopReadingAloud()
    moveTo(EbookPosition(result.match.section, result.match.start))
    searchMatchJob?.cancel()
    searchMatch = result.match
    searchMatchJob = scope.launch {
      delay(3.seconds)
      searchMatch = null
    }
  }

  private fun saveMarks() {
    val bookmarks = bookmarks
    val highlights = highlights
    scope.launch {
      store.updateData { ebooks ->
        ebooks.map { if (it.uri == uri) it.copy(bookmarks = bookmarks, highlights = highlights) else it }
      }
    }
  }

  internal fun onSettingsChange(settings: ReaderSettings) {
    this.settings = settings
    scope.launch { settingsStore.updateData { settings } }
  }

  internal fun onFontImport(uri: Uri) {
    scope.launch {
      val name = withContext(dispatcherProvider.io) { importFont(application, uri) }
      fontImportFailed = name == null
      if (name != null) {
        fonts = withContext(dispatcherProvider.io) { importedFonts(application) }
        onSettingsChange(settings.copy(customFont = name))
      }
    }
  }

  internal fun onThemesExport(uri: Uri) {
    val themes = settings.savedThemes
    scope.launch {
      themesFileFailed = !withContext(dispatcherProvider.io) { exportThemes(application, uri, themes) }
    }
  }

  internal fun onThemesImport(uri: Uri) {
    scope.launch {
      val imported = withContext(dispatcherProvider.io) { importThemes(application, uri) }
      themesFileFailed = imported.isEmpty()
      if (imported.isNotEmpty()) onSettingsChange(settings.copy(savedThemes = settings.savedThemes + imported))
    }
  }

  fun onCloseClick() {
    navigator.goBack()
  }

  internal fun onVoiceSelect(natural: Boolean) {
    naturalVoice = natural
    scope.launch { naturalVoiceStore.updateData { natural } }
    if (natural && !naturalReady && downloadProgress == null) {
      downloadFailed = false
      downloadProgress = 0F
      scope.launch {
        val ready = neuralVoice.download { progress -> downloadProgress = progress }
        downloadProgress = null
        naturalReady = ready
        downloadFailed = !ready
      }
    }
  }

  internal fun onSpeechRateChange(rate: Float) {
    speechRate = rate
    scope.launch { speechRateStore.updateData { rate } }
  }

  internal fun onSpeakerSelect(speaker: NaturalSpeaker) {
    speakerId = speaker.id
    scope.launch { naturalSpeakerStore.updateData { speaker.id } }
  }

  internal fun stopReadingAloudClick() {
    stopReadingAloud()
  }

  internal fun onStartReadingClick() {
    if (readAloudJob != null) return
    val content = content ?: return
    val book = readium?.takeIf { settings.newBookEngine }
    val speaker = readAloud ?: ReadAloud(application).also { readAloud = it }
    useNaturalVoice = naturalVoice && naturalReady
    readAloudJob = scope.launch {
      try {
        if (book != null) readAloudFrom(book, speaker) else readAloudFrom(content, speaker)
      } finally {
        if (readAloudJob == coroutineContext[Job]) {
          readAloudJob = null
          readingRange = null
          book?.readingAloud = null
        }
      }
    }
  }

  /** Speaks [text] with the natural voice, or the phone's when that fails, and returns false when the phone can't speak. */
  private suspend fun speak(
    text: String,
    next: String?,
    speaker: ReadAloud,
  ): Boolean {
    val naturalSpeaker = selectedSpeaker
    if (useNaturalVoice && (naturalSpeaker == null || !neuralVoice.speak(text, next, speechRate, naturalSpeaker))) {
      useNaturalVoice = false
      messageChannel.trySend(ReaderMessage.NaturalVoiceUnavailable)
    }
    if (!useNaturalVoice && !speaker.speak(text, speechRate)) {
      messageChannel.trySend(ReaderMessage.ReadAloudUnavailable)
      return false
    }
    return true
  }

  private suspend fun readAloudFrom(
    book: ReadiumBook,
    speaker: ReadAloud,
  ) {
    val iterator = book.publication.content(book.locator)?.iterator()
    var spokeAnything = false
    while (iterator != null && iterator.hasNext()) {
      val element = iterator.next() as? Content.TextElement ?: continue
      val text = element.text
      var offset = 0
      while (true) {
        val chunk = nextChunk(text, offset) ?: break
        val spoken = text.substring(chunk)
        // the text around it finds the part of the paragraph to highlight
        book.readingAloud = element.locator.copy(
          text = Locator.Text(
            before = text.substring((chunk.first - 40).coerceAtLeast(0), chunk.first).ifEmpty { null },
            highlight = spoken,
            after = text.substring(chunk.last + 1, (chunk.last + 41).coerceAtMost(text.length)).ifEmpty { null },
          ),
        )
        val next = nextChunk(text, chunk.last + 1)?.let { text.substring(it) }
        if (!speak(spoken, next, speaker)) return
        spokeAnything = true
        offset = chunk.last + 1
      }
    }
    if (!spokeAnything) {
      messageChannel.trySend(ReaderMessage.NothingToReadAloud)
    }
  }

  private suspend fun readAloudFrom(
    content: ReaderContent,
    speaker: ReadAloud,
  ) {
    var section = position.section
    var offset = position.offset
    var spokeAnything = false
    while (section < content.sectionCount) {
      val text = when (content) {
        is ReaderContent.Epub -> content.chapters[section]
        is ReaderContent.Pdf -> content.pdf.text(section)
      }
      while (true) {
        val chunk = nextChunk(text, offset) ?: break
        moveTo(EbookPosition(section, chunk.first))
        readingRange = chunk
        val next = nextChunk(text, chunk.last + 1)?.let { text.substring(it) }
        if (!speak(text.substring(chunk), next, speaker)) return
        spokeAnything = true
        offset = chunk.last + 1
      }
      section++
      offset = 0
    }
    if (!spokeAnything) {
      messageChannel.trySend(ReaderMessage.NothingToReadAloud)
    }
  }

  private fun stopReadingAloud() {
    readAloudJob?.cancel()
    readAloudJob = null
    readingRange = null
    readium?.readingAloud = null
  }

  private fun moveTo(
    newPosition: EbookPosition,
    progress: Float = progress(newPosition),
  ) {
    if (newPosition == position) return
    position = newPosition
    val now = System.currentTimeMillis()
    scope.launch {
      store.updateData { ebooks ->
        ebooks.map { ebook ->
          if (ebook.uri == uri) ebook.copy(position = newPosition, progress = progress, positionModified = now) else ebook
        }
      }
    }
  }

  private fun progress(position: EbookPosition): Float {
    val progress = when (val content = content) {
      null -> 0F
      is ReaderContent.Epub -> {
        val read = content.chapters.take(position.section).sumOf { it.length } + position.offset
        read.toFloat() / content.totalLength.coerceAtLeast(1)
      }
      is ReaderContent.Pdf -> position.section.toFloat() / (content.sectionCount - 1).coerceAtLeast(1)
    }
    return progress.coerceIn(0F, 1F)
  }

  /** Called when the reader leaves for good, not when the window changes size, e.g. when unfolding. */
  fun close() {
    stopReadingAloud()
    readAloud?.shutdown()
    if (naturalReady) neuralVoice.release()
    val content = content
    val publication = readium?.publication
    scope.launch {
      if (content is ReaderContent.Pdf) content.pdf.close()
      publication?.close()
      komgaSync.flush()
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(uri: Uri): ReaderViewModel
  }
}

// a spread of two pages of small text, more is a jump
private const val MAX_CHARACTERS_PER_TURN = 6_000

// a longer time on a page is a break
private val IDLE = 2.minutes

private val SCROLL_PACE_STEP = 10.seconds

private val REMOTE_PLACE_WAIT = 2.seconds

private val positionOrder = compareBy<EbookPosition>({ it.section }, { it.offset })
