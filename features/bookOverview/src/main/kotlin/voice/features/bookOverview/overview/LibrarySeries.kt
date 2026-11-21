package voice.features.bookOverview.overview

import java.io.File
import kotlin.time.Instant
import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.Book
import voice.core.data.BookCategory
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.category
import voice.core.data.nameKey
import voice.core.data.seriesName
import voice.core.data.seriesPart

internal val Ebook.seriesLine: String?
  get() =
    shownSeries.trimmedOrNull()?.let { name ->
      listOfNotNull(name, shownPart.trimmedOrNull()).joinToString(" ")
    }

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
      get() = Instant.fromEpochMilliseconds(ebook.lastOpened)

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
  // with the books the library filters hide, so a stack counts like its series screen
  allBooks: List<LibraryBook> = books,
) {

  val author: String? = books.mapNotNull { it.author }.mostCommon()

  val bookCount: Int = allBooks.size

  val finishedCount: Int = allBooks.count { it.category == BookCategory.FINISHED }

  val category: BookCategory =
    when {
      books.all { it.category == BookCategory.FINISHED } -> BookCategory.FINISHED
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

  val progress: List<Float> = allBooks.map {
    if (it.category == BookCategory.FINISHED) 1F else it.progress
  }

  val hidden: Boolean = books.all { it.hidden }
}

internal fun List<Book>.inSeries(name: String): List<Book> {
  val key = nameKey(name) ?: return emptyList()
  return filter { book -> book.seriesName?.let(::nameKey) == key }
}

/** The next book of [book]'s series that isn't completed, audiobook or ebook. */
internal fun List<LibraryBook>.nextInSeries(book: LibraryBook): LibraryBook? {
  val key = book.seriesName?.let(::nameKey) ?: return null
  return filter {
      it.seriesName?.let(::nameKey) == key &&
        SeriesOrder.compare(it, book) > 0 &&
        it.category != BookCategory.FINISHED &&
        !it.hidden
    }
    .minWithOrNull(SeriesOrder)
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
  allBooks: List<Book> = books,
  allEbooks: List<Ebook> = ebooks,
): Map<BookCategory, List<LibraryEntry>> {
  val all = books.map(LibraryBook::Audiobook) + ebooks.map(LibraryBook::Ebook)
  val allSeries =
    (allBooks.map(LibraryBook::Audiobook) + allEbooks.map(LibraryBook::Ebook))
      .series()
      .associateBy { it.key }
  val stacks =
    all
      .series()
      .filter { it.books.size >= 2 }
      .map { Series(it.key, it.name, it.books, allSeries[it.key]?.books ?: it.books) }
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
