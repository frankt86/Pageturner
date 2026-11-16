package voice.features.bookOverview.deleteBook

import android.net.Uri

/** Files on the phone, of which only the ones in a folder Pageturner may change can be deleted. */
class FakeDocumentDeleter(
  files: Collection<Uri>,
  private val writable: (Uri) -> Boolean = { true },
) : DocumentDeleter {

  val files: MutableSet<Uri> = files.toMutableSet()

  override fun delete(uri: Uri): Boolean {
    return uri in files && writable(uri) && files.remove(uri)
  }
}
