package voice.core.opds.download

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Call
import voice.core.audiobookshelf.download.AudiobookshelfDownloads
import voice.core.audiobookshelf.download.BookDownloadState
import voice.core.audiobookshelf.download.DownloadNotifications
import voice.core.audiobookshelf.download.DownloadProgress
import voice.core.audiobookshelf.http.ServerHttpClient
import voice.core.common.comparator.NaturalOrderComparator
import voice.core.data.BookContent
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.datastore.VoiceDataStoreFactory
import voice.core.logging.api.Logger
import voice.core.opds.OpdsCatalog
import voice.core.opds.feed.AudiobookPackaging
import voice.core.opds.feed.OpdsAcquisition
import voice.core.opds.feed.OpdsAudiobook
import voice.core.opds.feed.OpdsParseException
import voice.core.opds.feed.OpdsPublication
import voice.core.opds.feed.isAudioFileName
import voice.core.opds.feed.parseAudiobookManifest
import voice.core.scanner.MediaScanTrigger
import voice.core.scanner.downloadedAudiobooksFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** A book downloaded from a catalog as audio. Its files are a folder of [downloadedAudiobooksFolder]. */
@Serializable
internal data class DownloadedAudiobook(
  val catalogId: String,
  val entryId: String,
  val folder: String,
  val title: String?,
  val author: String?,
  /** Whether the book in the library got the title and author of the catalog yet. */
  val metadataApplied: Boolean = false,
)

@SingleIn(AppScope::class)
internal class DownloadedAudiobookStore(store: DataStore<List<DownloadedAudiobook>>) : DataStore<List<DownloadedAudiobook>> by store {

  @Inject
  constructor(factory: VoiceDataStoreFactory) : this(
    factory.create(
      serializer = ListSerializer(DownloadedAudiobook.serializer()),
      defaultValue = emptyList(),
      fileName = "opdsAudiobooks",
    ),
  )
}

private data class AudiobookDownload(
  val name: String,
  val cover: File?,
  val state: OpdsDownloadState,
)

/**
 * Downloads audiobooks from the catalogs. Their files go to a folder of their own in the app's storage, which the
 * scanner adds to the library like a folder picked as a single book, so the chapters come from the files. The title,
 * author and cover come from the catalog. Audiobooks are large, so a foreground service keeps them downloading with a
 * notification, the same as books from Audiobookshelf.
 */
