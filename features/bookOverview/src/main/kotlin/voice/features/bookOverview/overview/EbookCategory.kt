package voice.features.bookOverview.overview

import voice.core.data.BookCategory
import voice.core.data.Ebook
import voice.core.data.EbookPosition

internal val BookCategory.ebookComparator: Comparator<Ebook>
  get() =
    when (this) {
      BookCategory.NOT_STARTED -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
      BookCategory.CURRENT,
      BookCategory.FINISHED -> compareByDescending { it.lastOpened }
    }

internal val Ebook.category: BookCategory
  get() =
    when {
      progress >= 0.99F -> BookCategory.FINISHED
      lastOpened == 0L -> BookCategory.NOT_STARTED
      else -> BookCategory.CURRENT
    }

internal fun Ebook.withCategory(
  category: BookCategory,
  now: Long,
): Ebook =
  when (category) {
    BookCategory.NOT_STARTED ->
      copy(position = EbookPosition(), progress = 0F, lastOpened = 0, positionModified = now)
    // a finished book starts over
    BookCategory.CURRENT ->
      if (this.category == BookCategory.FINISHED) {
        copy(position = EbookPosition(), progress = 0F, lastOpened = now, positionModified = now)
      } else {
        copy(lastOpened = now)
      }
    BookCategory.FINISHED -> copy(progress = 1F, lastOpened = now, positionModified = now)
  }
