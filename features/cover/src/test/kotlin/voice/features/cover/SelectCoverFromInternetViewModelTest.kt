package voice.features.cover

import android.content.Context
import android.os.Looper
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import voice.core.common.DispatcherProvider
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.repo.BookRepository
import voice.core.data.store.EbooksStore
import voice.features.cover.SelectCoverFromInternetViewModel.Events
import voice.features.cover.SelectCoverFromInternetViewModel.Results
import voice.features.cover.api.CoverApi
import voice.features.cover.api.SearchResponse
import voice.navigation.Navigator
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class SelectCoverFromInternetViewModelTest {

  private val bookId = BookId("book")
  private val cover = SearchResponse.ImageResult(width = 600, height = 600, image = "image", thumbnail = "thumbnail")

  private val book = mockk<Book> {
    every { name } returns "Dune"
    every { author } returns null
  }
  private val bookRepository = mockk<BookRepository> {
    coEvery { get(bookId) } returns book
  }
  private val coverDownloader = mockk<CoverDownloader>()
  private val navigator = mockk<Navigator>(relaxed = true)
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val ebook = Ebook(
    uri = "content://books/document/book.epub".toUri(),
    name = "Dune",
    format = EbookFormat.Epub,
  )
  private val ebooksStore = EbooksStore(MemoryDataStore(listOf(ebook)))

  private fun viewModel(
    api: CoverApi,
    target: CoverTarget = CoverTarget.ForBook(bookId),
  ) = SelectCoverFromInternetViewModel(
    api = api,
    bookRepository = bookRepository,
    ebooksStore = ebooksStore,
    navigator = navigator,
    context = context,
    coverDownloader = coverDownloader,
    dispatcherProvider = DispatcherProvider(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined),
    target = target,
  )

  @Test
  fun `retry loads the covers after the search failed`() = runTest {
    val api = mockk<CoverApi> {
      coEvery { token(any()) } returnsMany listOf(null, "token")
      coEvery { search(any(), any(), any()) } returns SearchResponse(next = null, results = listOf(cover))
    }
    val viewModel = viewModel(api)
    val events = MutableSharedFlow<Events>(extraBufferCapacity = 1)
    val state = backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState(events)
    }
    settle()
    assertEquals(Results.Error, state.value.results)

    events.emit(Events.Retry)
    settle()

    val results = assertIs<Results.Content>(state.value.results)
    assertEquals(listOf(cover), results.items.itemSnapshotList.items)
  }

  @Test
  fun `a cover that can't be downloaded is reported`() = runTest {
    val api = mockk<CoverApi> {
      coEvery { token(any()) } returns "token"
      coEvery { search(any(), any(), any()) } returns SearchResponse(next = null, results = listOf(cover))
    }
    coEvery { coverDownloader.download(any()) } returns null
    val viewModel = viewModel(api)
    val events = MutableSharedFlow<Events>(extraBufferCapacity = 1)
    val state = backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState(events)
    }
    settle()

    events.emit(Events.CoverClick(cover))
    settle()

    assertEquals(null, state.value.downloading)
    assertEquals(true, state.value.downloadFailed)
  }

  @Test
  fun `a cover picked for an ebook replaces its old one`() = runTest {
    val api = mockk<CoverApi> {
      coEvery { token(any()) } returns "token"
      coEvery { search(any(), any(), any()) } returns SearchResponse(next = null, results = listOf(cover))
    }
    val coversDir = File(context.filesDir, "ebookCovers").apply { mkdirs() }
    val oldCover = coversDir.resolve("old").apply { writeText("old") }
    ebooksStore.updateData { ebooks -> ebooks.map { it.copy(cover = oldCover.absolutePath) } }
    val downloaded = File(context.cacheDir, "downloaded").apply { writeText("new") }
    coEvery { coverDownloader.download(any()) } returns downloaded
    val viewModel = viewModel(api, CoverTarget.ForEbook(ebook.uri))
    val events = MutableSharedFlow<Events>(extraBufferCapacity = 1)
    val state = backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState(events)
    }
    settle()

    events.emit(Events.CoverClick(cover))
    settle()

    val newCover = File(checkNotNull(ebooksStore.data.first().single().cover))
    assertEquals(coversDir, newCover.parentFile)
    assertEquals("new", newCover.readText())
    assertFalse(oldCover.exists())
    assertEquals(false, state.value.downloadFailed)
    verify { navigator.goBack() }
  }

  @Test
  fun `an ebook searches for its title and author`() = runTest {
    ebooksStore.updateData { ebooks -> ebooks.map { it.copy(author = "Frank Herbert") } }
    val api = mockk<CoverApi> {
      coEvery { token(any()) } returns "token"
      coEvery { search(any(), any(), any()) } returns SearchResponse(next = null, results = listOf(cover))
    }
    val viewModel = viewModel(api, CoverTarget.ForEbook(ebook.uri))
    backgroundScope.launchMolecule(RecompositionMode.Immediate) {
      viewModel.viewState(MutableSharedFlow())
    }
    settle()

    assertEquals("Dune Frank Herbert", viewModel.query.text.toString())
  }

  // paging hands its pages to the main looper, which only runs when it is idled.
  // runCurrent rather than advanceUntilIdle, which skips the background scope the presenter runs in.
  private fun TestScope.settle() {
    repeat(5) {
      shadowOf(Looper.getMainLooper()).idle()
      testScheduler.runCurrent()
    }
  }
}
