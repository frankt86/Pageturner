package voice.core.search

import voice.core.data.Book
import voice.core.data.Ebook as LibraryEbook

sealed interface BookSearchResult {

  // the ranges point into the field's text, which for a series includes its part
  val matches: Map<BookSearchField, List<IntRange>>
  val fieldsMatchingAllWords: Set<BookSearchField>
  val score: Int

  data class Audiobook(
    val book: Book,
    override val matches: Map<BookSearchField, List<IntRange>>,
    override val fieldsMatchingAllWords: Set<BookSearchField>,
    override val score: Int,
  ) : BookSearchResult

  data class Ebook(
    val ebook: LibraryEbook,
    override val matches: Map<BookSearchField, List<IntRange>>,
    override val fieldsMatchingAllWords: Set<BookSearchField>,
    override val score: Int,
  ) : BookSearchResult
}

enum class BookSearchField {
  Title,
  Author,
  Series,
  Narrator,
  Genre,
  ;

  fun text(book: Book): String? {
    val text = when (this) {
      Title -> book.name
      Author -> book.author
      Series -> seriesText(book.series, book.part)
      Narrator -> book.content.metadata.narrator
      Genre -> book.content.metadata.genre
    }
    return text?.takeIf { it.isNotBlank() }
  }

  fun text(ebook: LibraryEbook): String? {
    val text = when (this) {
      Title -> ebook.title
      Author -> ebook.shownAuthor
      Series -> seriesText(ebook.series, ebook.seriesPart)
      Narrator, Genre -> null
    }
    return text?.takeIf { it.isNotBlank() }
  }
}

private fun seriesText(
  series: String?,
  part: String?,
): String? {
  if (series == null) return null
  return listOfNotNull(series, part?.takeIf { it.isNotBlank() }).joinToString(" ")
}
