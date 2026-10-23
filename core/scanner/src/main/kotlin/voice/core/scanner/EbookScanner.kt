package voice.core.scanner

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import voice.core.data.Ebook
import voice.core.data.ebookFormat
import voice.core.data.store.EbooksStore
import voice.core.documentfile.CachedDocumentFile
import voice.core.documentfile.nameWithoutExtension
import voice.core.documentfile.walk

@Inject
internal class EbookScanner(
  @EbooksStore
  private val store: DataStore<List<Ebook>>,
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
        previousByUri[ebook.uri]?.copy(name = ebook.name, available = true) ?: ebook
      } + previous.filter { it.uri !in foundUris }.map { it.copy(available = false) }
    }
  }
}
