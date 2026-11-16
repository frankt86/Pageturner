package voice.features.bookOverview.deleteBook

import android.app.Application
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import voice.core.common.DispatcherProvider
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.store.EbooksStore
import voice.core.ebook.bookTextFile
import voice.core.ebook.bookTextFolder
import voice.core.scanner.MediaScanTrigger
import voice.features.bookOverview.MemoryDataStore
import voice.features.bookOverview.overview.bookId
import voice.navigation.Destination
import voice.navigation.Navigator

@RunWith(AndroidJUnit4::class)
class BookDeleterTest {

  private val scope = TestScope()
  private val application = ApplicationProvider.getApplicationContext<Application>()
  private val audiobook = BookId("content://books/tree/root/document/root%2FPersuasion")
  private val epub =
    Ebook(
      uri = "content://books/tree/root/document/root%2FEmma.epub".toUri(),
      name = "Emma",
      format = EbookFormat.Epub,
    )
  private val kindle =
    Ebook(
      uri = "content://books/tree/root/document/root%2FSanditon.azw3".toUri(),
      name = "Sanditon",
      format = EbookFormat.Azw3,
    )
  private val locked = "content://books/tree/locked/document/locked%2FLady%20Susan.epub".toUri()
  private val lockedEbook = Ebook(uri = locked, name = "Lady Susan", format = EbookFormat.Epub)
  private val ebooksStore = EbooksStore(MemoryDataStore(listOf(epub, kindle, lockedEbook)))
  private val files =
    FakeDocumentDeleter(
      files = listOf(audiobook.toUri(), epub.uri, kindle.uri, locked),
      writable = { it != locked },
    )
  private val navigator = mockk<Navigator>(relaxUnitFun = true)
  private val mediaScanTrigger = mockk<MediaScanTrigger>(relaxUnitFun = true)
  private val deleter =
    BookDeleter(
      application = application,
      documentDeleter = files,
      ebooksStore = ebooksStore,
      mediaScanTrigger = mediaScanTrigger,
      navigator = navigator,
      dispatcherProvider =
        DispatcherProvider(scope.coroutineContext, scope.coroutineContext, scope.coroutineContext),
    )

  private suspend fun storedUris() = ebooksStore.data.first().map { it.uri }

  @Test
  fun `an audiobook leaves the phone, its player closes and the library scans again`() =
    scope.runTest {
      deleter.delete(listOf(audiobook))

      assertFalse(audiobook.toUri() in files.files)
      verify { navigator.remove(Destination.Playback(audiobook)) }
      verify { mediaScanTrigger.scan(restartIfScanning = true) }
      assertNull(ShadowToast.getTextOfLatestToast())
    }

  @Test
  fun `an ebook leaves the phone and the library, with its text and its reader`() = scope.runTest {
    val text = bookTextFile(bookTextFolder(application), epub.uri.toString())
    text.apply { parentFile!!.mkdirs() }.writeText("text")
    val otherText = bookTextFile(bookTextFolder(application), kindle.uri.toString())
    otherText.writeText("text")

    deleter.delete(listOf(epub.bookId))

    assertFalse(epub.uri in files.files)
    assertEquals(expected = listOf(kindle.uri, locked), actual = storedUris())
    assertFalse(text.exists())
    assertTrue(otherText.exists())
    verify { navigator.remove(Destination.Reader(epub.uri)) }
    verify { mediaScanTrigger.scan(restartIfScanning = true) }
  }

  @Test
  fun `a book in a folder Pageturner may not change stays, and the listener learns about it`() =
    scope.runTest {
      deleter.delete(listOf(lockedEbook.bookId, epub.bookId))

      assertTrue(locked in files.files)
      assertEquals(expected = listOf(kindle.uri, locked), actual = storedUris())
      verify(exactly = 0) { navigator.remove(Destination.Reader(locked)) }
      assertEquals(expected = "Couldn't delete 1 book", actual = ShadowToast.getTextOfLatestToast())
    }
}
