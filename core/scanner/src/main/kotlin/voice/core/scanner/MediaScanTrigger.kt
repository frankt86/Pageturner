package voice.core.scanner

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import voice.core.data.folders.AudiobookFolders
import voice.core.data.folders.FolderType
import voice.core.data.repo.BookRepository
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.CachedDocumentFileFactory
import voice.core.documentfile.FileBasedDocumentFile
import voice.core.logging.api.Logger
import java.io.File
import kotlin.time.measureTime

@SingleIn(AppScope::class)
@Inject
public class MediaScanTrigger
internal constructor(
  private val audiobookFolders: AudiobookFolders,
  private val scanner: MediaScanner,
  private val ebookScanner: EbookScanner,
  private val coverScanner: CoverScanner,
  private val bookRepo: BookRepository,
  private val documentFileFactory: CachedDocumentFileFactory,
  private val context: Context,
) {

  public val scannerActive: Flow<Boolean>
    field = MutableStateFlow(false)

  private val scope = CoroutineScope(Dispatchers.IO)
  private var scanningJob: Job? = null

  public fun scan(restartIfScanning: Boolean = false) {
    Logger.i("scanForFiles with restartIfScanning=$restartIfScanning")
    if (scanningJob?.isActive == true && !restartIfScanning) {
      return
    }
    val oldJob = scanningJob
    scanningJob = scope.launch {
      scannerActive.value = true
      oldJob?.cancelAndJoin()

      measureTime {
        val pickedFolders: Map<FolderType, List<CachedDocumentFile>> = audiobookFolders.all()
          .first()
          .mapValues { (_, documentFilesWithUri) ->
            documentFilesWithUri.map {
              documentFileFactory.create(it.documentFile.uri)
            }
          }
        val downloaded = downloadedAudiobooks(context)
        val folders = pickedFolders + (FolderType.SingleFolder to pickedFolders[FolderType.SingleFolder].orEmpty() + downloaded)
        scanner.scan(folders)
        ebookScanner.scan(folders.values.flatten())
      }.also {
        Logger.i("scan took $it")
      }
      scannerActive.value = false

      val books = bookRepo.all()
      coverScanner.scan(books)
    }
  }
}

/**
 * The folder in the app's own storage that audiobooks downloaded from a catalog went to, one folder per book, before
 * they went to a folder of the library. The scan treats each of them like a folder picked as a single book.
 */
public fun downloadedAudiobooksFolder(context: Context): File = File(context.filesDir, "audiobooks")

// a download that's still running lives in a folder starting with a dot
private fun downloadedAudiobooks(context: Context): List<CachedDocumentFile> {
  return downloadedAudiobooksFolder(context).listFiles().orEmpty()
    .filter { it.isDirectory && !it.name.startsWith(".") }
    .map { FileBasedDocumentFile(it) }
}
