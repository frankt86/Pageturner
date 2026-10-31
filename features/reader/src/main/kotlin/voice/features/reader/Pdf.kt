package voice.features.reader

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import voice.core.ebook.PdfPages

// the engines can only have one page open at a time
internal class PdfFile(private val pages: PdfPages) {

  private val mutex = Mutex()
  private var closed = false

  val pageCount: Int = pages.pageCount

  private val aspectRatios = arrayOfNulls<Float>(pageCount)

  /** The shape of the last page measured, which most pages of a PDF share, for a page not measured yet. */
  var typicalAspectRatio = 1F
    private set

  suspend fun render(
    index: Int,
    maxWidth: Int,
    maxHeight: Int,
  ): Bitmap? = withContext(Dispatchers.Default) {
    mutex.withLock {
      if (closed) null else pages.render(index, maxWidth, maxHeight)
    }
  }

  fun knownAspectRatio(index: Int): Float = aspectRatios.getOrNull(index) ?: typicalAspectRatio

  /** The width of the page divided by its height. Opening every page at once takes long in a big PDF. */
  suspend fun aspectRatio(index: Int): Float = aspectRatios[index] ?: withContext(Dispatchers.Default) {
    mutex.withLock {
      if (closed) {
        typicalAspectRatio
      } else {
        pages.aspectRatio(index).also { ratio ->
          aspectRatios[index] = ratio
          typicalAspectRatio = ratio
        }
      }
    }
  }

  suspend fun text(index: Int): String = withContext(Dispatchers.Default) {
    mutex.withLock {
      if (closed) "" else pages.text(index)
    }
  }

  suspend fun close() {
    mutex.withLock {
      if (closed) return
      closed = true
      pages.close()
    }
  }
}
