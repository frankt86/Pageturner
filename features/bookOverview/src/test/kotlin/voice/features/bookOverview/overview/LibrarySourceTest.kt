package voice.features.bookOverview.overview

import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import voice.features.bookOverview.ebook

@RunWith(AndroidJUnit4::class)
class LibrarySourceTest {

  private val authority = "com.android.externalstorage.documents"
  private val books = DocumentsContract.buildTreeDocumentUri(authority, "primary:Books")
  private val kids = DocumentsContract.buildTreeDocumentUri(authority, "primary:Kids")

  @Test
  fun `an ebook belongs to the folder it was found in`() {
    val ebook =
      ebook()
        .copy(uri = DocumentsContract.buildDocumentUriUsingTree(kids, "primary:Kids/Emma.epub"))

    assertEquals(
      expected = LibrarySource.Folder(kids.toString()),
      actual = ebook.source(listOf(books, kids)),
    )
  }

  @Test
  fun `an ebook from a catalog in the app's storage belongs to no folder`() {
    val ebook = ebook().copy(uri = Uri.fromFile(File("/data/opds/Emma.epub")))

    assertNull(ebook.source(listOf(books, kids)))
  }
}
