package voice.core.opds.feed

import kotlin.test.Test
import kotlin.test.assertEquals

class OpdsFormatTest {

  @Test
  fun `kindle books are recognized by their type and extension`() {
    assertEquals(OpdsFormat.Mobi, opdsFormat("application/x-mobipocket-ebook", "https://example.com/book"))
    assertEquals(OpdsFormat.Azw3, opdsFormat("application/x-mobi8-ebook", "https://example.com/book"))
    assertEquals(OpdsFormat.Azw3, opdsFormat("application/vnd.amazon.ebook", "https://example.com/book.azw3"))
    assertEquals(OpdsFormat.Mobi, opdsFormat("application/vnd.amazon.ebook", "https://example.com/book.azw"))
    assertEquals(OpdsFormat.Mobi, opdsFormat(null, "https://example.com/book.mobi"))
    assertEquals(OpdsFormat.Azw3, opdsFormat("application/octet-stream", "https://example.com/book.azw3?x=1"))
  }

  @Test
  fun `an epub and a pdf come before kindle books, and azw3 before mobi`() {
    fun preferred(vararg types: String) = publication(types.map { OpdsAcquisition("https://example.com/$it", it) })
      .preferredAcquisition?.type
    assertEquals(
      "application/epub+zip",
      preferred("application/x-mobipocket-ebook", "application/x-mobi8-ebook", "application/epub+zip", "application/pdf"),
    )
    assertEquals("application/pdf", preferred("application/x-mobipocket-ebook", "application/pdf"))
    assertEquals("application/x-mobi8-ebook", preferred("application/x-mobipocket-ebook", "application/x-mobi8-ebook"))
    assertEquals("application/x-mobipocket-ebook", preferred("application/x-cbz", "application/x-mobipocket-ebook"))
  }

  private fun publication(acquisitions: List<OpdsAcquisition>) = OpdsPublication(
    id = "id",
    title = "Persuasion",
    authors = emptyList(),
    summary = null,
    coverUrl = null,
    thumbnailUrl = null,
    acquisitions = acquisitions,
  )
}
