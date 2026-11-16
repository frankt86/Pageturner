package voice.features.bookOverview.editBook

import voice.core.data.BookId

internal data class EditBookState(
  val title: String,
  val author: String,
  val bookId: BookId,
  val cover: String?,
  val series: String = "",
  val part: String = "",
  val allSeries: List<NameSuggestion> = emptyList(),
  val initialTitle: String = title,
  val initialAuthor: String = author,
  val initialSeries: String = series,
  val initialPart: String = part,
) {

  val confirmButtonEnabled: Boolean = title.trim().isNotEmpty()

  val suggestions: List<NameSuggestion> =
    if (series == initialSeries) emptyList() else allSeries.matching(series)
}
