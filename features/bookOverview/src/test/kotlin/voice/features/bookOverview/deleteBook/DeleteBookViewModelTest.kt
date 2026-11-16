package voice.features.bookOverview.deleteBook

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore
import voice.features.bookOverview.MemoryDataStore
import voice.features.bookOverview.book
import voice.features.bookOverview.bottomSheet.BottomSheetItem
import voice.features.bookOverview.chapter
import voice.navigation.Destination
import voice.navigation.Navigator

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DeleteBookViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val book =
    book(
      chapters = listOf(chapter(), chapter(), chapter()),
      id = BookId("content://$AUTHORITY/document/book"),
    )
  private val navigator = mockk<Navigator>(relaxUnitFun = true)

  private val dispatcherProvider =
    DispatcherProvider(
      main = dispatcher,
      io = dispatcher + onIoDispatcher.asContextElement(true),
      mainImmediate = dispatcher,
    )

  private val viewModel = viewModel(book)

  private fun viewModel(book: Book): DeleteBookViewModel {
    val application = ApplicationProvider.getApplicationContext<Application>()
    return DeleteBookViewModel(
      repo =
        mockk<BookRepository> {
          coEvery { get(book.id) } returns book
        },
      bookDeleter =
        BookDeleter(
          application = application,
          documentDeleter = DocumentFileDeleter(application),
          ebooksStore = EbooksStore(MemoryDataStore(emptyList())),
          mediaScanTrigger = mockk(relaxed = true),
          navigator = navigator,
          dispatcherProvider = dispatcherProvider,
        ),
      dispatcherProvider = dispatcherProvider,
    )
  }

  @Test
  fun `the sheet shows how many files get deleted`() =
    runTest(dispatcher) {
      viewModel.onItemClick(book.id, BottomSheetItem.DeleteBook)

      assertEquals(expected = 3, actual = viewModel.state.value?.fileCount)
    }

  @Test
  fun `deleting a book closes its player`() =
    runTest(dispatcher) {
      Robolectric.setupContentProvider(DocumentsProvider::class.java, AUTHORITY)

      delete()

      verify { navigator.remove(Destination.Playback(book.id)) }
    }

  @Test
  fun `a failed deletion keeps the player`() =
    runTest(dispatcher) {
      delete()

      verify(exactly = 0) { navigator.remove(any()) }
    }

  @Test
  fun `a book downloaded from a catalog is deleted with its folder`() =
    runTest(dispatcher) {
      val folder =
        File(
          ApplicationProvider.getApplicationContext<Application>().filesDir,
          "audiobooks/Persuasion",
        )
      File(folder, "Persuasion.m4b").apply { parentFile!!.mkdirs() }.writeText("audio")
      val downloaded = book(chapters = book.chapters, id = BookId(Uri.fromFile(folder)))

      delete(viewModel(downloaded), downloaded.id)

      assertFalse(folder.exists())
      verify { navigator.remove(Destination.Playback(downloaded.id)) }
    }

  private suspend fun TestScope.delete(
    viewModel: DeleteBookViewModel = this@DeleteBookViewModelTest.viewModel,
    bookId: BookId = book.id,
  ) {
    viewModel.onItemClick(bookId, BottomSheetItem.DeleteBook)
    viewModel.onDeleteCheckBoxCheck(true)
    viewModel.onConfirmDeletion()
    advanceUntilIdle()
  }

  /**
   * Answers calls made on the IO dispatcher, which is how a documents provider confirms a deletion.
   */
  class DocumentsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
      method: String,
      arg: String?,
      extras: Bundle?,
    ): Bundle {
      // a provider can take seconds to delete a folder, which blocks the main thread into an ANR
      check(onIoDispatcher.get() == true)
      return Bundle()
    }

    override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
      uri: Uri,
      values: ContentValues?,
    ): Uri? = null

    override fun delete(
      uri: Uri,
      selection: String?,
      selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?,
    ): Int = 0
  }
}

private const val AUTHORITY = "voice.test.documents"

private val onIoDispatcher = ThreadLocal.withInitial { false }
