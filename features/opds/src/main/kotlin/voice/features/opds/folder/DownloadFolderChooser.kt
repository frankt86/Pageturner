package voice.features.opds.folder

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.opds.download.DownloadFolder
import voice.core.opds.download.OpdsDownloadFolders

/** Asks for the download folder, in the settings of the catalogs and before the first download. */
internal class DownloadFolderChooser(
  private val folders: OpdsDownloadFolders,
  private val scope: CoroutineScope,
) {

  private var isOpen by mutableStateOf(false)
  private var failed by mutableStateOf(false)
  private var busy by mutableStateOf(false)
  private var onChosen: (() -> Unit)? = null

  /** The sheet, or null while it's closed. */
  @Composable
  fun sheet(): DownloadFolderSheetState? {
    val choices = remember { folders.choices }.collectAsState(initial = emptyList()).value
    val chosen = current()
    if (!isOpen) return null
    return DownloadFolderSheetState(
      folders = choices.map { folder ->
        DownloadFolderItem(
          uri = folder.uri,
          name = folder.name,
          type = folder.type,
          selected = folder.uri == chosen?.uri,
        )
      },
      failed = failed,
      busy = busy,
    )
  }

  @Composable
  fun current(): DownloadFolder? = remember { folders.current }.collectAsState(initial = null).value

  fun show() {
    open(onChosen = null)
  }

  /** Runs [download] right away, or once a folder is chosen when there's none or it can't be used anymore. */
  fun beforeDownload(download: () -> Unit) {
    scope.launch {
      if (folders.current.first() != null) {
        download()
      } else {
        open(onChosen = download)
      }
    }
  }

  fun onChoose(uri: Uri) {
    if (busy) return
    scope.launch {
      folders.choose(uri)
      close(chosen = true)
    }
  }

  fun onPick(uri: Uri) {
    if (busy) return
    busy = true
    failed = false
    scope.launch {
      val added = folders.addAndChoose(uri)
      busy = false
      if (added) close(chosen = true) else failed = true
    }
  }

  fun onDismiss() {
    close(chosen = false)
  }

  private fun open(onChosen: (() -> Unit)?) {
    this.onChosen = onChosen
    failed = false
    busy = false
    isOpen = true
  }

  private fun close(chosen: Boolean) {
    val download = onChosen
    onChosen = null
    isOpen = false
    if (chosen) download?.invoke()
  }
}
