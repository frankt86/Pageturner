package voice.core.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import voice.core.data.Ebook
import voice.core.data.EbookFormat
import voice.core.data.ebookFormat
import voice.core.data.store.EbooksStore
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension
import voice.core.documentfile.walk
import voice.core.ebook.parseEpub
import voice.core.ebook.renderPdfCover
import voice.core.logging.api.Logger
import java.io.File
import kotlin.uuid.Uuid

@Inject
internal class EbookScanner(
  @EbooksStore
  private val store: DataStore<List<Ebook>>,
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
    store.updateData { previous ->
      val previousByUri = previous.associateBy { it.uri }
      val foundUris = found.mapTo(mutableSetOf()) { it.uri }
      found.map { ebook ->
        val known = previousByUri[ebook.uri]
        // once read from the book, its own title wins over the file name
        known?.copy(name = if (known.metadataRead) known.name else ebook.name, available = true) ?: ebook
      } + previous.filter { it.uri !in foundUris }.map { it.copy(available = false) }
    }
    store.data.first()
      .filter { it.available && !it.metadataRead }
      .forEach { ebook ->
        val metadata = readMetadata(ebook)
        store.updateData { ebooks ->
          ebooks.map {
            if (it.uri == ebook.uri) {
              it.copy(
                name = metadata?.title ?: it.name,
                author = metadata?.author,
                cover = metadata?.cover,
                metadataRead = true,
              )
            } else {
              it
            }
          }
        }
      }
  }

  private class Metadata(
    val title: String?,
    val author: String?,
    val cover: String?,
  )

  private fun readMetadata(ebook: Ebook): Metadata? {
    return try {
      when (ebook.format) {
        EbookFormat.Epub -> context.contentResolver.openInputStream(ebook.uri)
          ?.use { parseEpub(it, withText = false) }
          ?.let { epub ->
            Metadata(
              title = epub.title,
              author = epub.author,
              cover = epub.cover?.let { bytes -> newCoverFile().apply { writeBytes(bytes) }.absolutePath },
            )
          }
        EbookFormat.Pdf -> context.contentResolver.openFileDescriptor(ebook.uri, "r")
          ?.let { renderPdfCover(it, width = 600) }
          ?.let { bitmap ->
            val file = newCoverFile()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
            Metadata(title = null, author = null, cover = file.absolutePath)
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
