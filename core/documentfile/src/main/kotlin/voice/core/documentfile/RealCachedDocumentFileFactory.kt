package voice.core.documentfile

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding

@ContributesBinding(AppScope::class)
class RealCachedDocumentFileFactory(private val context: Context) : CachedDocumentFileFactory {
  override fun create(uri: Uri): CachedDocumentFile {
    // books downloaded into the app's own storage are plain files, which the document provider doesn't know
    if (uri.scheme == ContentResolver.SCHEME_FILE) return FileBasedDocumentFile(uri.toFile())
    return RealCachedDocumentFile(context = context, uri = uri, preFilledContent = null)
  }
}
