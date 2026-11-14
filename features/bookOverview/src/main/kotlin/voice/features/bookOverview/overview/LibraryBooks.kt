package voice.features.bookOverview.overview

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore

// hidden books too, like the audiobooks of the repository
internal fun libraryBooks(
  repo: BookRepository,
  ebooksStore: EbooksStore,
): Flow<List<LibraryBook>> {
  return combine(repo.flow(), ebooksStore.data) { books, ebooks ->
    books.map(LibraryBook::Audiobook) + ebooks.filter { it.available }.map(LibraryBook::Ebook)
  }
}

internal suspend fun allLibraryBooks(
  repo: BookRepository,
  ebooksStore: EbooksStore,
): List<LibraryBook> {
  return repo.all().map(LibraryBook::Audiobook) +
    ebooksStore.data.first().filter { it.available }.map(LibraryBook::Ebook)
}

internal suspend fun EbooksStore.update(
  ids: Collection<BookId>,
  transform: (Ebook) -> Ebook,
) {
  if (ids.isEmpty()) return
  val idSet = ids.toSet()
  updateData { ebooks -> ebooks.map { if (it.bookId in idSet) transform(it) else it } }
}
