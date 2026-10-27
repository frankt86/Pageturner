@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.opds.feed

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import java.text.NumberFormat
import voice.core.strings.R as StringsR

@Composable
internal fun PublicationSheet(
  publication: PublicationViewState,
  onDismiss: () -> Unit,
  onDownload: () -> Unit,
  onCancelDownload: () -> Unit,
  onRead: (Uri) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberBottomSheetState(
      initialValue = Hidden,
      enabledValues = setOf(Hidden, Expanded),
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)
        .padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      CatalogCover(
        image = publication.cover,
        modifier = Modifier
          .height(200.dp)
          .aspectRatio(2F / 3F)
          .clip(RoundedCornerShape(12.dp)),
      )
      Spacer(Modifier.height(20.dp))
      Text(
        text = publication.title,
        style = MaterialTheme.typography.headlineSmall.bookText(),
        textAlign = TextAlign.Center,
      )
      publication.authors?.let { authors ->
        Spacer(Modifier.height(4.dp))
        Text(
          text = authors,
          style = MaterialTheme.typography.bodyLarge.bookText(),
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
      Spacer(Modifier.height(24.dp))
      DownloadAction(
        download = publication.download,
        onDownload = onDownload,
        onCancelDownload = onCancelDownload,
        onRead = onRead,
      )
      publication.summary?.let { summary ->
        Spacer(Modifier.height(24.dp))
        Text(
          modifier = Modifier.fillMaxWidth(),
          text = summary,
          style = MaterialTheme.typography.bodyMedium.bookText(),
        )
      }
    }
  }
}

@Composable
private fun DownloadAction(
  download: EntryDownload,
  onDownload: () -> Unit,
  onCancelDownload: () -> Unit,
  onRead: (Uri) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .semantics { liveRegion = LiveRegionMode.Polite },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    when (download) {
      is EntryDownload.Available -> PrimaryButton(
        text = stringResource(StringsR.string.opds_entry_download, download.format),
        onClick = onDownload,
      )
      is EntryDownload.Downloading -> {
        val progress = download.progress
        if (progress != null) {
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            modifier = Modifier.weight(1F),
            text = if (progress != null) {
              stringResource(StringsR.string.library_book_downloading) + " · " +
                NumberFormat.getPercentInstance().format(progress.toDouble())
            } else {
              stringResource(StringsR.string.library_book_downloading)
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant,
          )
          TextButton(onClick = onCancelDownload) {
            Text(stringResource(StringsR.string.book_download_stop))
          }
        }
      }
      EntryDownload.Failed -> {
        Text(
          text = stringResource(StringsR.string.book_download_failed),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.error,
          textAlign = TextAlign.Center,
        )
        PrimaryButton(
          text = stringResource(StringsR.string.book_download_retry),
          onClick = onDownload,
        )
      }
      is EntryDownload.Downloaded -> {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = VoiceIcons.DownloadDone,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = stringResource(StringsR.string.opds_entry_downloaded),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
          )
        }
        PrimaryButton(
          text = stringResource(StringsR.string.opds_entry_read),
          onClick = { onRead(download.uri) },
        )
      }
      is EntryDownload.Unavailable -> {
        Button(
          onClick = {},
          enabled = false,
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonDefaults.MediumContainerHeight),
        ) {
          Text(stringResource(StringsR.string.book_download_action))
        }
        Text(
          text = stringResource(StringsR.string.opds_entry_unsupported, download.formats),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
) {
  Button(
    onClick = onClick,
    shapes = ButtonDefaults.shapes(),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = ButtonDefaults.MediumContainerHeight),
  ) {
    Text(text)
  }
}
