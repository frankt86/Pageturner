@file:OptIn(ExperimentalMaterial3Api::class)

package voice.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import voice.core.data.EbookPosition
import voice.core.data.Highlight
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

internal enum class ContentsTab {
  Contents,
  Bookmarks,
  Highlights,
}

@Composable
internal fun ContentsSheet(
  // the size of the reader's controls
  scale: Float,
  state: ReaderState.Loaded,
  onChapterClick: (Int) -> Unit,
  onMarkClick: (EbookPosition) -> Unit,
  onBookmarkDelete: (EbookPosition) -> Unit,
  onHighlightDelete: (Highlight) -> Unit,
  onDismiss: () -> Unit,
) {
  val content = state.content
  // a PDF has no chapters to list and no text to highlight
  val tabs = if (content is ReaderContent.Epub) ContentsTab.entries else listOf(ContentsTab.Bookmarks)
  var selectedTab by rememberSaveable { mutableStateOf(tabs.first()) }
  ModalBottomSheet(onDismissRequest = onDismiss) {
    ScaledControls(scale) {
      if (tabs.size > 1) {
        PrimaryTabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
          tabs.forEach { tab ->
            Tab(
              selected = tab == selectedTab,
              onClick = { selectedTab = tab },
              text = { Text(tabTitle(tab)) },
            )
          }
        }
      } else {
        Text(
          text = tabTitle(tabs.first()),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
      }
      when (if (selectedTab in tabs) selectedTab else tabs.first()) {
        ContentsTab.Contents -> if (content is ReaderContent.Epub) {
          Chapters(content, state.position.section, onChapterClick)
        }
        ContentsTab.Bookmarks -> Bookmarks(content, state.bookmarks, onMarkClick, onBookmarkDelete)
        ContentsTab.Highlights -> if (content is ReaderContent.Epub) {
          Highlights(content, state.highlights, state.settings.highlightOpacity, onMarkClick, onHighlightDelete)
        }
      }
    }
  }
}

@Composable
internal fun tabTitle(tab: ContentsTab): String = stringResource(
  when (tab) {
    ContentsTab.Contents -> StringsR.string.reader_contents
    ContentsTab.Bookmarks -> StringsR.string.reader_bookmarks
    ContentsTab.Highlights -> StringsR.string.reader_highlights
  },
)

@Composable
private fun Chapters(
  content: ReaderContent.Epub,
  currentSection: Int,
  onChapterClick: (Int) -> Unit,
) {
  val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentSection)
  LazyColumn(state = listState) {
    items(content.sectionCount) { section ->
      val current = section == currentSection
      Text(
        text = content.chapterTitle(section),
        style = MaterialTheme.typography.bodyLarge.bookText(),
        fontWeight = if (current) FontWeight.Bold else null,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onChapterClick(section) }
          .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
          .padding(horizontal = 24.dp, vertical = 14.dp),
      )
    }
  }
}

@Composable
private fun Bookmarks(
  content: ReaderContent,
  bookmarks: List<EbookPosition>,
  onBookmarkClick: (EbookPosition) -> Unit,
  onBookmarkDelete: (EbookPosition) -> Unit,
) {
  if (bookmarks.isEmpty()) {
    EmptyMarks(stringResource(StringsR.string.reader_bookmarks_empty))
    return
  }
  val deleteLabel = stringResource(StringsR.string.reader_bookmark_delete)
  LazyColumn {
    items(bookmarks) { bookmark ->
      MarkRow(
        title = sectionTitle(content, bookmark.section),
        text = (content as? ReaderContent.Epub)?.let { AnnotatedString(snippet(it.chapters[bookmark.section], bookmark.offset)) },
        deleteLabel = deleteLabel,
        onClick = { onBookmarkClick(bookmark) },
        onDelete = { onBookmarkDelete(bookmark) },
      )
    }
  }
}

@Composable
private fun Highlights(
  content: ReaderContent.Epub,
  highlights: List<Highlight>,
  opacity: Float,
  onHighlightClick: (EbookPosition) -> Unit,
  onHighlightDelete: (Highlight) -> Unit,
) {
  if (highlights.isEmpty()) {
    EmptyMarks(stringResource(StringsR.string.reader_highlights_empty))
    return
  }
  val deleteLabel = stringResource(StringsR.string.reader_highlight_delete)
  LazyColumn {
    items(highlights) { highlight ->
      val quote = content.text(BookRange(highlight.section, highlight.start, highlight.end)).replace('\n', ' ')
      MarkRow(
        title = content.chapterTitle(highlight.section),
        text = buildAnnotatedString {
          withStyle(SpanStyle(background = highlight.color.background(opacity))) { append(quote) }
        },
        note = highlight.note,
        deleteLabel = deleteLabel,
        onClick = { onHighlightClick(EbookPosition(highlight.section, highlight.start)) },
        onDelete = { onHighlightDelete(highlight) },
      )
    }
  }
}

@Composable
private fun sectionTitle(
  content: ReaderContent,
  section: Int,
): String = when (content) {
  is ReaderContent.Epub -> content.chapterTitle(section)
  is ReaderContent.Pdf -> stringResource(StringsR.string.reader_page, section + 1, content.sectionCount)
}

@Composable
internal fun MarkRow(
  title: String,
  text: AnnotatedString?,
  deleteLabel: String,
  onClick: () -> Unit,
  onDelete: () -> Unit,
  note: String? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1F)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.bookText(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (text != null) {
        Text(
          text = text,
          style = MaterialTheme.typography.bodyMedium.bookText(),
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (note != null) {
        Text(
          text = note,
          style = MaterialTheme.typography.bodyMedium.bookText(),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    }
    IconButton(onClick = onDelete) {
      Icon(VoiceIcons.Delete, contentDescription = deleteLabel)
    }
  }
}

@Composable
internal fun EmptyMarks(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 32.dp, vertical = 48.dp),
  )
}
