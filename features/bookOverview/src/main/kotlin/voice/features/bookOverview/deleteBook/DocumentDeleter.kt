package voice.features.bookOverview.deleteBook

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding

fun interface DocumentDeleter {
  /** Deletes the file or folder at [uri], and tells whether it's gone. */
  fun delete(uri: Uri): Boolean
}

@ContributesBinding(AppScope::class)
class DocumentFileDeleter(private val application: Application) : DocumentDeleter {

  override fun delete(uri: Uri): Boolean {
    // a book downloaded from a catalog into the app's own storage is a plain file or folder
    val file =
      if (uri.scheme == ContentResolver.SCHEME_FILE) {
        DocumentFile.fromFile(uri.toFile())
      } else {
        DocumentFile.fromSingleUri(application, uri)
      }
    return file?.delete() == true
  }
}
