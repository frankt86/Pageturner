package voice.core.ebook.mobi

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val FOLDER = "converted"
private val lock = Any()

/**
 * The Kindle book at [uri] as an EPUB, which both book engines open. It's converted once and kept in the app's files,
 * named after the uri and the size and date of the file, so a changed file is converted again.
 */
fun kindleEpub(
  context: Context,
  uri: Uri,
): File {
  val folder = File(context.filesDir, FOLDER).apply { mkdirs() }
  val key = hash(uri.toString())
  val file = folder.resolve("$key-${hash(fileVersion(context, uri))}.epub")
  synchronized(lock) {
    if (file.exists()) return file
    val book = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Can't read the book")
    val partial = folder.resolve("$key.partial")
    try {
      partial.outputStream().buffered().use { convertKindleToEpub(book, it) }
      folder.listFiles()?.filter { it.name.startsWith("$key-") }?.forEach { it.delete() }
      if (!partial.renameTo(file)) throw IOException("Can't keep the converted book")
    } finally {
      partial.delete()
    }
  }
  return file
}

/** Deletes the converted copies of the books that aren't in the library anymore. */
fun deleteStaleKindleEpubs(
  context: Context,
  keep: Collection<Uri>,
) {
  val keys = keep.mapTo(mutableSetOf()) { hash(it.toString()) }
  synchronized(lock) {
    File(context.filesDir, FOLDER).listFiles()
      ?.filter { it.name.substringBefore('-').substringBefore('.') !in keys }
      ?.forEach { it.delete() }
  }
}

private fun fileVersion(
  context: Context,
  uri: Uri,
): String {
  if (uri.scheme == ContentResolver.SCHEME_FILE) {
    val file = uri.path?.let(::File) ?: return ""
    return "${file.length()}:${file.lastModified()}"
  }
  return try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (!cursor.moveToFirst()) return@use ""
      fun column(name: String): String {
        val index = cursor.getColumnIndex(name)
        return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index).toString() else ""
      }
      column(OpenableColumns.SIZE) + ":" + column(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    } ?: ""
  } catch (_: RuntimeException) {
    // some providers can't answer, then the copy is kept until the book leaves the library
    ""
  }
}

private fun hash(text: String): String {
  return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).copyOf(12).toHexString()
}
