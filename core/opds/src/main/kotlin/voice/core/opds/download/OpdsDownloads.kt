package voice.core.opds.download

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import voice.core.audiobookshelf.http.ServerHttpClient
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.OpdsSource
import voice.core.data.store.EbooksStore
import voice.core.logging.api.Logger
import voice.core.opds.CatalogType
import voice.core.opds.OpdsCatalog
import voice.core.opds.feed.OpdsFormat
import voice.core.opds.feed.OpdsPublication
import voice.core.opds.komga.komgaBookId
import voice.core.scanner.MediaScanTrigger
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

public data class OpdsEntryKey(
  val catalogId: String,
  val entryId: String,
)

public sealed interface OpdsDownloadState {

  /** [progress] is null while the size isn't known. [totalBytes] is 0 then. */
  public data class Downloading(
    val progress: Float?,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
  ) : OpdsDownloadState

  public data object Failed : OpdsDownloadState
}

/**
 * Downloads ebooks from the catalogs into the download folder of the library, where the scan finds them. Ebooks are
 * small, so a download runs in the app's process, without a service or a notification. Books downloaded before the
 * download folder existed stay in the app's own files.
 */
@SingleIn(AppScope::class)
@Inject
public class OpdsDownloads internal constructor(
  private val http: ServerHttpClient,
  private val ebooksStore: EbooksStore,
  private val context: Context,
  private val downloadFolders: OpdsDownloadFolders,
  private val writer: LibraryFolderWriter,
  private val mediaScanTrigger: MediaScanTrigger,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val jobs = ConcurrentHashMap<OpdsEntryKey, Job>()
  private val calls = ConcurrentHashMap<OpdsEntryKey, Call>()
  private val _states = MutableStateFlow<Map<OpdsEntryKey, OpdsDownloadState>>(emptyMap())

  /** The downloads that run or failed. A finished one is in the library instead. */
  public val states: StateFlow<Map<OpdsEntryKey, OpdsDownloadState>> = _states.asStateFlow()

  /** The books of the catalog that are in the library, by their entry id. */
  public fun downloaded(catalogId: String): Flow<Map<String, Uri>> {
    return ebooksStore.data
      .map { ebooks ->
        ebooks.mapNotNull { ebook ->
          val source = ebook.opdsSource ?: return@mapNotNull null
          if (source.catalogId != catalogId || !ebook.available || ebook.hidden) return@mapNotNull null
          source.entryId to ebook.uri
        }.toMap()
      }
      .distinctUntilChanged()
  }

  public fun download(
    catalog: OpdsCatalog,
    publication: OpdsPublication,
  ) {
    val acquisition = publication.preferredAcquisition ?: return
    val format = when (acquisition.format) {
      OpdsFormat.Epub -> EbookFormat.Epub
      OpdsFormat.Pdf -> EbookFormat.Pdf
      OpdsFormat.Mobi -> EbookFormat.Mobi
      OpdsFormat.Azw3 -> EbookFormat.Azw3
      null -> return
    }
    val key = OpdsEntryKey(catalog.id, publication.id)
    if (jobs[key]?.isActive == true) return
    _states.update { it + (key to OpdsDownloadState.Downloading(progress = null)) }
    jobs[key] = scope.launch {
      try {
        if (!restore(key)) {
          downloadAndAdd(key, catalog, publication, format)
        }
        _states.update { it - key }
      } catch (e: CancellationException) {
        _states.update { it - key }
        throw e
      } catch (e: IOException) {
        if (isActive) {
          Logger.w(e, "Could not download a book")
          _states.update { it + (key to OpdsDownloadState.Failed) }
        } else {
          _states.update { it - key }
        }
      } finally {
        calls.remove(key)
        jobs.remove(key)
      }
    }
  }

  private suspend fun downloadAndAdd(
    key: OpdsEntryKey,
    catalog: OpdsCatalog,
    publication: OpdsPublication,
    format: EbookFormat,
  ) {
    val acquisition = publication.preferredAcquisition ?: return
    val folder = downloadFolders.current() ?: throw IOException("No download folder")
    val partial = File(context.cacheDir, "opds").apply { mkdirs() }.resolve(Uuid.random().toString())
    val uri = try {
      fetch(key, catalog, acquisition.url, partial) { progress ->
        _states.update { it + (key to OpdsDownloadState.Downloading(progress)) }
      }
      val parent = writer.bookParent(folder, bookFolderPath(folder.type, publication.authors.firstOrNull()))
      writer.copyFile(
        parent = parent,
        name = safeFileName(publication.title, fallback = "Book"),
        extension = format.name.lowercase(),
        file = partial,
      )
    } finally {
      partial.delete()
    }
    val cover = (publication.coverUrl ?: publication.thumbnailUrl)?.let { cover(catalog, it) }
    add(
      key,
      Ebook(
        uri = uri,
        name = publication.title.ifBlank { "Book" },
        format = format,
        author = publication.authors.joinToString(", ").ifEmpty { null },
        cover = cover?.absolutePath,
        // without a cover from the catalog, the next scan takes the one in the book
        metadataRead = cover != null,
        opdsSource = OpdsSource(
          catalogId = catalog.id,
          entryId = publication.id,
          acquisitionUrl = acquisition.url,
          komgaBookId = if (catalog.type == CatalogType.Komga) komgaBookId(acquisition.url, publication.id) else null,
        ),
      ),
    )
    // the scan keeps the book with what the catalog said about it, as it's in the library under the same uri
    mediaScanTrigger.scan(restartIfScanning = true)
  }

  public fun cancel(key: OpdsEntryKey) {
    jobs[key]?.cancel()
    // a read that waits for the server only stops once the call is cancelled
    calls[key]?.cancel()
    _states.update { it - key }
  }

  /** Brings back a book that was removed from the library while its file stayed. */
  private suspend fun restore(key: OpdsEntryKey): Boolean {
    val existing = ebooksStore.data.first()
      .firstOrNull { it.matches(key) && writer.exists(it.uri) }
      ?: return false
    if (existing.hidden || !existing.available) {
      ebooksStore.updateData { ebooks ->
        ebooks.map { if (it.uri == existing.uri) it.copy(hidden = false, available = true) else it }
      }
    }
    return true
  }

  private suspend fun add(
    key: OpdsEntryKey,
    ebook: Ebook,
  ) {
    var replaced: List<Ebook> = emptyList()
    ebooksStore.updateData { ebooks ->
      // a scan may have found the file already
      val earlier = ebooks.filter { it.matches(key) || it.uri == ebook.uri }
      replaced = earlier.filter { it.matches(key) }
      // a book downloaded again keeps the title, author, cover and series the listener gave it
      val added = ebook.copy(
        customName = earlier.firstNotNullOfOrNull { it.customName },
        customAuthor = earlier.firstNotNullOfOrNull { it.customAuthor },
        customCover = earlier.firstNotNullOfOrNull { it.customCover },
        customSeries = earlier.firstNotNullOfOrNull { it.customSeries },
        customPart = earlier.firstNotNullOfOrNull { it.customPart },
      )
      ebooks.filterNot { it.matches(key) || it.uri == ebook.uri } + added
    }
    // a download from before the download folder was in the app's own files, where nothing else would remove it
    replaced.forEach { old ->
      if (old.uri.scheme == ContentResolver.SCHEME_FILE) {
        old.uri.path?.let(::File)?.takeIf { it.parentFile?.name == "opds" }?.delete()
      }
    }
  }

  private suspend fun fetch(
    key: OpdsEntryKey,
    catalog: OpdsCatalog,
    url: String,
    target: File,
    onProgress: (Float?) -> Unit,
  ) {
    http.client.fetch(downloadRequest(catalog, url), target, onCall = { calls[key] = it }) { read, total ->
      if (total > 0) onProgress(read.toFloat() / total)
    }
  }

  private fun cover(
    catalog: OpdsCatalog,
    url: String,
  ): File? {
    val folder = File(context.filesDir, "ebookCovers").apply { mkdirs() }
    val file = File(folder, Uuid.random().toString())
    return try {
      http.client.newCall(downloadRequest(catalog, url)).execute().use { response ->
        if (!response.isSuccessful) return null
        file.outputStream().use { output ->
          val _ = response.body.byteStream().copyTo(output)
        }
        file
      }
    } catch (e: IOException) {
      Logger.w(e, "Could not download a cover")
      file.delete()
      null
    }
  }
}

private fun Ebook.matches(key: OpdsEntryKey): Boolean {
  val source = opdsSource ?: return false
  return source.catalogId == key.catalogId && source.entryId == key.entryId
}
