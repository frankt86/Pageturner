package voice.features.bookOverview.editBook

import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore
import voice.features.bookOverview.overview.bookId

// audiobooks and ebooks share their series, so both are suggested
internal suspend fun seriesOfOtherBooks(
  repo: BookRepository,
  ebooksStore: EbooksStore,
  bookId: BookId,
): List<NameSuggestion> {
  val audiobookSeries = repo.all().filter { it.id != bookId }.map { it.series }
  val ebookSeries =
    ebooksStore.data.first().filter { it.bookId != bookId && !it.hidden }.map { it.shownSeries }
  return (audiobookSeries + ebookSeries).toNameSuggestions()
}
