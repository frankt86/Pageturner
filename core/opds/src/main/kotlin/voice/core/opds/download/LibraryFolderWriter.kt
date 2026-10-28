package voice.core.opds.download

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import voice.core.logging.api.Logger
import java.io.File
import java.io.IOException
import kotlin.uuid.Uuid

private const val BINARY = "application/octet-stream"

/**
 * Copies finished downloads into a folder of the library through the system's document provider. A copy is made
 * under a name starting with a dot, which the scan skips, and then renamed, so the library never sees half a book.
 */
@Inject
internal class LibraryFolderWriter(private val context: Context) {

  private val resolver: ContentResolver get() = context.contentResolver

  /** The folder in [root] that a book goes to, created when it's missing. */
  fun bookParent(
    root: DownloadFolder,
    path: List<String>,
  ): Uri {
    var parent = DocumentsContract.buildDocumentUriUsingTree(root.uri, DocumentsContract.getTreeDocumentId(root.uri))
    path.forEach { name ->
      parent = children(parent).firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }?.uri
        ?: create(parent, Document.MIME_TYPE_DIR, name)
    }
    return parent
  }

  /** Copies [files] into a new folder named [name] in [parent]. @return the folder, as the scan finds it */
  suspend fun copyFolder(
    parent: Uri,
    name: String,
    files: List<File>,
  ): Uri {
    val finalName = uniqueName(name, extension = null, taken = children(parent).map { it.name })
    val hidden = create(parent, Document.MIME_TYPE_DIR, ".${Uuid.random()}")
    val renamed = try {
      files.forEach { file -> copy(file, create(hidden, BINARY, file.name)) }
      rename(hidden, finalName)
    } catch (e: Exception) {
      delete(hidden)
      throw e
    }
    if (renamed != null) return asFoundByScan(parent, renamed)
    // the provider can't rename, so the files go straight into the folder of the book
    delete(hidden)
    val folder = create(parent, Document.MIME_TYPE_DIR, finalName)
    files.forEach { file -> copy(file, create(folder, BINARY, file.name)) }
    return asFoundByScan(parent, folder)
  }

  /** Copies [file] into [parent] as [name] with [extension]. @return the file, as the scan finds it */
  suspend fun copyFile(
    parent: Uri,
    name: String,
    extension: String,
    file: File,
  ): Uri {
    val finalName = uniqueName(name, extension, taken = children(parent).map { it.name })
    val hidden = create(parent, BINARY, ".${Uuid.random()}")
    val renamed = try {
      copy(file, hidden)
      rename(hidden, finalName)
    } catch (e: Exception) {
      delete(hidden)
      throw e
    }
    if (renamed != null) return asFoundByScan(parent, renamed)
    delete(hidden)
    val target = create(parent, BINARY, finalName)
    copy(file, target)
    return asFoundByScan(parent, target)
  }

  /** Whether the file of a book is still there. */
  fun exists(uri: Uri): Boolean {
    if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.let(::File)?.exists() == true
    return try {
      resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.count > 0 } == true
    } catch (e: Exception) {
      Logger.w(e, "Could not check for a file")
      false
    }
  }

  private class Child(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
  )

  private fun children(parent: Uri): List<Child> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
    val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
    return saf {
      resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            val id = cursor.getString(0) ?: continue
            this += Child(
              uri = DocumentsContract.buildDocumentUriUsingTree(parent, id),
              name = cursor.getString(1).orEmpty(),
              isDirectory = cursor.getString(2) == Document.MIME_TYPE_DIR,
            )
          }
        }
      } ?: throw IOException("Could not list the folder")
    }
  }

  private fun create(
    parent: Uri,
    mimeType: String,
    name: String,
  ): Uri {
    return saf { DocumentsContract.createDocument(resolver, parent, mimeType, name) }
      ?: throw IOException("Could not create a file")
  }

  private suspend fun copy(
    file: File,
    target: Uri,
  ) {
    currentCoroutineContext().ensureActive()
    val _ = saf {
      val output = resolver.openOutputStream(target) ?: throw IOException("Could not write a file")
      output.use { file.inputStream().use { input -> input.copyTo(it) } }
    }
  }

  private fun rename(
    uri: Uri,
    name: String,
  ): Uri? {
    return try {
      DocumentsContract.renameDocument(resolver, uri, name)
    } catch (e: Exception) {
      Logger.w(e, "Could not rename a download")
      null
    }
  }

  private fun delete(uri: Uri) {
    try {
      DocumentsContract.deleteDocument(resolver, uri)
    } catch (_: Exception) {
      // already renamed, or gone
    }
  }
}

// the scan builds the uris of the files from the folder of the library, so a book keeps the id it gets there
private fun asFoundByScan(
  parent: Uri,
  document: Uri,
): Uri = DocumentsContract.buildDocumentUriUsingTree(parent, DocumentsContract.getDocumentId(document))

// a provider tells a lost permission or a full disk in other exceptions than IOException
private inline fun <T> saf(block: () -> T): T {
  return try {
    block()
  } catch (e: IOException) {
    throw e
  } catch (e: SecurityException) {
    throw IOException(e)
  } catch (e: IllegalArgumentException) {
    throw IOException(e)
  } catch (e: IllegalStateException) {
    throw IOException(e)
  } catch (e: UnsupportedOperationException) {
    throw IOException(e)
  }
}
