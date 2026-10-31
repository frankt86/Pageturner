package voice.core.scanner

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.ebookFormat
import voice.core.data.store.EbooksStore
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension
import voice.core.documentfile.walk
import voice.core.ebook.mobi.deleteStaleKindleEpubs
import voice.core.ebook.mobi.readKindleMetadata
import voice.core.ebook.parseEpub
import voice.core.ebook.renderPdfCover
import voice.core.logging.api.Logger
import java.io.File
import kotlin.uuid.Uuid

@Inject
internal class EbookScanner(
  private val store: EbooksStore,
  private val context: Context,
) {

  suspend fun scan(folders: List<CachedDocumentFile>) {
    val found = folders.asSequence()
      .flatMap { it.walk() }
      .mapNotNull { file ->
        file.ebookFormat()?.let { format ->
          Ebook(uri = file.uri, name = file.nameWithoutExtension(), format = format)
        }
      }
      .distinctBy { it.uri }
      .toList()
    store.updateData { all ->
      // older downloads from a catalog live in the app's storage, outside of every folder. Newer ones are in a folder
      // of the library and found there.
      val (downloaded, previous) = all.partition { it.opdsSource != null && it.uri.scheme == ContentResolver.SCHEME_FILE }
      val previousByUri = previous.associateBy { it.uri }
      val foundUris = found.mapTo(mutableSetOf()) { it.uri }
      val gone = previous.filter { it.uri !in foundUris }
        // a removed book comes back once its folder is added again
        .map { it.copy(available = false, hidden = false) }
      found.map { ebook ->
        val known = previousByUri[ebook.uri]
        // once read from the book or taken from a catalog, its own title wins over the file name
        val keepName = known != null && (known.metadataRead || known.opdsSource != null)
        known?.copy(name = if (keepName) known.name else ebook.name, available = true) ?: ebook
      } + gone + downloaded.map { it.copy(available = it.uri.path?.let(::File)?.exists() == true) }
    }
    store.data.first()
      .filter { it.available && !it.hidden && (!it.metadataRead || (it.format == EbookFormat.Epub && !it.seriesRead)) }
      .forEach { ebook ->
        val metadata = readMetadata(ebook, withCover = !ebook.metadataRead)
        store.updateData { ebooks ->
          ebooks.map { if (it.uri == ebook.uri) it.withMetadata(metadata) else it }
        }
      }
    val kindleBooks = store.data.first().filter { it.format.isKindle && it.available && !it.hidden }.map { it.uri }
    deleteStaleKindleEpubs(context, keep = kindleBooks)
  }

  private class Metadata(
    val title: String?,
    val author: String?,
    val cover: String?,
    val series: String? = null,
    val seriesPart: String? = null,
  )

  private fun Ebook.withMetadata(metadata: Metadata?): Ebook {
    if (metadataRead) {
      // a book read before series were, gets only those
      return metadata?.let { copy(series = it.series, seriesPart = it.seriesPart, seriesRead = true) }
        ?: copy(seriesRead = true)
    }
    // like a name the listener gave, what the catalog said wins over the file
    val fromCatalog = opdsSource != null
    return copy(
      name = if (fromCatalog) name else metadata?.title ?: name,
      author = if (fromCatalog && author != null) author else metadata?.author,
      cover = metadata?.cover,
      series = metadata?.series,
      seriesPart = metadata?.seriesPart,
      metadataRead = true,
      seriesRead = true,
    )
  }

  private fun readMetadata(
    ebook: Ebook,
    withCover: Boolean,
  ): Metadata? {
    return try {
      when (ebook.format) {
        EbookFormat.Epub -> context.contentResolver.openInputStream(ebook.uri)
          ?.use { parseEpub(it, withText = false) }
          ?.let { epub ->
            Metadata(
              title = epub.title,
              author = epub.author,
              cover = epub.cover?.takeIf { withCover }?.let { bytes -> newCoverFile().apply { writeBytes(bytes) }.absolutePath },
              series = epub.series,
              seriesPart = epub.seriesPart,
            )
          }
        EbookFormat.Pdf -> context.contentResolver.openFileDescriptor(ebook.uri, "r")
          ?.let { renderPdfCover(it, width = 600) }
          ?.let { bitmap ->
            val file = newCoverFile()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            Metadata(title = null, author = null, cover = file.absolutePath)
          }
        EbookFormat.Mobi, EbookFormat.Azw3 -> context.contentResolver.openInputStream(ebook.uri)
          ?.use { readKindleMetadata(it, withCover) }
          ?.let { kindle ->
            Metadata(
              title = kindle.title,
              author = kindle.author,
              cover = kindle.cover?.let { bytes -> newCoverFile().apply { writeBytes(bytes) }.absolutePath },
            )
          }
      }
    } catch (e: Exception) {
      Logger.w(e, "Could not read the metadata of a ${ebook.format}")
      null
    }
  }

  private fun newCoverFile(): File {
    return File(context.filesDir, "ebookCovers").apply { mkdirs() }.resolve(Uuid.random().toString())
  }
}
