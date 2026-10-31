package voice.features.reader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toFile
import voice.core.logging.api.Logger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

// raise it when the text taken from a book changes, so the books are read anew
private const val BOOK_TEXT_VERSION = 1

// a novel's text is about a megabyte
private const val CACHED_BOOKS = 20

/** The old engine's text of an EPUB, taken from the file once and kept for the next time it opens. */
internal class BookText(
  val title: String?,
  val chapters: List<String>,
  val chapterTitles: List<String?>,
)

/** Changes when the file changes, or null when the file can't tell its size and when it changed. */
internal fun bookTextKey(
  uri: String,
  size: Long?,
  modified: Long?,
): String? {
  if (size == null || size <= 0 || modified == null || modified <= 0) return null
  return "$BOOK_TEXT_VERSION|$uri|$size|$modified"
}

internal fun bookTextFile(
  folder: File,
  uri: String,
): File {
  val hash = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
  return File(folder, hash)
}

internal fun writeBookText(
  file: File,
  key: String,
  text: BookText,
) {
  file.parentFile?.mkdirs()
  val temporary = File(file.path + ".tmp")
  DataOutputStream(temporary.outputStream().buffered()).use { out ->
    out.writeString(key)
    out.writeNullableString(text.title)
    out.writeInt(text.chapters.size)
    text.chapters.forEachIndexed { index, chapter ->
      out.writeNullableString(text.chapterTitles.getOrNull(index))
      out.writeString(chapter)
    }
  }
  if (!temporary.renameTo(file)) temporary.delete()
}

/** The text saved with [key], or null when there's none or the file changed since. */
internal fun readBookText(
  file: File,
  key: String,
): BookText? {
  if (!file.exists()) return null
  return try {
    DataInputStream(file.inputStream().buffered()).use { input ->
      if (input.readString() != key) return null
      val title = input.readNullableString()
      val count = input.readInt()
      val titles = ArrayList<String?>(count)
      val chapters = ArrayList<String>(count)
      repeat(count) {
        titles += input.readNullableString()
        chapters += input.readString()
      }
      BookText(title, chapters, titles)
    }
  } catch (_: IOException) {
    null
  }.also {
    // the most recently opened books stay
    if (it != null) file.setLastModified(System.currentTimeMillis())
  }
}

/** Removes all but the [keep] most recently used texts. */
internal fun pruneBookTexts(
  folder: File,
  keep: Int = CACHED_BOOKS,
) {
  val files = folder.listFiles()?.filter { it.isFile } ?: return
  files.sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
}

/** The key of the book at [uri] by its size and when it changed, or null when the file doesn't tell. */
internal fun bookTextKey(
  context: Context,
  uri: Uri,
): String? {
  return try {
    if (uri.scheme == "file") {
      val file = uri.toFile()
      bookTextKey(uri.toString(), file.length(), file.lastModified())
    } else {
      val columns = arrayOf(OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
      context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        fun long(name: String): Long? {
          val column = cursor.getColumnIndex(name)
          return if (column == -1 || cursor.isNull(column)) null else cursor.getLong(column)
        }
        bookTextKey(uri.toString(), long(OpenableColumns.SIZE), long(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
      }
    }
  } catch (e: IllegalArgumentException) {
    // not every app that shares a file tells when it changed
    Logger.w(e, "Could not tell when the book changed")
    null
  } catch (e: SecurityException) {
    Logger.w(e, "Could not tell when the book changed")
    null
  } catch (e: IOException) {
    Logger.w(e, "Could not tell when the book changed")
    null
  }
}

internal fun bookTextFolder(context: Context): File = File(context.cacheDir, "book_text")

// writeUTF only takes 64 KB, a chapter can be longer
private fun DataOutputStream.writeString(value: String) {
  val bytes = value.toByteArray()
  writeInt(bytes.size)
  write(bytes)
}

private fun DataOutputStream.writeNullableString(value: String?) {
  writeBoolean(value != null)
  if (value != null) writeString(value)
}

private fun DataInputStream.readString(): String {
  val size = readInt()
  if (size !in 0..available()) throw IOException("A broken text cache")
  val bytes = ByteArray(size)
  readFully(bytes)
  return bytes.decodeToString()
}

private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readString() else null
