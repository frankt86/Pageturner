package voice.features.bookOverview.ebookActions

import voice.core.data.BookId

internal data class RenameEbookState(
  val title: String,
  val bookId: BookId,
  val cover: String?,
) {

  val confirmButtonEnabled: Boolean = title.trim().isNotEmpty()
}
