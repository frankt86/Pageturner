package voice.core.opds.download

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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
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
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

public data class OpdsEntryKey(
  val catalogId: String,
  val entryId: String,
)

public sealed interface OpdsDownloadState {

  /** [progress] is null while the size isn't known. */
  public data class Downloading(val progress: Float?) : OpdsDownloadState

  public data object Failed : OpdsDownloadState
}

/**
 * Downloads books from the catalogs into the app's own files and adds them to the library. Ebooks are small, so a
 * download runs in the app's process, without a service or a notification.
 */
@SingleIn(AppScope::class)
@Inject
public class OpdsDownloads(
  private val http: ServerHttpClient,
  private val ebooksStore: EbooksStore,
  private val context: Context,
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
    val folder = File(context.filesDir, "opds").apply { mkdirs() }
    val name = Uuid.random().toString()
    val partial = File(folder, "$name.part")
    val file = File(folder, "$name.${format.name.lowercase()}")
    try {
      fetch(key, catalog, acquisition.url, partial) { progress ->
        _states.update { it + (key to OpdsDownloadState.Downloading(progress)) }
      }
      if (!partial.renameTo(file)) throw IOException("Could not move the download")
    } catch (e: Exception) {
      partial.delete()
      file.delete()
      throw e
    }
    val cover = (publication.coverUrl ?: publication.thumbnailUrl)?.let { cover(catalog, it) }
    add(
      key,
      Ebook(
        uri = Uri.fromFile(file),
        name = publication.title.ifBlank { name },
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
      .firstOrNull { it.matches(key) && it.uri.path?.let(::File)?.exists() == true }
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
      replaced = ebooks.filter { it.matches(key) }
      ebooks.filterNot { it.matches(key) } + ebook
    }
    replaced.forEach { old ->
      old.uri.path?.let(::File)?.takeIf { it.parentFile?.name == "opds" }?.delete()
    }
  }

  private suspend fun fetch(
    key: OpdsEntryKey,
    catalog: OpdsCatalog,
    url: String,
    target: File,
    onProgress: (Float?) -> Unit,
  ) {
    val call = http.client.newCall(downloadRequest(catalog, url))
    calls[key] = call
    call.execute().use { response ->
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
      val total = response.body.contentLength()
      var read = 0L
      var lastReported = -1
      response.body.byteStream().use { input ->
        target.outputStream().use { output ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            read += count
            if (total > 0) {
              val percent = (read * 100 / total).toInt()
              if (percent != lastReported) {
                lastReported = percent
                onProgress(read.toFloat() / total)
              }
            }
          }
        }
      }
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

private fun downloadRequest(
  catalog: OpdsCatalog,
  url: String,
): Request {
  return Request.Builder()
    .url(url.toHttpUrlOrNull() ?: throw IOException("Not a url"))
    .apply { catalog.authorizationFor(url)?.let { header("Authorization", it) } }
    .build()
}
