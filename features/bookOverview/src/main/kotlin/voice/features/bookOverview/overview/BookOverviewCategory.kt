package voice.features.bookOverview.overview

import androidx.annotation.StringRes
import voice.core.data.Book
import voice.core.data.Ebook
import java.util.concurrent.TimeUnit.SECONDS
import voice.core.strings.R as StringsR

enum class BookOverviewCategory(@StringRes val nameRes: Int) {
  CURRENT(StringsR.string.library_category_current_title),
  NOT_STARTED(StringsR.string.library_category_not_started_title),
  FINISHED(StringsR.string.library_category_completed_title),
}

val Book.category: BookOverviewCategory
  get() {
    return if (position == 0L) {
      BookOverviewCategory.NOT_STARTED
    } else {
      if (position >= duration - SECONDS.toMillis(5)) {
        BookOverviewCategory.FINISHED
      } else {
        BookOverviewCategory.CURRENT
      }
    }
  }

val BookOverviewCategory.ebookComparator: Comparator<Ebook>
  get() = when (this) {
    BookOverviewCategory.NOT_STARTED -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    BookOverviewCategory.CURRENT, BookOverviewCategory.FINISHED -> compareByDescending { it.lastOpened }
  }

val Ebook.category: BookOverviewCategory
  get() = when {
    progress >= 0.99F -> BookOverviewCategory.FINISHED
    lastOpened == 0L -> BookOverviewCategory.NOT_STARTED
    else -> BookOverviewCategory.CURRENT
  }
