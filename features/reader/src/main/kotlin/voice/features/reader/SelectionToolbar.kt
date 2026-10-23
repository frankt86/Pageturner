@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import voice.core.data.HighlightColor
import voice.core.logging.api.Logger
import voice.core.strings.R as StringsR

@Composable
internal fun SelectionToolbar(
  selectedText: String,
  highlightNames: Map<HighlightColor, String>,
  onHighlightClick: (HighlightColor) -> Unit,
  onNoteClick: () -> Unit,
  onLookUp: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  HorizontalFloatingToolbar(
    expanded = true,
    modifier = modifier,
  ) {
    HighlightColor.entries.forEach { color ->
      val name = highlightColorName(color, highlightNames)
      IconButton(
        onClick = { onHighlightClick(color) },
        modifier = Modifier.semantics { contentDescription = name },
      ) {
        Box(
          Modifier
            .size(24.dp)
            .background(color.ink(), CircleShape),
        )
      }
    }
    TextButton(onClick = onNoteClick) {
      Text(stringResource(StringsR.string.reader_highlight_note))
    }
    TextButton(
      onClick = {
        lookUp(context, selectedText)
        onLookUp()
      },
    ) {
      Text(stringResource(StringsR.string.reader_look_up))
    }
  }
}

// dictionaries and translators offer themselves for selected text, a web search is the fallback
internal fun lookUp(
  context: Context,
  text: String,
) {
  val processText = Intent(Intent.ACTION_PROCESS_TEXT)
    .setType("text/plain")
    .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
  val webSearch = Intent(Intent.ACTION_WEB_SEARCH)
    .putExtra(SearchManager.QUERY, text)
  for (intent in listOf(processText, webSearch)) {
    try {
      context.startActivity(intent)
      return
    } catch (e: ActivityNotFoundException) {
      Logger.d("No app to look up a word with ${intent.action}")
    }
  }
}
