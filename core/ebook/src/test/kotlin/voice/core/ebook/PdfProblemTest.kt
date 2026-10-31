package voice.core.ebook

import io.legere.pdfiumandroid.api.PdfPasswordException
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfProblemTest {

  @Test
  fun `a locked PDF asks for the password`() {
    val problem = pdfProblem(SecurityException("password"), PdfPasswordException(), withPassword = false)
    assertEquals(PdfProblem.Password, problem)
  }

  @Test
  fun `a password that doesn't unlock it is wrong`() {
    val problem = pdfProblem(SecurityException("password"), PdfPasswordException(), withPassword = true)
    assertEquals(PdfProblem.WrongPassword, problem)
  }

  @Test
  fun `before Android 15 only PDFium hears the password`() {
    val problem = pdfProblem(renderer = null, PdfPasswordException(), withPassword = true)
    assertEquals(PdfProblem.WrongPassword, problem)
  }

  @Test
  fun `a broken file is damaged`() {
    val problem = pdfProblem(
      IOException("file not in PDF format or corrupted"),
      IOException("File not in PDF format or corrupted."),
      withPassword = false,
    )
    assertEquals(PdfProblem.Damaged, problem)
  }

  @Test
  fun `an empty file is damaged`() {
    assertEquals(PdfProblem.Damaged, pdfProblem(null, IOException("File is empty"), withPassword = false))
  }

  @Test
  fun `an encryption PDFium doesn't know is unsupported`() {
    val problem = pdfProblem(SecurityException(), IOException("Unsupported security scheme."), withPassword = false)
    assertEquals(PdfProblem.Unsupported, problem)
  }

  @Test
  fun `a file PDFium can't read is missing`() {
    val problem = pdfProblem(null, IOException("File not found or could not be opened."), withPassword = false)
    assertEquals(PdfProblem.Missing, problem)
  }

  @Test
  fun `without PDFium, PdfRenderer's answer counts`() {
    val noPdfium = RuntimeException("Failed to initialize PdfiumCore native libraries")
    assertEquals(PdfProblem.Password, pdfProblem(SecurityException(), noPdfium, withPassword = false))
    assertEquals(PdfProblem.Damaged, pdfProblem(IOException(), noPdfium, withPassword = false))
    assertEquals(PdfProblem.Unknown, pdfProblem(IllegalStateException(), noPdfium, withPassword = false))
  }

  @Test
  fun `an error PDFium doesn't explain falls back to PdfRenderer's`() {
    assertEquals(PdfProblem.Damaged, pdfProblem(IOException(), IOException("Unknown error"), withPassword = false))
    assertEquals(PdfProblem.Unknown, pdfProblem(null, IOException("Unknown error"), withPassword = false))
  }

  @Test
  fun `a file that's gone or out of reach is missing`() {
    assertEquals(PdfProblem.Missing, fileProblem(FileNotFoundException()))
    assertEquals(PdfProblem.Missing, fileProblem(SecurityException("Permission Denial")))
    assertEquals(PdfProblem.Unknown, fileProblem(IOException("No space left on device")))
  }

  @Test
  fun `PDFium's text gets plain line ends`() {
    assertEquals("One\nTwo\nThree", pdfiumText("One\r\nTwo\rThree\u0000"))
  }
}
