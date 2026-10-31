package voice.features.bookOverview.overview

import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Ebook
import java.io.File
import java.text.Normalizer
import java.time.Instant

internal fun seriesKey(name: String): String? {
  return Normalizer.normalize(name, Normalizer.Form.NFKD)
    // accents like the one of ä can go, but in scripts like Devanagari the marks are part of the letters
    .filter { it.isLetterOrDigit() || (it.category in Marks && it !in Accents) }
    .lowercase()
    .takeIf { it.isNotEmpty() }
}

private val Marks = setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK)
private val Accents = '\u0300'..'\u036F'

internal val Book.seriesName: String?
  get() = series.trimmedOrNull()

internal val Book.seriesPart: String?
  get() = part.trimmedOrNull()

internal val Book.seriesLine: String?
  get() = seriesLine(seriesName, seriesPart)

internal val Ebook.seriesLine: String?
  get() = seriesLine(shownSeries.trimmedOrNull(), shownPart.trimmedOrNull())

private fun seriesLine(
  name: String?,
  part: String?,
): String? = name?.let { listOfNotNull(it, part).joinToString(" ") }

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** An audiobook or an ebook, as both can be in a series. */
internal sealed interface LibraryBook {
  val id: BookId
  val name: String
  val author: String?
  val coverUrl: String?
  val seriesName: String?
  val seriesPart: String?
  val category: BookOverviewCategory
  val lastActiveAt: Instant

  data class Audiobook(val book: Book) : LibraryBook {
    override val id: BookId get() = book.id
    override val name: String get() = book.name
    override val author: String? get() = book.author
    override val coverUrl: String? get() = book.coverUrl
    override val seriesName: String? get() = book.seriesName
    override val seriesPart: String? get() = book.seriesPart
    override val category: BookOverviewCategory get() = book.category
    override val lastActiveAt: Instant get() = book.content.lastPlayedAt
  }

  data class Ebook(val ebook: voice.core.data.Ebook) : LibraryBook {
    override val id: BookId get() = ebook.bookId
    override val name: String get() = ebook.title
    override val author: String? get() = ebook.shownAuthor
    override val coverUrl: String? get() = ebook.shownCover?.let { File(it).toURI().toString() }
    override val seriesName: String? get() = ebook.shownSeries.trimmedOrNull()
    override val seriesPart: String? get() = ebook.shownPart.trimmedOrNull()
    override val category: BookOverviewCategory get() = ebook.category
    override val lastActiveAt: Instant get() = Instant.ofEpochMilli(ebook.lastOpened)
  }
}

// Voice doesn't store file names, so books without a part follow their names, which mostly come from their files
internal val SeriesOrder: Comparator<LibraryBook> =
  compareBy<LibraryBook, String?>(nullsLast(NaturalOrderComparator.stringComparator)) { it.seriesPart }
    .thenBy(NaturalOrderComparator.stringComparator) { it.name }
    .thenBy { it.id.value }

