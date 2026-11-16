package voice.features.bookOverview.deleteBook

import android.app.Application
import android.widget.Toast
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.data.BookId
import voice.core.data.isRemote
import voice.core.data.store.EbooksStore
import voice.core.ebook.bookTextFile
import voice.core.ebook.bookTextFolder
import voice.core.ebook.mobi.deleteKindleEpub
import voice.core.logging.api.Logger
import voice.core.scanner.MediaScanTrigger
import voice.core.strings.R as StringsR
import voice.features.bookOverview.overview.bookId
import voice.navigation.Destination
import voice.navigation.Navigator

@Inject
class BookDeleter(
  private val application: Application,
  private val documentDeleter: DocumentDeleter,
  private val ebooksStore: EbooksStore,
  private val mediaScanTrigger: MediaScanTrigger,
  private val navigator: Navigator,
  private val dispatcherProvider: DispatcherProvider,
) {

  /** Deletes the files of the books, and tells the listener about the ones that stay. */
  internal suspend fun delete(ids: Collection<BookId>) {
    // Pageturner never deletes files on a server
    val local = ids.filterNot { it.isRemote }
    if (local.isEmpty()) return
    val ebooks = ebooksStore.data.first().associateBy { it.bookId }
    var failed = 0
    local.forEach { id ->
      val ebook = ebooks[id]
      val uri = ebook?.uri ?: id.toUri()
      // a provider can take seconds to delete a folder
      val deleted = withContext(dispatcherProvider.io) { documentDeleter.delete(uri) }
      if (!deleted) {
        failed++
        return@forEach
      }
      if (ebook == null) {
        // the player of a deleted book would be left without a book
        navigator.remove(Destination.Playback(id))
      } else {
        withContext(dispatcherProvider.io) {
          deleteKindleEpub(application, uri)
          bookTextFile(bookTextFolder(application), uri.toString()).delete()
        }
        // a book that's only missing keeps its place in the store, a deleted one leaves it
        ebooksStore.updateData { all -> all.filterNot { it.uri == uri } }
        navigator.remove(Destination.Reader(uri))
      }
    }
    mediaScanTrigger.scan(restartIfScanning = true)
    if (failed > 0) {
      Logger.w("Could not delete $failed of ${local.size} books")
      withContext(dispatcherProvider.main) {
        Toast.makeText(
            application,
            application.resources.getQuantityString(
              StringsR.plurals.book_delete_failed,
              failed,
              failed,
            ),
            Toast.LENGTH_LONG,
          )
          .show()
      }
    }
  }
}
