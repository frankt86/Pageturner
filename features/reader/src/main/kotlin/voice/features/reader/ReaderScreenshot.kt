package voice.features.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import voice.core.data.EbookPosition
import voice.core.data.Highlight
import voice.core.data.HighlightColor
import voice.core.data.ReaderFont
import voice.core.data.ReaderSettings
import voice.core.data.ReaderTheme
import voice.core.ui.VoiceTheme

private val chapterOne = """
  It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.

  However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families, that he is considered the rightful property of some one or other of their daughters.

  “My dear Mr. Bennet,” said his lady to him one day, “have you heard that Netherfield Park is let at last?”

  Mr. Bennet replied that he had not.

  “But it is,” returned she; “for Mrs. Long has just been here, and she told me all about it.”

  Mr. Bennet made no answer.

  “Do you not want to know who has taken it?” cried his wife impatiently.

  “You want to tell me, and I have no objection to hearing it.”

  This was invitation enough.

  “Why, my dear, you must know, Mrs. Long says that Netherfield is taken by a young man of large fortune from the north of England; that he came down on Monday in a chaise and four to see the place, and was so much delighted with it, that he agreed with Mr. Morris immediately; that he is to take possession before Michaelmas, and some of his servants are to be in the house by the end of next week.”

  “What is his name?”

  “Bingley.”

  “Is he married or single?”

  “Oh! Single, my dear, to be sure! A single man of large fortune; four or five thousand a year. What a fine thing for our girls!”

  “How so? How can it affect them?”

  “My dear Mr. Bennet,” replied his wife, “how can you be so tiresome! You must know that I am thinking of his marrying one of them.”

  “Is that his design in settling here?”

  “Design! Nonsense, how can you talk so! But it is very likely that he may fall in love with one of them, and therefore you must visit him as soon as he comes.”
""".trimIndent()

// rendered by CI into the screenshots of the README and the docs, one page folded and two pages unfolded
@Composable
@Preview(widthDp = 411, heightDp = 891, name = "folded")
@Preview(widthDp = 841, heightDp = 701, name = "unfolded")
private fun ReaderScreenshot() {
  ReaderPreview(previewTurn = null)
}

@Composable
@Preview(widthDp = 411, heightDp = 891, name = "folded")
@Preview(widthDp = 841, heightDp = 701, name = "unfolded")
private fun ReaderTurnEarlyPreview() {
  ReaderPreview(previewTurn = 0.3F)
}

@Composable
@Preview(widthDp = 411, heightDp = 891, name = "folded")
@Preview(widthDp = 841, heightDp = 701, name = "unfolded")
private fun ReaderTurnLatePreview() {
  ReaderPreview(previewTurn = 0.7F)
}

@Composable
@Preview(widthDp = 411, heightDp = 891, name = "folded")
@Preview(widthDp = 841, heightDp = 701, name = "unfolded")
private fun ReaderSepiaControlsPreview() {
  ReaderPreview(
    previewTurn = null,
    settings = ReaderSettings(theme = ReaderTheme.Sepia, font = ReaderFont.Sans),
    showControls = true,
  )
}

@Composable
private fun ReaderPreview(
  previewTurn: Float?,
  settings: ReaderSettings = ReaderSettings(),
  showControls: Boolean = false,
) {
  val readAloud = chapterOne.indexOf("“My dear Mr. Bennet,”")
  val state = ReaderState.Loaded(
    content = ReaderContent.Epub(
      title = "Pride and Prejudice",
      chapters = listOf(chapterOne + "\n\n" + chapterOne),
      chapterTitles = listOf("Chapter 1"),
    ),
    position = EbookPosition(),
    readingAloud = true,
    readingRange = readAloud until chapterOne.indexOf('\n', readAloud),
    settings = settings,
    voice = VoiceState(
      natural = true,
      naturalReady = true,
      downloadProgress = null,
      downloadFailed = false,
      speechRate = 1F,
    ),
    highlights = listOf(
      Highlight(section = 0, start = 0, end = chapterOne.indexOf(','), color = HighlightColor.Yellow),
    ),
  )
  VoiceTheme {
    Scaffold(
      topBar = {
        ReaderTopBar(
          title = state.content.title,
          readingAloud = true,
          bookmarked = true,
          showSearch = true,
          onCloseClick = {},
          onReadAloudClick = {},
          onContentsClick = {},
          onDisplayClick = {},
          onBookmarkClick = {},
          onSearchClick = {},
        )
      },
    ) { contentPadding ->
      Book(
        state = state,
        onPageTurn = { _, _ -> },
        selection = remember { PageSelection() },
        onHighlightClick = {},
        onVisiblePagesChange = {},
        modifier = Modifier
          .fillMaxSize()
          .padding(contentPadding),
        paginateNow = true,
        previewTurn = previewTurn,
        overlay = { pages, pagesPerSpread, _ ->
          if (showControls) {
            ReaderControls(
              state = state,
              pages = pages,
              pagesPerSpread = pagesPerSpread,
              barsVisible = true,
              onTurn = {},
              onJump = { _, _ -> },
            )
          }
        },
      )
    }
  }
}