@SingleIn(AppScope::class)
@Inject
public class OpdsAudiobookDownloads internal constructor(
  private val http: ServerHttpClient,
  private val context: Context,
  private val store: DownloadedAudiobookStore,
  private val bookRepository: BookRepository,
  private val mediaScanTrigger: MediaScanTrigger,
  private val notifications: DownloadNotifications,
  private val serverDownloads: AudiobookshelfDownloads,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val jobs = ConcurrentHashMap<OpdsEntryKey, Job>()
  private val calls = ConcurrentHashMap<OpdsEntryKey, Call>()
  private val downloads = MutableStateFlow<Map<OpdsEntryKey, AudiobookDownload>>(emptyMap())

  /** The downloads that run or failed. A finished one is in the library instead. */
  public val states: Flow<Map<OpdsEntryKey, OpdsDownloadState>> = downloads
    .map { downloads -> downloads.mapValues { it.value.state } }
    .distinctUntilChanged()

  /** The audiobooks of the catalog that are in the library, by their entry id. */
  public fun downloaded(catalogId: String): Flow<Map<String, BookId>> {
    return combine(store.data, bookRepository.flow()) { entries, books ->
      val inLibrary = books.mapTo(HashSet()) { it.id }
      entries
        .filter { it.catalogId == catalogId }
        .mapNotNull { entry -> bookId(entry).takeIf { it in inLibrary }?.let { entry.entryId to it } }
        .toMap()
    }.distinctUntilChanged()
  }

  /** Whether to ask for notifications before a download, so its progress shows. Pageturner asks once. */
  public suspend fun shouldAskForNotifications(): Boolean = serverDownloads.shouldAskForNotifications()

  public suspend fun onAskedForNotifications() {
    serverDownloads.onAskedForNotifications()
  }

  // the scanner adds the book with what the files say, then it gets the title and author from the catalog
  internal fun start() {
    scope.launch {
      combine(store.data, bookRepository.flow()) { entries, books ->
        val inLibrary = books.associateBy { it.id }
        entries.filter { !it.metadataApplied && bookId(it) in inLibrary }
      }
        .distinctUntilChanged()
        .collect { found ->
          found.forEach { entry ->
            bookRepository.updateBook(bookId(entry)) { it.withCatalogMetadata(entry) }
          }
          if (found.isNotEmpty()) {
            store.updateData { entries -> entries.map { if (it in found) it.copy(metadataApplied = true) else it } }
          }
        }
    }
  }

  public fun download(
    catalog: OpdsCatalog,
    publication: OpdsPublication,
  ) {
    val audiobook = publication.audiobook ?: return
    val key = OpdsEntryKey(catalog.id, publication.id)
    if (jobs[key]?.isActive == true) return
    val name = publication.title.ifBlank { "Audiobook" }
    val starting = AudiobookDownload(name = name, cover = null, state = OpdsDownloadState.Downloading(progress = null))
    downloads.update { it + (key to starting) }
    notifications.cancelResult(key.tag)
    startService()
    jobs[key] = scope.launch {
      try {
        val folder = downloadAndAdd(key, catalog, publication, audiobook)
        downloads.update { it - key }
        notifications.downloaded(key.tag, name, File(folder, COVER_FILE).takeIf { it.exists() })
      } catch (e: CancellationException) {
        downloads.update { it - key }
        throw e
      } catch (e: IOException) {
        if (isActive) {
          Logger.w(e, "Could not download an audiobook")
          downloads.update { downloads ->
            val failed = downloads[key]?.copy(state = OpdsDownloadState.Failed) ?: return@update downloads
            downloads + (key to failed)
          }
          notifications.failed(key.tag, name, cover = null, retry = null)
        } else {
          downloads.update { it - key }
        }
      } finally {
        calls.remove(key)
        jobs.remove(key)
      }
    }
  }

  public fun cancel(key: OpdsEntryKey) {
    jobs[key]?.cancel()
    calls[key]?.cancel()
    downloads.update { it - key }
  }

  internal fun cancelAll() {
    jobs.keys.toList().forEach { cancel(it) }
  }

  /** The notification of the running downloads, or null when none runs. */
  internal val notification: StateFlow<Notification?> = MutableStateFlow<Notification?>(null).also { notification ->
    scope.launch {
      downloads
        .map { downloads -> downloads.values.filter { it.state is OpdsDownloadState.Downloading } }
        .distinctUntilChanged()
        .collect { running ->
          notification.value = if (running.isEmpty()) null else progressNotification(running)
          // Android drops updates of a notification that come too fast, the state flow keeps the latest one
          delay(NOTIFICATION_INTERVAL)
        }
    }
  }

  internal fun currentNotification(): Notification {
    return notification.value ?: notifications.progress(emptyList(), OpdsDownloadService.stopIntent(context))
  }

  private fun progressNotification(running: List<AudiobookDownload>): Notification {
    return notifications.progress(
      downloads = running.map { download ->
        val state = download.state as OpdsDownloadState.Downloading
        DownloadProgress(
          name = download.name,
          cover = download.cover,
          state = BookDownloadState.Downloading(
            progress = state.progress ?: 0F,
            downloadedBytes = state.downloadedBytes,
            totalBytes = state.totalBytes,
            waitingFor = null,
          ),
        )
      },
      stop = OpdsDownloadService.stopIntent(context),
    )
  }

  private fun startService() {
    notifications.createChannel()
    try {
      ContextCompat.startForegroundService(context, OpdsDownloadService.startIntent(context))
    } catch (e: IllegalStateException) {
      // Android only allows it while Pageturner is visible, which it is when a download starts
      Logger.w(e, "Could not start the audiobook downloads")
    }
  }

  /** @return the folder of the book */
  private suspend fun downloadAndAdd(
    key: OpdsEntryKey,
    catalog: OpdsCatalog,
    publication: OpdsPublication,
    audiobook: OpdsAudiobook,
  ): File {
    val root = downloadedAudiobooksFolder(context)
    // the scanner skips folders that start with a dot, so it never sees a half downloaded book
    val partial = File(root, ".${Uuid.random()}")
    try {
      if (!partial.mkdirs()) throw IOException("Could not create the folder")
      (publication.coverUrl ?: publication.thumbnailUrl)?.let { url ->
        val cover = File(partial, COVER_FILE)
        if (downloadCover(catalog, url, cover)) {
          downloads.update { downloads ->
            val download = downloads[key] ?: return@update downloads
            downloads + (key to download.copy(cover = cover))
          }
        }
      }
      when (audiobook.packaging) {
        AudiobookPackaging.Files -> downloadFiles(key, catalog, publication.title, audiobook.files, audiobook.size, partial)
        AudiobookPackaging.Manifest -> {
          val files = manifestFiles(key, catalog, audiobook.files.single().url)
          downloadFiles(key, catalog, publication.title, files, OpdsAudiobook(files, AudiobookPackaging.Files).size, partial)
        }
        AudiobookPackaging.Zip -> {
          val zip = File(partial, ".download.zip")
          downloadFiles(key, catalog, publication.title, audiobook.files, audiobook.size, partial, target = zip)
          extractAudio(zip, partial)
          zip.delete()
        }
      }
      val folder = uniqueFolder(root, publication.title)
      if (!partial.renameTo(folder)) throw IOException("Could not move the download")
      add(
        DownloadedAudiobook(
          catalogId = key.catalogId,
          entryId = key.entryId,
          folder = folder.name,
          title = publication.title.takeIf { it.isNotBlank() },
          author = publication.authors.joinToString(", ").ifEmpty { null },
        ),
      )
      mediaScanTrigger.scan(restartIfScanning = true)
      return folder
    } finally {
      if (partial.exists()) partial.deleteRecursively()
    }
  }

  private suspend fun downloadFiles(
    key: OpdsEntryKey,
    catalog: OpdsCatalog,
    title: String,
    files: List<OpdsAcquisition>,
    knownSize: Long?,
    folder: File,
    target: File? = null,
  ) {
    var finished = 0L
    files.forEachIndexed { index, file ->
      val destination = target ?: File(folder, audioFileName(index, files.size, file, title))
      http.client.fetch(downloadRequest(catalog, file.url), destination, onCall = { calls[key] = it }) { read, total ->
        val downloaded = finished + read
        val size = knownSize ?: total.takeIf { files.size == 1 && it > 0 }
        val progress = when {
          size != null -> downloaded.toFloat() / size
          total > 0 -> (index + read.toFloat() / total) / files.size
          files.size > 1 -> index.toFloat() / files.size
          else -> null
        }
        report(key, OpdsDownloadState.Downloading(progress?.coerceIn(0F, 1F), downloaded, size ?: 0L))
      }
      finished += destination.length()
    }
  }

  private fun report(
    key: OpdsEntryKey,
    state: OpdsDownloadState.Downloading,
  ) {
    downloads.update { downloads ->
      val download = downloads[key] ?: return@update downloads
      downloads + (key to download.copy(state = state))
    }
  }

  private fun manifestFiles(
    key: OpdsEntryKey,
    catalog: OpdsCatalog,
    url: String,
  ): List<OpdsAcquisition> {
    val call = http.client.newCall(downloadRequest(catalog, url))
    calls[key] = call
    val files = call.execute().use { response ->
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
      try {
        parseAudiobookManifest(response.body.string(), response.request.url.toString())
      } catch (e: OpdsParseException) {
        throw IOException("Not an audiobook manifest", e)
      }
    }
    return files.ifEmpty { throw IOException("The manifest lists no audio") }
  }

  // the scanner orders the files by name, so the numbers keep the order of the catalog or the zip
  private suspend fun extractAudio(
    zip: File,
    folder: File,
  ) {
    ZipFile(zip).use { archive ->
      val entries = archive.entries().toList()
        .filter { entry ->
          val name = entry.name.substringAfterLast('/')
          !entry.isDirectory && !name.startsWith(".") && !entry.name.startsWith("__MACOSX") && isAudioFileName(name)
        }
        .sortedWith(compareBy(NaturalOrderComparator.stringComparator) { it.name })
      if (entries.isEmpty()) throw IOException("No audio in the zip")
      entries.forEachIndexed { index, entry ->
        currentCoroutineContext().ensureActive()
        val name = safeFileName(entry.name.substringAfterLast('/'))
        val target = File(folder, if (entries.size == 1) name else "${number(index)} $name")
        archive.getInputStream(entry).use { input ->
          target.outputStream().use { output ->
            val _ = input.copyTo(output)
          }
        }
      }
    }
  }

  private fun downloadCover(
    catalog: OpdsCatalog,
    url: String,
    target: File,
  ): Boolean {
    return try {
      http.client.newCall(downloadRequest(catalog, url)).execute().use { response ->
        if (!response.isSuccessful) return false
        target.outputStream().use { output ->
          val _ = response.body.byteStream().copyTo(output)
        }
        true
      }
    } catch (e: IOException) {
      Logger.w(e, "Could not download a cover")
      target.delete()
      false
    }
  }

  private suspend fun add(audiobook: DownloadedAudiobook) {
    var replaced: List<DownloadedAudiobook> = emptyList()
    store.updateData { entries ->
      replaced = entries.filter { it.catalogId == audiobook.catalogId && it.entryId == audiobook.entryId }
      entries - replaced.toSet() + audiobook
    }
    replaced.forEach { old ->
      File(downloadedAudiobooksFolder(context), old.folder).takeIf { old.folder != audiobook.folder }?.deleteRecursively()
    }
  }

  private fun bookId(entry: DownloadedAudiobook): BookId = BookId(Uri.fromFile(File(downloadedAudiobooksFolder(context), entry.folder)))
}

