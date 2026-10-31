package voice.core.ebook

import io.legere.pdfiumandroid.api.PdfPasswordException
import java.io.FileNotFoundException
import java.io.IOException

/** Why a PDF couldn't be opened. */
enum class PdfProblem {
  /** It's locked, and no password was given. */
  Password,

  /** It's locked, and the password given doesn't open it. */
  WrongPassword,

  /** The file is broken, or not a PDF at all. */
  Damaged,

  /** It uses an encryption or a feature no engine here can read. */
  Unsupported,

  /** The file is gone, or the app can't reach it anymore. */
  Missing,

  Unknown,
}

/** The problem with a file that couldn't even be opened, before any engine looked at it. */
internal fun fileProblem(error: Exception): PdfProblem = when (error) {
  // the file is gone, or the access to its folder was taken away
  is FileNotFoundException, is SecurityException -> PdfProblem.Missing
  else -> PdfProblem.Unknown
}

/**
 * The problem with a PDF both engines refused: Android's PdfRenderer with [renderer], which is null when it wasn't
 * asked, and PDFium with [pdfium]. PDFium knows more, so its answer counts first.
 */
internal fun pdfProblem(
  renderer: Exception?,
  pdfium: Exception,
  withPassword: Boolean,
): PdfProblem {
  val locked = if (withPassword) PdfProblem.WrongPassword else PdfProblem.Password
  return when {
    pdfium is PdfPasswordException -> locked
    pdfium is IOException -> pdfiumProblem(pdfium.message) ?: rendererProblem(renderer, locked)
    // PDFium couldn't start at all, so PdfRenderer's answer is all there is
    else -> rendererProblem(renderer, locked)
  }
}

// the messages PDFium's error codes come with
private fun pdfiumProblem(message: String?): PdfProblem? {
  val text = message.orEmpty()
  return when {
    "not in PDF format or corrupted" in text || "File is empty" in text || "content error" in text -> PdfProblem.Damaged
    "Unsupported security scheme" in text -> PdfProblem.Unsupported
    "not found or could not be opened" in text || "Cannot read from file" in text -> PdfProblem.Missing
    else -> null
  }
}

private fun rendererProblem(
  renderer: Exception?,
  locked: PdfProblem,
): PdfProblem = when (renderer) {
  // PdfRenderer says this for a password as well as for an encryption it doesn't know
  is SecurityException -> locked
  is IOException -> PdfProblem.Damaged
  else -> PdfProblem.Unknown
}

/** PDFium ends lines with \r\n and may leave a null character at the end. */
internal fun pdfiumText(text: String): String = text.replace("\r\n", "\n").replace("\r", "\n").trimEnd('\u0000')
