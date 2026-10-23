package voice.features.playbackScreen.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import voice.core.data.BookId
import voice.core.data.KioskModeDemoData
import voice.core.ui.CoverTheme
import voice.core.ui.VoiceTheme
import voice.features.playbackScreen.BookPlayViewState
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// rendered by CI into the screenshots of the README and the docs
@Composable
@Preview(widthDp = 411, heightDp = 891)
private fun PlayerScreenshot() {
  val book = KioskModeDemoData.echoesOfTomorrow
  val viewState = BookPlayViewState(
    chapterName = "The Signal",
    showPreviousNextButtons = true,
    title = book.title,
    author = book.author,
    sleepTimerState = BookPlayViewState.SleepTimerViewState.Disabled,
    playedTime = 17.minutes,
    duration = 42.minutes,
    playing = true,
    cover = null,
    skipSilence = true,
    playbackSpeed = 1.25F,
    volumeBoostActive = false,
    chapterNumber = 12,
    chapterCount = 18,
    bookPlayedTime = 10.hours + 25.minutes,
    bookDuration = 14.hours + 28.minutes,
    chapterSegments = List(18) { 1F / 18 },
    skipSeconds = 20,
    jumpBack = null,
  )
  VoiceTheme {
    CoverTheme(cover = viewState.cover) {
      BookPlayView(
        viewState = viewState,
        bookId = BookId("screenshot"),
        onPlayClick = {},
        onRewindClick = {},
        onFastForwardClick = {},
        onSeek = {},
        onSleepTimerClick = {},
        onBookmarkClick = {},
        onBookmarkLongClick = {},
        onSpeedChangeClick = {},
        onSkipSilenceClick = {},
        onVolumeBoostClick = {},
        onSkipToNext = {},
        onSkipToPrevious = {},
        onCloseClick = {},
        onBookActionsClick = {},
        onCurrentChapterClick = {},
        onJumpBack = {},
        onJumpBackExpire = {},
      )
    }
  }
}
