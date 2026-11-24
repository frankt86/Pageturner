package voice.core.search

import voice.core.data.Book
import voice.core.data.Ebook as LibraryEbook

sealed interface BookSearchResult {

  // the ranges point into the field's text, which for a series includes its part
  val matches: Map<BookSearchField, List<IntRange>>
  // how well the author and the series match, for each whose name has all words of the search
  val nameScores: Map<BookSearchField, Int>
  val score: Int
  // the chapter that explains why the search found the book, null when the other fields do
  val chapter: String?

  data class Audiobook(
    val book: Book,
    override val matches: Map<BookSearchField, List<IntRange>>,
    override val nameScores: Map<BookSearchField, Int>,
    override val score: Int,
    override val chapter: String? = null,
  ) : BookSearchResult

  data class Ebook(
    val ebook: LibraryEbook,
    override val matches: Map<BookSearchField, List<IntRange>>,
    override val nameScores: Map<BookSearchField, Int>,
    override val score: Int,
    override val chapter: String? = null,
  ) : BookSearchResult
}

enum class BookSearchField {
  Title,
  Author,
  Series,
  Narrator,
  Genre,
  Chapters;

  // a book has many chapters, which BookSearch gets from its chapter list
  fun text(book: Book): String? {
    val text =
      when (this) {
        Title -> book.name
        Author -> book.author
        Series -> seriesText(book.series, book.part)
        Narrator -> book.content.metadata.narrator
        Genre -> book.content.metadata.genre
        Chapters -> null
      }
    return text?.takeIf { it.isNotBlank() }
  }

  fun text(ebook: LibraryEbook): String? {
    val text =
      when (this) {
        Title -> ebook.title
        Author -> ebook.shownAuthor
        Series -> seriesText(ebook.shownSeries, ebook.shownPart)
        Narrator,
        Genre,
        Chapters -> null
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
