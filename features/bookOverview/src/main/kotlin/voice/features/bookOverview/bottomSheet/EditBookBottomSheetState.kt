package voice.features.bookOverview.bottomSheet

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import voice.core.data.BookCategory
import voice.core.strings.R as StringsR
import voice.core.ui.R as UiR
import voice.features.bookOverview.overview.BookOverviewItemViewState

internal data class EditBookBottomSheetState(
  val book: BookOverviewItemViewState,
  val category: BookCategory,
  val items: List<BottomSheetItem>,
)

enum class BottomSheetItem(
  @StringRes val titleRes: Int,
  @DrawableRes val icon: Int,
) {
  Edit(StringsR.string.common_action_edit, UiR.drawable.ic_edit),
  InternetCover(StringsR.string.book_edit_cover_internet, UiR.drawable.ic_image_search),
  FileCover(StringsR.string.book_edit_cover_file, UiR.drawable.ic_image),
  Hide(StringsR.string.book_hide, UiR.drawable.ic_visibility_off),
  Show(StringsR.string.book_show, UiR.drawable.ic_visibility),
  Select(StringsR.string.book_select, UiR.drawable.ic_check_circle),
  DeleteBook(StringsR.string.book_delete_bottom_sheet_title, UiR.drawable.ic_delete),
  BookCategoryMarkAsNotStarted(
    StringsR.string.book_category_action_mark_not_started,
    UiR.drawable.ic_hourglass_empty,
  ),
  BookCategoryMarkAsCurrent(
    StringsR.string.book_category_action_mark_current,
    UiR.drawable.ic_not_started,
  ),
  BookCategoryMarkAsCompleted(
    StringsR.string.book_category_action_mark_completed,
    UiR.drawable.ic_done,
  ),
}

internal fun BookCategory.markAsItem(): BottomSheetItem =
  when (this) {
    BookCategory.NOT_STARTED -> BottomSheetItem.BookCategoryMarkAsNotStarted
    BookCategory.CURRENT -> BottomSheetItem.BookCategoryMarkAsCurrent
    BookCategory.FINISHED -> BottomSheetItem.BookCategoryMarkAsCompleted
  }

internal val BottomSheetItem.category: BookCategory?
  get() =
    when (this) {
      BottomSheetItem.BookCategoryMarkAsNotStarted -> BookCategory.NOT_STARTED
      BottomSheetItem.BookCategoryMarkAsCurrent -> BookCategory.CURRENT
      BottomSheetItem.BookCategoryMarkAsCompleted -> BookCategory.FINISHED
      else -> null
    }
