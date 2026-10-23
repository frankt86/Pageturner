@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

@Composable
internal fun SearchSheet(
  content: ReaderContent,
  search: suspend (String) -> List<SearchResult>,
  onResultClick: (SearchResult) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  val latestSearch by rememberUpdatedState(search)
  val trimmed = query.trim()
  // null while searching
  val results by produceState<List<SearchResult>?>(emptyList(), trimmed) {
    if (trimmed.length < 2) {
      value = emptyList()
      return@produceState
    }
    value = null
    // waits for a pause in typing
    delay(300)
    value = latestSearch(trimmed)
  }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberBottomSheetState(
      initialValue = Hidden,
      enabledValues = setOf(Hidden, Expanded),
    ),
  ) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      placeholder = { Text(stringResource(StringsR.string.reader_search)) },
      leadingIcon = { Icon(VoiceIcons.Search, contentDescription = null) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .focusRequester(focusRequester),
    )
    val found = results
    if (found != null && found.isEmpty() && trimmed.length >= 2) {
      Text(
        text = stringResource(StringsR.string.reader_search_empty),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 32.dp, vertical = 48.dp),
      )
    }
    LazyColumn {
      items(found.orEmpty()) { result ->
        SearchResultRow(
          content = content,
          result = result,
          onClick = { onResultClick(result) },
        )
      }
    }
  }
}

@Composable
private fun SearchResultRow(
  content: ReaderContent,
  result: SearchResult,
  onClick: () -> Unit,
) {
  val matchColor = MaterialTheme.colorScheme.tertiaryContainer
  Column(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 24.dp, vertical = 10.dp),
  ) {
    Text(
      text = when (content) {
        is ReaderContent.Epub -> content.chapterTitle(result.match.section)
        is ReaderContent.Pdf -> stringResource(StringsR.string.reader_page, result.match.section + 1, content.sectionCount)
      },
      style = MaterialTheme.typography.labelLarge.bookText(),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    val snippet = remember(result, matchColor) {
      buildAnnotatedString {
        append(result.snippet)
        val end = (result.snippetMatchStart + result.match.end - result.match.start).coerceAtMost(result.snippet.length)
        addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = matchColor), result.snippetMatchStart, end)
      }
    }
    Text(
      text = snippet,
      style = MaterialTheme.typography.bodyMedium.bookText(),
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
