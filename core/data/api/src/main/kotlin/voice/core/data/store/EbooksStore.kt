package voice.core.data.store

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.builtins.ListSerializer
import voice.core.data.Ebook
import voice.core.datastore.VoiceDataStoreFactory

/** The EPUB and PDF files found in the library folders, with where the reader left off. */
@SingleIn(AppScope::class)
public class EbooksStore(store: DataStore<List<Ebook>>) : DataStore<List<Ebook>> by store {

  @Inject
  public constructor(factory: VoiceDataStoreFactory) : this(
    factory.create(
      serializer = ListSerializer(Ebook.serializer()),
      defaultValue = emptyList(),
      fileName = "ebooks",
    ),
  )
}
