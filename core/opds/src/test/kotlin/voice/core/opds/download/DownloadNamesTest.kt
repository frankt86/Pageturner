package voice.core.opds.download

import voice.core.data.folders.FolderType
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadNamesTest {

  @Test
  fun freeNameStays() {
    assertEquals("Persuasion.epub", uniqueName("Persuasion", "epub", taken = listOf("Emma.epub")))
  }

  @Test
  fun takenNameGetsANumber() {
    assertEquals(
      "Persuasion (3).epub",
      uniqueName("Persuasion", "epub", taken = listOf("Persuasion.epub", "persuasion (2).EPUB")),
    )
  }

  @Test
  fun folderWithoutExtension() {
    assertEquals("Persuasion (2)", uniqueName("Persuasion", extension = null, taken = listOf("Persuasion", "Persuasion.epub")))
  }

  @Test
  fun unsafeCharactersAndDotsGo() {
    assertEquals("AC_DC_ Live", safeFileName("AC/DC: Live"))
    assertEquals("hidden", safeFileName("..hidden"))
    assertEquals("Book", safeFileName("  ", fallback = "Book"))
  }

  @Test
  fun onlyWritableFoldersOfSeveralBooks() {
    val candidates = downloadFolderCandidates(
      folders = mapOf(
        FolderType.SingleFile to listOf("file"),
        FolderType.SingleFolder to listOf("book"),
        FolderType.Author to listOf("authors"),
        FolderType.Root to listOf("library", "readOnly"),
      ),
      writable = setOf("file", "book", "authors", "library"),
    )
    assertEquals(listOf("library" to FolderType.Root, "authors" to FolderType.Author), candidates)
  }

  @Test
  fun authorFoldersGetAFolderOfTheAuthor() {
    assertEquals(listOf("Jane Austen"), bookFolderPath(FolderType.Author, "Jane Austen"))
    assertEquals(listOf("Unknown author"), bookFolderPath(FolderType.Author, null))
    assertEquals(emptyList(), bookFolderPath(FolderType.Root, "Jane Austen"))
  }
}
