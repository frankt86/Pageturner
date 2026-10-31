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

  suspend fun render(
    index: Int,
    maxWidth: Int,
    maxHeight: Int,
  ): Bitmap? = withContext(Dispatchers.Default) {
    mutex.withLock {
      if (closed) null else pages.render(index, maxWidth, maxHeight)
    }
  }

  /** The width of each page divided by its height. */
  suspend fun aspectRatios(): List<Float> = withContext(Dispatchers.Default) {
    mutex.withLock {
      if (closed) emptyList() else pages.aspectRatios()
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