internal class Series(
  val key: String,
  val name: String,
  val books: List<LibraryBook>,
) {

  val author: String? = books.mapNotNull { it.author }.mostCommon()

  val finishedCount: Int = books.count { it.category == BookOverviewCategory.FINISHED }

  val category: BookOverviewCategory = when {
    finishedCount == books.size -> BookOverviewCategory.FINISHED
    books.all { it.category == BookOverviewCategory.NOT_STARTED } -> BookOverviewCategory.NOT_STARTED
    else -> BookOverviewCategory.CURRENT
  }

  val inProgress: LibraryBook? = books
    .filter { it.category == BookOverviewCategory.CURRENT }
    .maxByOrNull { it.lastActiveAt }

  val firstUnfinished: LibraryBook? = books.firstOrNull { it.category != BookOverviewCategory.FINISHED }

  val front: LibraryBook = inProgress ?: firstUnfinished ?: books.first()

  val covers: List<String?> = (listOf(front) + books.filter { it != front }.take(2)).map { it.coverUrl }

  val lastPlayedAt: Instant = books.maxOf { it.lastActiveAt }

  val progress: Float = run {
    val durations = books.filterIsInstance<LibraryBook.Audiobook>().map { it.book.duration }
    // an ebook has no length, so it weighs as much as an average audiobook of the series
    val ebookWeight = durations.average().takeIf { it > 0 } ?: 1.0
    var total = 0.0
    var done = 0.0
    books.forEach { book ->
      when (book) {
        is LibraryBook.Audiobook -> {
          total += book.book.duration
          done += if (book.category == BookOverviewCategory.FINISHED) book.book.duration else book.book.position
        }
        is LibraryBook.Ebook -> {
          total += ebookWeight
          done += ebookWeight * if (book.category == BookOverviewCategory.FINISHED) 1F else book.ebook.progress
        }
      }
    }
    if (total > 0) (done / total).toFloat().coerceIn(0F, 1F) else 0F
  }
}

internal fun List<LibraryBook>.series(): List<Series> {
  return mapNotNull { book -> book.seriesName?.let(::seriesKey)?.let { key -> key to book } }
    .groupBy(keySelector = { it.first }, valueTransform = { it.second })
    .map { (key, books) ->
      Series(
        key = key,
        name = books.mapNotNull { it.seriesName }.mostCommon()!!,
        books = books.sortedWith(SeriesOrder),
      )
    }
}

internal sealed interface LibraryEntry {

  data class Single(val book: LibraryBook) : LibraryEntry

  data class Stack(val series: Series) : LibraryEntry
}

// a started series stays one stack too, so none of its books disappears from the library
internal fun libraryEntries(
  books: List<Book>,
  ebooks: List<Ebook> = emptyList(),
): Map<BookOverviewCategory, List<LibraryEntry>> {
  val all = books.map(LibraryBook::Audiobook) + ebooks.map(LibraryBook::Ebook)
  val stacks = all.series().filter { it.books.size >= 2 }
  val stacked = stacks.flatMapTo(HashSet()) { series -> series.books.map { it.id } }
  val entries = all.filter { it is LibraryBook.Audiobook && it.id !in stacked }.map { LibraryEntry.Single(it) } +
    stacks.map { LibraryEntry.Stack(it) }
  // ebooks outside a stack follow the audiobooks, as before there were stacks
  val singleEbooks = ebooks.filter { it.bookId !in stacked }.groupBy { it.category }
  return BookOverviewCategory.entries
    .associateWith { category ->
      entries.filter { it.category == category }.sortedWith(category.entryComparator()) +
        singleEbooks[category].orEmpty().sortedWith(category.ebookComparator).map { LibraryEntry.Single(LibraryBook.Ebook(it)) }
    }
    .filterValues { it.isNotEmpty() }
    .toSortedMap()
}

private val LibraryEntry.category: BookOverviewCategory
  get() = when (this) {
    is LibraryEntry.Single -> book.category
    is LibraryEntry.Stack -> series.category
  }

private val LibraryEntry.lastPlayedAt: Instant
  get() = when (this) {
    is LibraryEntry.Single -> book.lastActiveAt
    is LibraryEntry.Stack -> series.lastPlayedAt
  }

private val LibraryEntry.name: String
  get() = when (this) {
    is LibraryEntry.Single -> book.name
    is LibraryEntry.Stack -> series.name
  }

private fun BookOverviewCategory.entryComparator(): Comparator<LibraryEntry> = when (this) {
  BookOverviewCategory.CURRENT, BookOverviewCategory.FINISHED -> compareByDescending { it.lastPlayedAt }
  BookOverviewCategory.NOT_STARTED -> compareBy(NaturalOrderComparator.stringComparator) { it.name }
}

private fun List<String>.mostCommon(): String? {
  return groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}
