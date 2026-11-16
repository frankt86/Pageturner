package voice.features.bookOverview.overview

import android.net.Uri
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import voice.core.data.Book
import voice.core.data.Ebook
import voice.core.data.isIn
import voice.core.datastore.VoiceDataStoreFactory

@Serializable
sealed interface LibrarySource {

  @Serializable @SerialName("folder") data class Folder(val uri: String) : LibrarySource

  // stays readable in the stored hidden sources of listeners who hid a server library
  @Serializable
  @SerialName("serverLibrary")
  data class ServerLibrary(val id: String) : LibrarySource
}

@SingleIn(AppScope::class)
class HiddenSourcesStore(store: DataStore<Set<LibrarySource>>) :
  DataStore<Set<LibrarySource>> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.create(
      serializer = SetSerializer(LibrarySource.serializer()),
      defaultValue = emptySet(),
      fileName = "hiddenLibrarySources",
    ),
  )
}

internal fun Book.source(folders: List<Uri>): LibrarySource? {
  return folders.firstOrNull { id.isIn(it) }?.let { LibrarySource.Folder(it.toString()) }
}

internal fun Ebook.source(folders: List<Uri>): LibrarySource? {
  return folders.firstOrNull { bookId.isIn(it) }?.let { LibrarySource.Folder(it.toString()) }
}
