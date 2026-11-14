package voice.features.bookOverview.overview

import java.io.File
import java.text.Normalizer
import java.time.Instant
import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.Book
import voice.core.data.BookCategory
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.category

internal fun nameKey(name: String): String? {
  return Normalizer.normalize(name, Normalizer.Form.NFKD)
    // accents like the one of ä can go, but in scripts like Devanagari the marks are part of the
    // letters
    .filter { it.isLetterOrDigit() || (it.category in Marks && it !in Accents) }
    .lowercase()
    .takeIf { it.isNotEmpty() }
}

private val Marks =
  setOf(
    CharCategory.NON_SPACING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
    CharCategory.ENCLOSING_MARK,
  )
private val Accents = '̀'..'ͯ'

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
  val category: BookCategory
  val lastActiveAt: Instant
  val hidden: Boolean
  val progress: Float

  data class Audiobook(val book: Book) : LibraryBook {
    override val id: BookId
      get() = book.id

    override val name: String
      get() = book.name

    override val author: String?
      get() = book.author

    override val coverUrl: String?
      get() = book.coverUrl

    override val seriesName: String?
      get() = book.seriesName

    override val seriesPart: String?
      get() = book.seriesPart

    override val category: BookCategory
      get() = book.category

    override val lastActiveAt: Instant
      get() = book.content.lastPlayedAt

    override val hidden: Boolean
      get() = book.custom.hidden

    override val progress: Float
      get() = book.progress()
  }

  data class Ebook(val ebook: voice.core.data.Ebook) : LibraryBook {
    override val id: BookId
      get() = ebook.bookId

    override val name: String
      get() = ebook.title

    override val author: String?
      get() = ebook.shownAuthor

    override val coverUrl: String?
      get() = ebook.shownCover?.let { File(it).toURI().toString() }

    override val seriesName: String?
      get() = ebook.shownSeries.trimmedOrNull()

    override val seriesPart: String?
      get() = ebook.shownPart.trimmedOrNull()

    override val category: BookCategory
      get() = ebook.category

    override val lastActiveAt: Instant
      get() = Instant.ofEpochMilli(ebook.lastOpened)

    override val hidden: Boolean
      get() = ebook.hidden

    override val progress: Float
      get() = ebook.progress.coerceIn(0F, 1F)
  }
}

// Pageturner doesn't store file names, so books without a part follow their names, which mostly
// come from their files
internal val SeriesOrder: Comparator<LibraryBook> =
  compareBy<LibraryBook, String?>(nullsLast(NaturalOrderComparator.stringComparator)) {
      it.seriesPart
    }
    .thenBy(NaturalOrderComparator.stringComparator) { it.name }
    .thenBy { it.id.value }

internal class Series(
  val key: String,
  val name: String,
  val books: List<LibraryBook>,
) {

  val author: String? = books.mapNotNull { it.author }.mostCommon()

  val finishedCount: Int = books.count { it.category == BookCategory.FINISHED }

  val category: BookCategory =
    when {
      finishedCount == books.size -> BookCategory.FINISHED
      books.all { it.category == BookCategory.NOT_STARTED } -> BookCategory.NOT_STARTED
      else -> BookCategory.CURRENT
    }

  // the series screen lists hidden books too, but doesn't offer to continue one
  val inProgress: LibraryBook? =
    books
      .filter { it.category == BookCategory.CURRENT && !it.hidden }
      .maxByOrNull { it.lastActiveAt }

  val firstUnfinished: LibraryBook? = books.firstOrNull {
    it.category != BookCategory.FINISHED && !it.hidden
  }

  val front: LibraryBook = inProgress ?: firstUnfinished ?: books.first()

  val covers: List<String?> =
    (listOf(front) + books.filter { it != front }.take(2)).map { it.coverUrl }

  val lastPlayedAt: Instant = books.maxOf { it.lastActiveAt }

  val progress: List<Float> = books.map {
    if (it.category == BookCategory.FINISHED) 1F else it.progress
  }

  val hidden: Boolean = books.all { it.hidden }
}

internal fun List<Book>.inSeries(name: String): List<Book> {
  val key = nameKey(name) ?: return emptyList()
  return filter { book -> book.seriesName?.let(::nameKey) == key }
}

@JvmName("libraryBooksInSeries")
internal fun List<LibraryBook>.inSeries(name: String): List<LibraryBook> {
  val key = nameKey(name) ?: return emptyList()
  return filter { book -> book.seriesName?.let(::nameKey) == key }
}

internal fun List<Book>.series(): List<Series> = map(LibraryBook::Audiobook).series()

@JvmName("libraryBookSeries")
internal fun List<LibraryBook>.series(): List<Series> {
  return mapNotNull { book -> book.seriesName?.let(::nameKey)?.let { key -> key to book } }
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

@JvmName("libraryBookEntries")
internal fun libraryEntries(books: List<LibraryBook>): Map<BookCategory, List<LibraryEntry>> {
  return libraryEntries(
    books.filterIsInstance<LibraryBook.Audiobook>().map { it.book },
    books.filterIsInstance<LibraryBook.Ebook>().map { it.ebook },
  )
}

// a started series stays one stack too, so none of its books disappears from the library
internal fun libraryEntries(
  books: List<Book>,
  ebooks: List<Ebook> = emptyList(),
): Map<BookCategory, List<LibraryEntry>> {
  val all = books.map(LibraryBook::Audiobook) + ebooks.map(LibraryBook::Ebook)
  val stacks = all.series().filter { it.books.size >= 2 }
  val stacked = stacks.flatMapTo(HashSet()) { series -> series.books.map { it.id } }
  val entries =
    all
      .filter { it is LibraryBook.Audiobook && it.id !in stacked }
      .map { LibraryEntry.Single(it) } + stacks.map { LibraryEntry.Stack(it) }
  // ebooks outside a stack follow the audiobooks, as before there were stacks
  val singleEbooks = ebooks.filter { it.bookId !in stacked }.groupBy { it.category }
  return BookCategory.entries
    .associateWith { category ->
      entries.filter { it.category == category }.sortedWith(category.entryComparator()) +
        singleEbooks[category].orEmpty().sortedWith(category.ebookComparator).map {
          LibraryEntry.Single(LibraryBook.Ebook(it))
        }
    }
    .filterValues { it.isNotEmpty() }
    .toSortedMap()
}

private val LibraryEntry.category: BookCategory
  get() =
    when (this) {
      is LibraryEntry.Single -> book.category
      is LibraryEntry.Stack -> series.category
    }

private val LibraryEntry.lastPlayedAt: Instant
  get() =
    when (this) {
      is LibraryEntry.Single -> book.lastActiveAt
      is LibraryEntry.Stack -> series.lastPlayedAt
    }

private val LibraryEntry.name: String
  get() =
    when (this) {
      is LibraryEntry.Single -> book.name
      is LibraryEntry.Stack -> series.name
    }

private fun BookCategory.entryComparator(): Comparator<LibraryEntry> =
  when (this) {
    BookCategory.CURRENT,
    BookCategory.FINISHED -> compareByDescending { it.lastPlayedAt }
    BookCategory.NOT_STARTED -> compareBy(NaturalOrderComparator.stringComparator) { it.name }
  }

internal fun <T> List<T>.mostCommon(): T? {
  return groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}
