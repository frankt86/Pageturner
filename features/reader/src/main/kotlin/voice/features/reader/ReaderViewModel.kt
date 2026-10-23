package voice.features.reader

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.EbookPosition
import voice.core.data.ReaderSettings
import voice.core.data.store.EbooksStore
import voice.core.data.store.ReaderNaturalVoiceStore
import voice.core.data.store.ReaderSettingsStore
import voice.core.data.store.ReaderSpeechRateStore
import voice.core.ebook.parseEpub
import voice.core.logging.api.Logger
import voice.navigation.Navigator

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
  ) : ReaderState
}

internal data class VoiceState(
  val natural: Boolean,
  val naturalReady: Boolean,
  /** 0 to 1 while the natural voice downloads. */
  val downloadProgress: Float?,
  val downloadFailed: Boolean,
  val speechRate: Float,
)

internal enum class ReaderMessage {
  ReadAloudUnavailable,
  NothingToReadAloud,
  NaturalVoiceUnavailable,
}

@AssistedInject
class ReaderViewModel(
  @EbooksStore
  private val store: DataStore<List<Ebook>>,
  @ReaderSettingsStore
  private val settingsStore: DataStore<ReaderSettings>,
  @ReaderNaturalVoiceStore
  private val naturalVoiceStore: DataStore<Boolean>,
  @ReaderSpeechRateStore
  private val speechRateStore: DataStore<Float>,
  private val application: Application,
  private val navigator: Navigator,
  private val dispatcherProvider: DispatcherProvider,
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
  private val neuralVoice by lazy { NeuralVoice(application) }
  private var naturalVoice by mutableStateOf(true)
  private var naturalReady by mutableStateOf(false)
  private var downloadProgress by mutableStateOf<Float?>(null)
  private var downloadFailed by mutableStateOf(false)
  private var speechRate by mutableFloatStateOf(1F)

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
      naturalReady = withContext(dispatcherProvider.io) { neuralVoice.isDownloaded }
      naturalVoice = naturalVoiceStore.data.first()
    }
    scope.launch {
      val ebook = store.data.first().firstOrNull { it.uri == uri }
      if (ebook == null) {
        failed = true
        return@launch
      }
      try {
        val loaded = withContext(dispatcherProvider.io) { load(ebook) }
        position = ebook.position
        content = loaded
        store.updateData { ebooks ->
          ebooks.map { if (it.uri == uri) it.copy(lastOpened = System.currentTimeMillis()) else it }
        }
      } catch (e: Exception) {
        Logger.w(e, "Could not open a ${ebook.format}")
        failed = true
      }
    }
  }

  private fun load(ebook: Ebook): ReaderContent {
    val contentResolver = application.contentResolver
    return when (ebook.format) {
      EbookFormat.Epub -> {
        val epub = contentResolver.openInputStream(uri)!!.use { parseEpub(it) }
        ReaderContent.Epub(
          title = epub.title ?: ebook.name,
          chapters = epub.chapters.map { it.text },
          chapterTitles = epub.chapters.map { it.title },
        )
      }
      EbookFormat.Pdf -> {
        val fileDescriptor = contentResolver.openFileDescriptor(uri, "r")!!
        ReaderContent.Pdf(title = ebook.name, pdf = PdfFile(fileDescriptor))
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
        ),
      )
    }
  }

  internal fun onPageTurned(
    page: Page,
    pageProgress: Float,
  ) {
    stopReadingAloud()
    moveTo(EbookPosition(page.section, page.start), pageProgress)
  }

  internal fun onChapterClick(section: Int) {
    stopReadingAloud()
    moveTo(EbookPosition(section, 0))
  }

  internal fun onSettingsChange(settings: ReaderSettings) {
    this.settings = settings
    scope.launch { settingsStore.updateData { settings } }
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

  internal fun stopReadingAloudClick() {
    stopReadingAloud()
  }

  internal fun onStartReadingClick() {
    if (readAloudJob != null) return
    val content = content ?: return
    val speaker = readAloud ?: ReadAloud(application).also { readAloud = it }
    readAloudJob = scope.launch {
      try {
        readAloudFrom(content, speaker)
      } finally {
        if (readAloudJob == coroutineContext[Job]) {
          readAloudJob = null
          readingRange = null
        }
      }
    }
  }

  private suspend fun readAloudFrom(
    content: ReaderContent,
    speaker: ReadAloud,
  ) {
    var section = position.section
    var offset = position.offset
    var spokeAnything = false
    var useNaturalVoice = naturalVoice && naturalReady
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
        if (useNaturalVoice && !neuralVoice.speak(text.substring(chunk), next, speechRate)) {
          useNaturalVoice = false
          messageChannel.trySend(ReaderMessage.NaturalVoiceUnavailable)
        }
        if (!useNaturalVoice && !speaker.speak(text.substring(chunk), speechRate)) {
          messageChannel.trySend(ReaderMessage.ReadAloudUnavailable)
          return
        }
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
  }

  private fun moveTo(
    newPosition: EbookPosition,
    progress: Float = progress(newPosition),
  ) {
    if (newPosition == position) return
    position = newPosition
    scope.launch {
      store.updateData { ebooks ->
        ebooks.map { ebook ->
          if (ebook.uri == uri) ebook.copy(position = newPosition, progress = progress) else ebook
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
    scope.launch {
      if (content is ReaderContent.Pdf) content.pdf.close()
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(uri: Uri): ReaderViewModel
  }
}
