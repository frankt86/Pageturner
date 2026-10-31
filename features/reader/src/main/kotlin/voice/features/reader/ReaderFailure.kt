package voice.features.reader

import voice.core.ebook.PdfOpenException
import voice.core.ebook.PdfProblem
import voice.core.ebook.mobi.KindleDrmException
import java.io.FileNotFoundException

/** Why the reader couldn't show a book, for a message more helpful than that it couldn't. */
internal enum class ReaderFailure {
  Damaged,
  Unsupported,
  Missing,
  Unknown,

  /** A Kindle book encrypted with DRM, which Pageturner never removes. */
  Drm,
}

internal fun readerFailure(error: Exception): ReaderFailure = when (error) {
  is PdfOpenException -> when (error.problem) {
    PdfProblem.Damaged -> ReaderFailure.Damaged
    PdfProblem.Unsupported -> ReaderFailure.Unsupported
    PdfProblem.Missing -> ReaderFailure.Missing
    // a locked PDF asks for the password instead
    PdfProblem.Password, PdfProblem.WrongPassword, PdfProblem.Unknown -> ReaderFailure.Unknown
  }
  // the file is gone, or the access to its folder was taken away
  is FileNotFoundException, is SecurityException -> ReaderFailure.Missing
  is KindleDrmException -> ReaderFailure.Drm
  else -> ReaderFailure.Unknown
}
