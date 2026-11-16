package voice.core.ebook

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Where the old engine keeps the text it took from an EPUB, so the book opens faster. */
fun bookTextFolder(context: Context): File = File(context.cacheDir, "book_text")

fun bookTextFile(
  folder: File,
  uri: String,
): File {
  val hash =
    MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") {
      "%02x".format(it)
    }
  return File(folder, hash)
}
