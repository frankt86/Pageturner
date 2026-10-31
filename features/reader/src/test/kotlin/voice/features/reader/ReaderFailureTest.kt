package voice.features.reader

import voice.core.ebook.PdfOpenException
import voice.core.ebook.PdfProblem
import voice.core.ebook.mobi.KindleDrmException
import voice.core.ebook.mobi.KindleFormatException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderFailureTest {

  private fun pdf(problem: PdfProblem) = PdfOpenException(problem, IOException())

  @Test
  fun `a PDF that can't be opened tells why`() {
    assertEquals(ReaderFailure.Damaged, readerFailure(pdf(PdfProblem.Damaged)))
    assertEquals(ReaderFailure.Unsupported, readerFailure(pdf(PdfProblem.Unsupported)))
    assertEquals(ReaderFailure.Missing, readerFailure(pdf(PdfProblem.Missing)))
    assertEquals(ReaderFailure.Unknown, readerFailure(pdf(PdfProblem.Unknown)))
  }

  @Test
  fun `a book whose file is gone or out of reach is missing`() {
    assertEquals(ReaderFailure.Missing, readerFailure(FileNotFoundException()))
    assertEquals(ReaderFailure.Missing, readerFailure(SecurityException("Permission Denial")))
  }

  @Test
  fun `a kindle book with DRM says so`() {
    assertEquals(ReaderFailure.Drm, readerFailure(KindleDrmException()))
    assertEquals(ReaderFailure.Unknown, readerFailure(KindleFormatException("Topaz")))
  }

  @Test
  fun `anything else gets the general message`() {
    assertEquals(ReaderFailure.Unknown, readerFailure(ZipException()))
    assertEquals(ReaderFailure.Unknown, readerFailure(IllegalStateException()))
  }
}