private const val COVER_FILE = "cover.jpg"
private val NOTIFICATION_INTERVAL = 500.milliseconds

/** Tells the result notifications of the books apart. */
private val OpdsEntryKey.tag: String get() = "opds:$catalogId:$entryId"

private fun BookContent.withCatalogMetadata(entry: DownloadedAudiobook): BookContent {
  return withMetadata(
    metadata.copy(
      name = entry.title ?: metadata.name,
      author = entry.author ?: metadata.author,
    ),
  )
}

private fun number(index: Int): String = (index + 1).toString().padStart(3, '0')

private fun audioFileName(
  index: Int,
  count: Int,
  file: OpdsAcquisition,
  title: String,
): String {
  val extension = file.audioExtension
  if (count == 1) return "${safeFileName(title)}.$extension"
  val name = (file.title ?: file.url.substringBefore('?').substringAfterLast('/'))
    .let { if (it.endsWith(".$extension", ignoreCase = true)) it.dropLast(extension.length + 1) else it }
  return "${number(index)} ${safeFileName(name)}.$extension"
}

private fun safeFileName(name: String): String {
  return name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
    .trim()
    .trimStart('.')
    .take(80)
    .trim()
    .ifEmpty { "Audiobook" }
}

private fun uniqueFolder(
  root: File,
  title: String,
): File {
  val name = safeFileName(title)
  var folder = File(root, name)
  var number = 2
  while (folder.exists()) {
    folder = File(root, "$name ($number)")
    number++
  }
  return folder
}
