package voice.features.bookOverview.overview

import androidx.compose.runtime.Immutable
import java.io.File
import java.text.NumberFormat
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.seriesLine
import voice.core.logging.api.Logger
import voice.core.ui.formatTime

@Immutable
data class BookOverviewItemViewState(
  val name: String,
  val author: String?,
  val cover: String?,
  val progress: Float,
  val id: BookId,
  val remainingTime: String,
  val series: String? = null,
  val hidden: Boolean = false,
  val isEbook: Boolean = false,
)

internal fun Book.toItemViewState() =
  BookOverviewItemViewState(
    name = name,
    author = author,
    cover = coverUrl,
    id = id,
    progress = progress(),
    remainingTime = formatTime(duration - position),
    series = seriesLine,
    hidden = custom.hidden,
  )

// ebooks share the library with audiobooks, so they need an id of the same kind
internal val Ebook.bookId: BookId
  get() = BookId(uri.toString())

internal fun Ebook.toItemViewState() =
  BookOverviewItemViewState(
    name = title,
    author = shownAuthor,
    cover = shownCover?.let { File(it).toURI().toString() },
    id = bookId,
    progress = progress,
    remainingTime =
      "${format.name.uppercase()} · ${NumberFormat.getPercentInstance().format(progress)}",
    series = seriesLine,
    hidden = hidden,
    isEbook = true,
  )

internal fun LibraryBook.toItemViewState() =
  when (this) {
    is LibraryBook.Audiobook -> book.toItemViewState()
    is LibraryBook.Ebook -> ebook.toItemViewState()
  }

internal fun Book.progress(): Float {
  val globalPosition = position
  val totalDuration = duration
  if (totalDuration == 0L) return 0F
  val progress = globalPosition.toFloat() / totalDuration.toFloat()
  if (progress < 0F) {
    Logger.w("Negative progress: position=$globalPosition, duration=$totalDuration")
  }
  return progress.coerceIn(0F, 1F)
}
