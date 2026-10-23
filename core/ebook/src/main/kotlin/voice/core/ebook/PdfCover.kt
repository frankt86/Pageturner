package voice.core.ebook

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap

/** The first page of a PDF, [width] pixels wide, to show as its cover. */
fun renderPdfCover(
  fileDescriptor: ParcelFileDescriptor,
  width: Int,
): Bitmap? {
  PdfRenderer(fileDescriptor).use { renderer ->
    if (renderer.pageCount == 0) return null
    renderer.openPage(0).use { page ->
      val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
      return createBitmap(width, height).also { bitmap ->
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
      }
    }
  }
}
