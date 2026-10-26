package voice.features.bookOverview.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import voice.core.data.BookId
import voice.core.kioskmode.KioskModeDemoData
import voice.core.ui.VoiceTheme
import voice.features.bookOverview.overview.BookOverviewCategory
import voice.features.bookOverview.overview.BookOverviewItemViewState
import voice.features.bookOverview.overview.BookOverviewLayoutMode
import voice.features.bookOverview.overview.BookOverviewViewState

// rendered by CI into the screenshots of the README and the docs
@Composable
@Preview(widthDp = 411, heightDp = 891)
private fun LibraryScreenshot() {
  VoiceTheme {
    BookOverview(
      viewState = screenshotState(),
      onSettingsClick = {},
      onBookClick = {},
      onBookLongClick = {},
      onBookFolderClick = {},
      onFolderPickerMovedDialogDismiss = {},
      onPlayButtonClick = {},
      onSearchClick = {},
      onPermissionBugCardClick = {},
      onShowFoldersClick = {},
    )
  }
}

private fun screenshotState(): BookOverviewViewState {
  fun item(
    name: String,
    author: String,
    progress: Float,
    remaining: String,
    ebook: Boolean = false,
  ) = BookOverviewItemViewState(
    name = name,
    author = author,
    cover = null,
    progress = progress,
    id = BookId(name),
    remainingTime = remaining,
    isEbook = ebook,
  )
  val echoes = KioskModeDemoData.echoesOfTomorrow
  val current = listOf(
    item(echoes.title, echoes.author, echoes.progress / 100F, echoes.remaining),
    item("The Quiet Orbit", "Mara Quill", 0.4F, "EPUB · 40%", ebook = true),
    item(KioskModeDemoData.bookshopOnMapleStreet.title, KioskModeDemoData.bookshopOnMapleStreet.author, 0.48F, "4:19:55"),
  )
  val notStarted = listOf(
    item(KioskModeDemoData.beyondTheHorizon.title, KioskModeDemoData.beyondTheHorizon.author, 0F, "11:05:22"),
    item("Pride and Prejudice", "Jane Austen", 0F, "EPUB · 0%", ebook = true),
    item("Field Guide to the Stars", "Iris Calder", 0F, "PDF · 0%", ebook = true),
  )
  return BookOverviewViewState(
    books = mapOf(
      BookOverviewCategory.CURRENT to current.associate { it.id to mutableStateOf(it) },
      BookOverviewCategory.NOT_STARTED to notStarted.associate { it.id to mutableStateOf(it) },
    ),
    currentBookId = current.first().id,
    layoutMode = BookOverviewLayoutMode.List,
    playButtonState = BookOverviewViewState.PlayButtonState.Paused,
    showEmptyLibrary = false,
    showSearchIcon = true,
    isLoading = false,
    showStoragePermissionBugCard = false,
    showFolderAccessBadge = false,
    showFolderPickerIcon = false,
    dialog = null,
  )
}
