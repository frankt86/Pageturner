@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.opds.feed

import android.net.Uri
import android.text.format.Formatter
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import voice.core.data.BookId
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
  onDownloadAudiobook: () -> Unit,
  onCancelAudiobookDownload: () -> Unit,
  onListen: (BookId) -> Unit,
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
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        publication.download?.let { download ->
          DownloadAction(
            download = download,
            downloadText = (download as? EntryDownload.Available)?.let {
              stringResource(StringsR.string.opds_entry_download, it.format)
            },
            details = null,
            openText = stringResource(StringsR.string.opds_entry_read),
            primary = true,
            onDownload = onDownload,
            onCancelDownload = onCancelDownload,
            onOpen = { publication.readUri?.let(onRead) },
          )
        }
        publication.audiobook?.let { audiobook ->
          val context = LocalContext.current
          DownloadAction(
            download = audiobook,
            downloadText = stringResource(StringsR.string.opds_entry_download_audiobook),
            details = (audiobook as? EntryDownload.Available)?.let { available ->
              listOfNotNull(available.format, available.size?.let { Formatter.formatShortFileSize(context, it) })
                .joinToString(" · ")
            },
            openText = stringResource(StringsR.string.opds_entry_listen),
            // the ebook comes first when a book comes as both
            primary = publication.download == null,
            onDownload = onDownloadAudiobook,
            onCancelDownload = onCancelAudiobookDownload,
            onOpen = { publication.audiobookId?.let(onListen) },
          )
        }
      }
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
  downloadText: String?,
  details: String?,
  openText: String,
  primary: Boolean,
  onDownload: () -> Unit,
  onCancelDownload: () -> Unit,
  onOpen: () -> Unit,
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
      is EntryDownload.Available -> {
        ActionButton(
          text = downloadText.orEmpty(),
          primary = primary,
          onClick = onDownload,
        )
        details?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
          )
        }
      }
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
            text = downloadingText(download),
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
        ActionButton(
          text = stringResource(StringsR.string.book_download_retry),
          primary = primary,
          onClick = onDownload,
        )
      }
      EntryDownload.Downloaded -> {
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
        ActionButton(
          text = openText,
          primary = primary,
          onClick = onOpen,
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
private fun downloadingText(download: EntryDownload.Downloading): String {
  val downloading = stringResource(StringsR.string.library_book_downloading)
  val progress = download.progress
  return when {
    download.totalBytes > 0 -> {
      val context = LocalContext.current
      stringResource(
        StringsR.string.book_download_progress,
        Formatter.formatShortFileSize(context, download.downloadedBytes),
        Formatter.formatShortFileSize(context, download.totalBytes),
      )
    }
    progress != null -> downloading + " · " + NumberFormat.getPercentInstance().format(progress.toDouble())
    // the server didn't tell the size, so at least the download shows that it moves
    download.downloadedBytes > 0 ->
      downloading + " · " +
        Formatter.formatShortFileSize(LocalContext.current, download.downloadedBytes)
    else -> downloading
  }
}

@Composable
private fun ActionButton(
  text: String,
  primary: Boolean,
  onClick: () -> Unit,
) {
  val modifier = Modifier
    .fillMaxWidth()
    .heightIn(min = ButtonDefaults.MediumContainerHeight)
  if (primary) {
    Button(
      onClick = onClick,
      shapes = ButtonDefaults.shapes(),
      modifier = modifier,
    ) {
      Text(text)
    }
  } else {
    FilledTonalButton(
      onClick = onClick,
      shapes = ButtonDefaults.shapes(),
      modifier = modifier,
    ) {
      Text(text)
    }
  }
}
