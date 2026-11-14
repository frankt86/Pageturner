package voice.features.bookOverview.overview

import voice.core.data.BookCategory
import voice.core.data.Ebook

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
