package voice.features.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// PdfRenderer can only have one page open at a time
internal class PdfFile(private val fileDescriptor: ParcelFileDescriptor) {

  private val renderer = PdfRenderer(fileDescriptor)
  private val mutex = Mutex()
  private var closed = false

  val pageCount: Int = renderer.pageCount

  suspend fun render(
    index: Int,
    maxWidth: Int,
    maxHeight: Int,
  ): Bitmap? = withContext(Dispatchers.Default) {
    mutex.withLock {
      if (closed) return@withContext null
      renderer.openPage(index).use { page ->
        val scale = minOf(maxWidth.toFloat() / page.width, maxHeight.toFloat() / page.height)
        val width = (page.width * scale).toInt().coerceAtLeast(1)
        val height = (page.height * scale).toInt().coerceAtLeast(1)
        createBitmap(width, height).also { bitmap ->
          // PDFs expect paper underneath, a transparent page would show the dark theme through
          bitmap.eraseColor(Color.WHITE)
          page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
      }
    }
  }

  /** The width of each page divided by its height. */
  suspend fun aspectRatios(): List<Float> = withContext(Dispatchers.Default) {
    mutex.withLock {
      if (closed) {
        emptyList()
      } else {
        List(pageCount) { index ->
          renderer.openPage(index).use { page -> page.width.toFloat() / page.height.coerceAtLeast(1) }
        }
      }
    }
  }

  suspend fun text(index: Int): String {
    if (Build.VERSION.SDK_INT < 35) return ""
    return withContext(Dispatchers.Default) {
      mutex.withLock {
        if (closed) {
          ""
        } else {
          renderer.openPage(index).use { page ->
            page.textContents.joinToString("\n") { it.text }
          }
        }
      }
    }
  }

  suspend fun close() {
    mutex.withLock {
      if (closed) return
      closed = true
      renderer.close()
      fileDescriptor.close()
    }
  }
}
