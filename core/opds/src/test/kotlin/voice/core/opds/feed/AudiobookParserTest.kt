package voice.core.opds.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudiobookParserTest {

  private val opds1 = parseFeed(resource("audiobooks.xml"), "https://books.example.com/opds/audiobooks", null)
    .publications.associateBy { it.title }

  private val opds2 = parseFeed(resource("audiobooks.json"), "https://books.example.com/opds2/audiobooks", null)
    .publications.associateBy { it.title }

  @Test
  fun `an entry with an ebook and an audiobook offers both`() {
    val book = opds1.getValue("Persuasion")
    assertEquals(OpdsFormat.Epub, book.preferredAcquisition?.format)
    val audiobook = book.audiobook!!
    assertEquals(AudiobookPackaging.Files, audiobook.packaging)
    assertEquals(listOf("https://books.example.com/download/persuasion.m4b"), audiobook.files.map { it.url })
    assertEquals("M4B", audiobook.format)
    assertEquals(312_000_000L, audiobook.size)
  }

  @Test
  fun `one file per chapter is one book in the order of the feed`() {
    val audiobook = opds1.getValue("The Time Machine").audiobook!!
    assertEquals(AudiobookPackaging.Files, audiobook.packaging)
    assertEquals(
      listOf(
        "https://books.example.com/audio/time-machine/01.mp3",
        "https://books.example.com/audio/time-machine/02.mp3",
        "https://books.example.com/audio/time-machine/03.mp3",
      ),
      audiobook.files.map { it.url },
    )
    assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3"), audiobook.files.map { it.title })
    assertEquals("MP3", audiobook.format)
    assertEquals(6000L, audiobook.size)
    assertNull(opds1.getValue("The Time Machine").preferredAcquisition)
  }

  @Test
  fun `the same book linked twice is one file, not two chapters`() {
    val audiobook = opds1.getValue("Carmilla").audiobook!!
    assertEquals(listOf("https://books.example.com/download/carmilla/m4b"), audiobook.files.map { it.url })
    assertEquals(250_000_000L, audiobook.size)
  }

  @Test
  fun `a generic type is recognized by the extension in the title`() {
    val audiobook = opds1.getValue("Dracula").audiobook!!
    assertEquals("M4B", audiobook.format)
    assertEquals("m4b", audiobook.files.single().audioExtension)
    assertNull(audiobook.size)
  }

  @Test
  fun `a zip is an audiobook when the entry says so`() {
    val audiobook = opds1.getValue("Frankenstein").audiobook!!
    assertEquals(AudiobookPackaging.Zip, audiobook.packaging)
    assertEquals("https://books.example.com/download/frankenstein.zip", audiobook.files.single().url)
    assertEquals("ZIP", audiobook.format)

    val comic = opds1.getValue("Watchmen")
    assertNull(comic.audiobook)
    assertEquals("CBZ", comic.acquisitions.single().label)
  }

  @Test
  fun `an audiobook zip is always an audiobook`() {
    val emma = opds1.getValue("Emma")
    assertTrue(emma.isAudiobook)
    assertEquals(AudiobookPackaging.Zip, emma.audiobook?.packaging)
  }

  @Test
  fun `an m4b labelled as video comes before mp3 files`() {
    val audiobook = opds1.getValue("Middlemarch").audiobook!!
    assertEquals(listOf("https://books.example.com/download/middlemarch"), audiobook.files.map { it.url })
    assertEquals("M4B", audiobook.format)
    assertEquals("m4b", audiobook.files.single().audioExtension)
  }

  @Test
  fun `opds 2 audiobook manifest`() {
    val flatland = opds2.getValue("Flatland")
    assertTrue(flatland.isAudiobook)
    assertEquals(listOf("Edwin A. Abbott"), flatland.authors)
    val audiobook = flatland.audiobook!!
    assertEquals(AudiobookPackaging.Manifest, audiobook.packaging)
    assertEquals("https://books.example.com/audiobooks/flatland/manifest.json", audiobook.files.single().url)
  }

  @Test
  fun `opds 2 audio files`() {
    val audiobook = opds2.getValue("The Raven").audiobook!!
    assertEquals(AudiobookPackaging.Files, audiobook.packaging)
    assertEquals("OGG", audiobook.format)
    assertEquals(listOf(4000L, null), audiobook.files.map { it.length })
    assertNull(audiobook.size)
  }

  @Test
  fun `opds 2 zip that conforms to the audiobook profile`() {
    val ulysses = opds2.getValue("Ulysses")
    assertTrue(ulysses.isAudiobook)
    assertEquals(AudiobookPackaging.Zip, ulysses.audiobook?.packaging)
  }

  @Test
  fun `manifest lists the files in reading order`() {
    val files = parseAudiobookManifest(
      resource("audiobook-manifest.json"),
      "https://books.example.com/audiobooks/flatland/manifest.json",
    )
    assertEquals(
      listOf(
        OpdsAcquisition("https://books.example.com/audiobooks/flatland/audio/01.mp3", "audio/mpeg", "Part 1"),
        OpdsAcquisition("https://cdn.example.com/flatland/02.mp3", "audio/mpeg", "Part 2"),
      ),
      files,
    )
  }

  @Test
  fun `ebooks have no audiobook`() {
    val feed = parseFeed(resource("standard-ebooks.xml"), "https://standardebooks.org/feeds/opds/new-releases", null)
    assertNull(feed.publications.single().audiobook)
  }
}
