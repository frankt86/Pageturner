package voice.core.ebook

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/** The first page of the PDF at [uri], [width] pixels wide, to show as its cover. */
fun renderPdfCover(
  context: Context,
  uri: Uri,
  width: Int,
): Bitmap? {
  openPdf(context, uri).use { pdf ->
    if (pdf.pageCount == 0) return null
    return pdf.render(0, maxWidth = width, maxHeight = Int.MAX_VALUE)
  }
}
