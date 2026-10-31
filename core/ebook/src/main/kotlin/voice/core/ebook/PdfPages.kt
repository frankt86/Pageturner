package voice.core.ebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import java.io.Closeable
import java.io.IOException

/**
 * The pages of an open PDF. Android's PdfRenderer opens most PDFs. It's a reduced PDFium, so the ones it refuses go
 * to a full PDFium, and whoever shows the pages doesn't need to know which one opened them.
 * Use it from one thread at a time.
 */
interface PdfPages : Closeable {

  val pageCount: Int

  /** The width of the page divided by its height. */
  fun aspectRatio(index: Int): Float

  /** The page as large as fits into [maxWidth] by [maxHeight], on white paper. */
  fun render(
    index: Int,
    maxWidth: Int,
    maxHeight: Int,
  ): Bitmap

  /** The text of the page, empty where it can't be read. */
  fun text(index: Int): String
}

/**
 * Opens the PDF at [uri], unlocked with [password] if it has one.
 * @throws PdfOpenException with the reason when no engine can open it.
 */
fun openPdf(
  context: Context,
  uri: Uri,
  password: String? = null,
): PdfPages {
  // PdfRenderer only takes a password from Android 15 on
  val rendererFailure = if (password == null || Build.VERSION.SDK_INT >= 35) {
    val file = openFile(context, uri)
    try {
      return AndroidPdfPages(file, password)
    } catch (e: Exception) {
      file.close()
      e
    }
  } else {
    null
  }
  val file = openFile(context, uri)
  try {
    return PdfiumPages(PdfiumCore(context).newDocument(file, password))
  } catch (e: Exception) {
    file.close()
    throw PdfOpenException(pdfProblem(rendererFailure, e, withPassword = password != null), e)
  }
}

/** Why a PDF couldn't be opened. */
class PdfOpenException(
  val problem: PdfProblem,
  cause: Throwable,
) : IOException("The PDF couldn't be opened: $problem", cause)

private fun openFile(
  context: Context,
  uri: Uri,
): ParcelFileDescriptor {
  return try {
    openSeekablePdf(context, uri)
  } catch (e: Exception) {
    throw PdfOpenException(fileProblem(e), e)
  }
}

private class AndroidPdfPages(
  private val file: ParcelFileDescriptor,
  password: String?,
) : PdfPages {

  private val renderer = if (password != null && Build.VERSION.SDK_INT >= 35) {
    PdfRenderer(file, LoadParams.Builder().setPassword(password).build())
  } else {
    PdfRenderer(file)
  }

  override val pageCount: Int = renderer.pageCount

  override fun aspectRatio(index: Int): Float = renderer.openPage(index).use { page ->
    page.width.toFloat() / page.height.coerceAtLeast(1)
  }

  override fun render(
    index: Int,
    maxWidth: Int,
    maxHeight: Int,
  ): Bitmap = renderer.openPage(index).use { page ->
    pageBitmap(page.width, page.height, maxWidth, maxHeight).also { bitmap ->
      page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    }
  }

  override fun text(index: Int): String {
    if (Build.VERSION.SDK_INT < 35) return ""
    return renderer.openPage(index).use { page ->
      page.textContents.joinToString("\n") { it.text }
    }
  }

  override fun close() {
    renderer.close()
    file.close()
  }
}

private class PdfiumPages(private val document: PdfDocument) : PdfPages {

  override val pageCount: Int = document.getPageCount()

  // PDFium tells the sizes of all pages without opening them
  private val aspectRatios by lazy {
    document.getPageSizes(POINTS_PER_INCH).map { size -> size.width.toFloat() / size.height.coerceAtLeast(1) }
  }

  override fun aspectRatio(index: Int): Float = aspectRatios.getOrElse(index) { 1F }

  override fun render(
    index: Int,
    maxWidth: Int,
    maxHeight: Int,
  ): Bitmap {
    val page = document.openPage(index) ?: throw IOException("Page $index of the PDF couldn't be loaded")
    return page.use {
      pageBitmap(page.getPageWidthPoint(), page.getPageHeightPoint(), maxWidth, maxHeight).also { bitmap ->
        page.renderPageBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, renderAnnot = true)
      }
    }
  }

  override fun text(index: Int): String {
    val page = document.openPage(index) ?: return ""
    return page.use {
      page.openTextPage().use { textPage ->
        val count = textPage.textPageCountChars()
        if (count <= 0) "" else pdfiumText(textPage.textPageGetText(0, count).orEmpty())
      }
    }
  }

  override fun close() {
    // closes the file as well
    document.close()
  }
}

/** A white bitmap of the largest size of a page of [width] by [height] that fits into [maxWidth] by [maxHeight]. */
private fun pageBitmap(
  width: Int,
  height: Int,
  maxWidth: Int,
  maxHeight: Int,
): Bitmap {
  val scale = minOf(maxWidth.toFloat() / width.coerceAtLeast(1), maxHeight.toFloat() / height.coerceAtLeast(1))
  return createBitmap(
    (width * scale).toInt().coerceAtLeast(1),
    (height * scale).toInt().coerceAtLeast(1),
  ).apply {
    // PDFs expect paper underneath, a transparent page would show the dark theme through
    eraseColor(Color.WHITE)
  }
}

private const val POINTS_PER_INCH = 72
