package voice.core.ebook

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

/**
 * Opens a PDF for PdfRenderer, which needs a file it can jump around in. Some folders, like the ones of cloud drives,
 * only hand out a stream, and PdfRenderer refuses those. Such a PDF is copied into the cache and opened from there.
 */
fun openSeekablePdf(
  context: Context,
  uri: Uri,
): ParcelFileDescriptor {
  val resolver = context.contentResolver
  val direct = resolver.openFileDescriptor(uri, "r") ?: throw IOException("No file for $uri")
  if (direct.isSeekable()) return direct
  direct.close()
  val copy = File(context.cacheDir, "pdf").apply { mkdirs() }.resolve("${uri.toString().hashCode()}.pdf")
  val input = resolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
  input.use { stream ->
    copy.outputStream().use { output ->
      val _ = stream.copyTo(output)
    }
  }
  return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
}

private fun ParcelFileDescriptor.isSeekable(): Boolean {
  return try {
    val _ = Os.lseek(fileDescriptor, 0, OsConstants.SEEK_SET)
    true
  } catch (_: ErrnoException) {
    false
  }
}
