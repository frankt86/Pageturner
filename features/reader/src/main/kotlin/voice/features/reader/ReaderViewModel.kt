package voice.features.reader

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
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
import voice.core.data.store.EbooksStore
import voice.core.logging.api.Logger
import voice.navigation.Navigator

internal sealed interface ReaderContent {
  val title: String
  val sectionCount: Int

  class Epub(
    override val title: String,
    val chapters: List<String>,
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
  ) : ReaderState
}

internal enum class ReaderMessage {
  ReadAloudUnavailable,
  NothingToReadAloud,
}

@AssistedInject
class ReaderViewModel(
  @EbooksStore
  private val store: DataStore<List<Ebook>>,
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

  private val messageChannel = Channel<ReaderMessage>(Channel.CONFLATED)
  internal val messages: Flow<ReaderMessage> = messageChannel.receiveAsFlow()

  init {
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
        val epub = contentResolver.openInputStream(uri)!!.use(::parseEpub)
        ReaderContent.Epub(title = epub.title ?: ebook.name, chapters = epub.chapters)
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
      )
    }
  }

  internal fun onPageTurned(page: Page) {
    stopReadingAloud()
    moveTo(EbookPosition(page.section, page.start))
  }

  fun onCloseClick() {
    navigator.goBack()
  }

  fun onReadAloudClick() {
    if (readAloudJob != null) {
      stopReadingAloud()
      return
    }
    val content = content ?: return
    val speaker = readAloud ?: ReadAloud(application).also { readAloud = it }
    readAloudJob = scope.launch {
      try {
        readAloudFrom(content, speaker)
      } finally {
        if (readAloudJob == coroutineContext[Job]) readAloudJob = null
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
    while (section < content.sectionCount) {
      val text = when (content) {
        is ReaderContent.Epub -> content.chapters[section]
        is ReaderContent.Pdf -> content.pdf.text(section)
      }
      while (true) {
        val chunk = nextChunk(text, offset) ?: break
        moveTo(EbookPosition(section, chunk.first))
        if (!speaker.speak(text.substring(chunk))) {
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
  }

  private fun moveTo(newPosition: EbookPosition) {
    if (newPosition == position) return
    position = newPosition
    val progress = progress(newPosition)
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
