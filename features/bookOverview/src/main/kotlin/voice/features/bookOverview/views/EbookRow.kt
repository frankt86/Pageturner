@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.bookOverview.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import voice.core.data.EbookFormat
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import voice.features.bookOverview.overview.EbookItemViewState

@Composable
internal fun EbookRow(
  ebook: EbookItemViewState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(24.dp),
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(onClick = onClick),
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = VoiceIcons.Book,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
          )
        }
      }
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1F)) {
        Text(
          text = ebook.name,
          style = MaterialTheme.typography.titleMediumEmphasized.bookText(),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (ebook.progress > 0F) {
            LinearProgressIndicator(
              progress = { ebook.progress },
              modifier = Modifier
                .weight(1F)
                .padding(end = 12.dp),
              gapSize = 0.dp,
              drawStopIndicator = {},
            )
          }
          Text(
            text = when (ebook.format) {
              EbookFormat.Epub -> "EPUB"
              EbookFormat.Pdf -> "PDF"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
@Preview
private fun EbookRowPreview() {
  EbookRow(
    ebook = EbookItemViewState(
      uri = "content://book.epub".toUri(),
      name = "Book",
      format = EbookFormat.Epub,
      progress = 0.4F,
    ),
    onClick = {},
  )
}
