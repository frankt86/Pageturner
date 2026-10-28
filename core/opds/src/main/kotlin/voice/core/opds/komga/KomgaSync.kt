package voice.core.opds.komga

import android.net.Uri
import android.os.SystemClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.store.EbooksStore
import voice.core.logging.api.Logger
import voice.core.opds.CatalogType
import voice.core.opds.OpdsCatalog
import voice.core.opds.OpdsCatalogStore
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val PUSH_DELAY = 5.seconds
private val REFRESH_INTERVAL = 15.minutes

/**
 * Keeps the place in books from Komga in step with the server. Every call may fail without a connection: the place
 * here stays, and what couldn't be sent goes out with the next change or the next refresh.
 */
@SingleIn(AppScope::class)
@Inject
public class KomgaSync(
  private val api: KomgaApi,
  private val catalogStore: OpdsCatalogStore,
  private val ebooksStore: EbooksStore,
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mutex = Mutex()
  private val positions = ConcurrentHashMap<String, List<JsonObject>>()
  private var refreshedAt: Long? = null

  internal fun start() {
    scope.launch {
      combine(ebooksStore.data, catalogStore.data) { ebooks, catalogs ->
        ebooks.filter { it.target(catalogs) != null && it.unsent }.map { it.uri to it.positionModified }
      }
        .distinctUntilChanged()
        .collectLatest { unsent ->
          if (unsent.isEmpty()) return@collectLatest
          // a page turn after another sends only the last place
          delay(PUSH_DELAY)
          flush()
        }
    }
  }

  /** Sends what wasn't sent yet right away, for when the reader leaves the book. */
  public fun flush() {
    scope.launch { pushAll() }
  }

  /** Takes the progress from the server for the library, at most every few minutes. */
  internal fun refresh() {
    val now = SystemClock.elapsedRealtime()
    val last = refreshedAt
    if (last != null && now - last < REFRESH_INTERVAL.inWholeMilliseconds) return
    refreshedAt = now
    scope.launch {
      pushAll()
      pullAll()
    }
  }

  /** The place on the server when it's newer than the one here, or null. */
  public suspend fun newerPlace(ebook: Ebook): RemotePlace? {
    val (catalog, bookId) = ebook.target(catalogStore.data.first()) ?: return null
    return try {
      val book = api.bookProgress(catalog, bookId) ?: return null
      if (syncAction(ebook.positionModified, ebook.syncedPosition, book.modified) != SyncAction.Pull) return null
      val locator = if (ebook.format == EbookFormat.Epub && !book.completed) api.progressionLocator(catalog, bookId) else null
      remotePlace(ebook.format, book, locator)
    } catch (_: IOException) {
      null
    } catch (e: IllegalArgumentException) {
      Logger.w(e, "Could not read the progress from Komga")
      null
    }
  }

  private suspend fun pushAll() = mutex.withLock {
    val catalogs = catalogStore.data.first()
    ebooksStore.data.first().forEach { ebook ->
      val (catalog, bookId) = ebook.target(catalogs) ?: return@forEach
      if (ebook.unsent) push(ebook, catalog, bookId)
    }
  }

  private suspend fun push(
    ebook: Ebook,
    catalog: OpdsCatalog,
    bookId: String,
  ) {
    val update = komgaUpdate(
      format = ebook.format,
      progress = ebook.progress,
      section = ebook.position.section,
      lastOpened = ebook.lastOpened,
      readiumLocator = ebook.readiumLocator,
    )
    val code = try {
      when (update) {
        KomgaUpdate.Unread -> api.markUnread(catalog, bookId)
        KomgaUpdate.Read -> api.markRead(catalog, bookId)
        is KomgaUpdate.Page -> api.markPage(catalog, bookId, update.page)
        is KomgaUpdate.Progression -> {
          val serverPositions = positions[bookId] ?: api.positions(catalog, bookId).also { positions[bookId] = it }
          val locator = positionAt(serverPositions, update) ?: return
          api.putProgression(catalog, bookId, ebook.positionModified, locator)
        }
      }
    } catch (_: IOException) {
      return
    } catch (e: IllegalArgumentException) {
      Logger.w(e, "Could not read the positions from Komga")
      return
    }
    // the server has a newer place, or can't take this one, so sending it again wouldn't help
    val done = code in 200..299 || code == 400 || code == 404 || code == 409
    if (done) {
      markSent(ebook.uri, ebook.positionModified)
    } else {
      Logger.w("Could not send the progress to Komga: HTTP $code")
    }
  }

  private suspend fun pullAll() {
    val catalogs = catalogStore.data.first()
    ebooksStore.data.first().forEach { ebook ->
      val (catalog, bookId) = ebook.target(catalogs) ?: return@forEach
      val book = try {
        api.bookProgress(catalog, bookId)
      } catch (_: IOException) {
        // the server is out of reach, the other books would only wait for it too
        return
      } catch (e: IllegalArgumentException) {
        Logger.w(e, "Could not read the progress from Komga")
        null
      } ?: return@forEach
      if (syncAction(ebook.positionModified, ebook.syncedPosition, book.modified) != SyncAction.Pull) return@forEach
      val place = remotePlace(ebook.format, book, locator = null)
      if (place.isSamePlaceAs(ebook.progress)) return@forEach
      // the place itself is taken when the book opens, where its pages are known
      ebooksStore.updateData { ebooks ->
        ebooks.map {
          if (it.uri == ebook.uri && it.positionModified == ebook.positionModified) {
            it.copy(progress = place.totalProgression.toFloat(), lastOpened = maxOf(it.lastOpened, place.modified))
          } else {
            it
          }
        }
      }
    }
  }

  private suspend fun markSent(
    uri: Uri,
    positionModified: Long,
  ) {
    ebooksStore.updateData { ebooks ->
      ebooks.map { ebook ->
        val source = ebook.opdsSource
        if (ebook.uri == uri && source != null && source.syncedPosition < positionModified) {
          ebook.copy(opdsSource = source.copy(syncedPosition = positionModified))
        } else {
          ebook
        }
      }
    }
  }
}

private val Ebook.syncedPosition: Long get() = opdsSource?.syncedPosition ?: 0

private val Ebook.unsent: Boolean get() = positionModified > syncedPosition

private fun Ebook.target(catalogs: List<OpdsCatalog>): Pair<OpdsCatalog, String>? {
  val source = opdsSource ?: return null
  if (hidden || !available) return null
  val catalog = catalogs.firstOrNull { it.id == source.catalogId && it.type == CatalogType.Komga && it.syncProgress }
    ?: return null
  val bookId = source.komgaBookId ?: komgaBookId(source.acquisitionUrl, source.entryId) ?: return null
  return catalog to bookId
}
