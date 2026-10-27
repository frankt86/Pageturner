package voice.core.opds.komga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import voice.core.data.EbookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KomgaProgressTest {

  @Test
  fun bookIdFromTheOpdsDownload() {
    assertEquals(
      "0B7KXZ3GT0MKK",
      komgaBookId("https://komga.example.com/opds/v1.2/books/0B7KXZ3GT0MKK/file/My%20Book.epub", entryId = "other"),
    )
  }

  @Test
  fun bookIdFromTheApiDownload() {
    assertEquals("0B7KXZ3GT0MKK", komgaBookId("http://192.168.1.2:25600/komga/api/v1/books/0B7KXZ3GT0MKK/file", entryId = ""))
  }

  @Test
  fun bookIdFromTheEntry() {
    assertEquals("0B7KXZ3GT0MKK", komgaBookId("https://example.com/download/1", entryId = "urn:uuid:0B7KXZ3GT0MKK"))
    assertEquals("0B7KXZ3GT0MKK", komgaBookId("https://example.com/download/1", entryId = "0B7KXZ3GT0MKK"))
  }

  @Test
  fun noBookIdElsewhere() {
    assertNull(komgaBookId("https://example.com/download/1", entryId = "urn:uuid:6f0b5f7e-3f1a-4c4e-9d58-1b2c3d4e5f60"))
  }

  @Test
  fun newerServerPlaceWins() {
    assertEquals(SyncAction.Pull, syncAction(positionModified = 1_000, syncedPosition = 1_000, remoteModified = 2_000))
    // also over a change here that wasn't sent yet
    assertEquals(SyncAction.Pull, syncAction(positionModified = 1_500, syncedPosition = 1_000, remoteModified = 2_000))
  }

  @Test
  fun newerPlaceHereIsSent() {
    assertEquals(SyncAction.Push, syncAction(positionModified = 3_000, syncedPosition = 1_000, remoteModified = 2_000))
    assertEquals(SyncAction.Push, syncAction(positionModified = 3_000, syncedPosition = 0, remoteModified = null))
  }

  @Test
  fun samePlaceStays() {
    assertEquals(SyncAction.Nothing, syncAction(positionModified = 2_000, syncedPosition = 2_000, remoteModified = 2_000))
    assertEquals(SyncAction.Nothing, syncAction(positionModified = 0, syncedPosition = 0, remoteModified = null))
  }

  @Test
  fun finishedAndNotStartedBooks() {
    assertEquals(KomgaUpdate.Read, update(progress = 1F))
    assertEquals(KomgaUpdate.Read, update(progress = 0.995F))
    assertEquals(KomgaUpdate.Unread, update(progress = 0F, lastOpened = 0))
  }

  @Test
  fun pdfPagesCountFromOne() {
    assertEquals(KomgaUpdate.Page(5), update(format = EbookFormat.Pdf, progress = 0.4F, section = 4))
  }

  @Test
  fun epubTakesTheFileOfTheNewBookEngine() {
    val locator = """
      {"href":"OEBPS/Text/Chapter%201.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5,"totalProgression":0.25}}
    """.trimIndent()
    assertEquals(
      KomgaUpdate.Progression(totalProgression = 0.25, href = "OEBPS/Text/Chapter 1.xhtml", progression = 0.5),
      update(progress = 0.25F, readiumLocator = locator),
    )
  }

  @Test
  fun epubSkipsTheFileWhenTheOldBookEngineMovedOn() {
    val locator = """{"href":"OEBPS/ch1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5,"totalProgression":0.25}}"""
    assertEquals(
      KomgaUpdate.Progression(totalProgression = 0.5F.toDouble(), href = null, progression = null),
      update(progress = 0.5F, readiumLocator = locator),
    )
  }

  @Test
  fun positionInTheSameFile() {
    val update = KomgaUpdate.Progression(totalProgression = 0.9, href = "OEBPS/ch2.xhtml", progression = 0.5)
    assertEquals(5, positionAt(positions, update)!!.position)
  }

  @Test
  fun positionAtTheStartOfTheFile() {
    val update = KomgaUpdate.Progression(totalProgression = 0.9, href = "OEBPS/ch2.xhtml", progression = null)
    assertEquals(4, positionAt(positions, update)!!.position)
  }

  @Test
  fun positionFromTheWholeBook() {
    assertEquals(1, positionAt(positions, KomgaUpdate.Progression(0.0, href = null, progression = null))!!.position)
    assertEquals(3, positionAt(positions, KomgaUpdate.Progression(0.4, href = "missing.xhtml", progression = 0.5))!!.position)
    assertEquals(6, positionAt(positions, KomgaUpdate.Progression(1.0, href = null, progression = null))!!.position)
    assertNull(positionAt(emptyList(), KomgaUpdate.Progression(0.5, href = null, progression = null)))
  }

  @Test
  fun bookProgress() {
    val json = """
      {"id":"0B7KXZ3GT0MKK","media":{"pagesCount":200,"mediaProfile":"EPUB"},
       "readProgress":{"page":50,"completed":false,"readDate":"2026-10-27T10:15:30Z","deviceId":"","deviceName":""}}
    """.trimIndent()
    assertEquals(
      KomgaBookProgress(page = 50, pageCount = 200, completed = false, modified = 1_793_096_130_000),
      parseBookProgress(json),
    )
    assertNull(parseBookProgress("""{"id":"0B7KXZ3GT0MKK","media":{"pagesCount":200}}"""))
  }

  @Test
  fun dates() {
    assertEquals(1_793_096_130_000, parseInstant("2026-10-27T10:15:30Z"))
    assertEquals(1_793_096_130_250, parseInstant("2026-10-27T10:15:30.250Z"))
    assertEquals(1_793_096_130_000, parseInstant("2026-10-27T12:15:30+02:00"))
    assertNull(parseInstant("yesterday"))
    assertEquals(1_793_096_130_250, parseInstant(formatInstant(1_793_096_130_250)))
  }

  @Test
  fun epubPlaceFromTheServer() {
    val book = KomgaBookProgress(page = 50, pageCount = 200, completed = false, modified = 1)
    val locator = Json.parseToJsonElement(
      """{"href":"OEBPS/ch%202.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5,"totalProgression":0.3}}""",
    ).jsonObject
    assertEquals(
      RemotePlace(modified = 1, totalProgression = 0.3, href = "OEBPS/ch 2.xhtml", progression = 0.5, page = null, completed = false),
      remotePlace(EbookFormat.Epub, book, locator),
    )
    assertEquals(0.25, remotePlace(EbookFormat.Epub, book, locator = null).totalProgression)
  }

  @Test
  fun pdfPlaceFromTheServer() {
    val place = remotePlace(EbookFormat.Pdf, KomgaBookProgress(page = 11, pageCount = 21, completed = false, modified = 1), null)
    assertEquals(10, place.page)
    assertEquals(0.5, place.totalProgression)
  }

  @Test
  fun finishedOnTheServer() {
    val place = remotePlace(EbookFormat.Pdf, KomgaBookProgress(page = 3, pageCount = 21, completed = true, modified = 1), null)
    assertEquals(1.0, place.totalProgression)
    assertEquals(20, place.page)
    assertTrue(place.completed)
  }

  @Test
  fun samePlace() {
    val place = RemotePlace(modified = 1, totalProgression = 0.5, href = null, progression = null, page = null, completed = false)
    assertTrue(place.isSamePlaceAs(0.502F))
    assertFalse(place.isSamePlaceAs(0.52F))
  }

  private fun update(
    format: EbookFormat = EbookFormat.Epub,
    progress: Float,
    section: Int = 0,
    lastOpened: Long = 1,
    readiumLocator: String? = null,
  ) = komgaUpdate(format, progress, section, lastOpened, readiumLocator)

  private val JsonObject.position: Int
    get() = getValue("locations").jsonObject.getValue("position").jsonPrimitive.int

  // like Komga's own: two files, three positions each
  private val positions: List<JsonObject> = listOf(
    Triple("OEBPS/ch1.xhtml", 0.0, 1),
    Triple("OEBPS/ch1.xhtml", 0.33333334, 2),
    Triple("OEBPS/ch1.xhtml", 0.6666667, 3),
    Triple("OEBPS/ch2.xhtml", 0.0, 4),
    Triple("OEBPS/ch2.xhtml", 0.33333334, 5),
    Triple("OEBPS/ch2.xhtml", 0.6666667, 6),
  ).map { (href, progression, position) ->
    Json.parseToJsonElement(
      """{"href":"$href","type":"application/xhtml+xml","locations":{"progression":$progression,"position":$position,"totalProgression":${position / 6.0}}}""",
    ).jsonObject
  }
}
