package voice.features.bookOverview.overview

import androidx.compose.runtime.Immutable
import voice.core.audiobookshelf.download.BookDownloadState
import voice.core.audiobookshelf.download.WaitingFor
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.logging.api.Logger
import voice.core.ui.formatTime
import java.io.File
import java.text.NumberFormat

@Immutable
data class BookOverviewItemViewState(
  val name: String,
  val author: String?,
  val cover: String?,
  val progress: Float,
  val id: BookId,
  val remainingTime: String,
  /** A server book that isn't downloaded while the server can't be reached. */
  val unavailable: Boolean = false,
  val download: DownloadBadge? = null,
  val isEbook: Boolean = false,
)

@Immutable
sealed interface DownloadBadge {
  data class Running(
    val progress: Float,
    val waitingFor: WaitingFor?,
  ) : DownloadBadge

  data object Failed : DownloadBadge
}

internal fun BookDownloadState?.badge(): DownloadBadge? = when (this) {
  is BookDownloadState.Downloading -> DownloadBadge.Running(progress, waitingFor)
  BookDownloadState.Failed -> DownloadBadge.Failed
  is BookDownloadState.Downloaded, BookDownloadState.NotDownloaded, null -> null
}

internal fun Book.toItemViewState(
  unavailable: Boolean = false,
  download: DownloadBadge? = null,
) = BookOverviewItemViewState(
  name = name,
  author = author,
  cover = coverUrl,
  id = id,
  progress = progress(),
  remainingTime = formatTime(duration - position),
  unavailable = unavailable,
  download = download,
)

// ebooks share the library with audiobooks, so they need an id of the same kind
internal val Ebook.bookId: BookId get() = BookId(uri.toString())

internal fun Ebook.toItemViewState() = BookOverviewItemViewState(
  name = title,
  author = shownAuthor,
  cover = shownCover?.let { File(it).toURI().toString() },
  id = bookId,
  progress = progress,
  remainingTime = "${format.name.uppercase()} · ${NumberFormat.getPercentInstance().format(progress)}",
  isEbook = true,
)

private fun Book.progress(): Float {
  val globalPosition = position
  val totalDuration = duration
  val progress = globalPosition.toFloat() / totalDuration.toFloat()
  if (progress < 0F) {
    Logger.w("Negative progress: position=$globalPosition, duration=$totalDuration")
  }
  return progress.coerceIn(0F, 1F)
}
