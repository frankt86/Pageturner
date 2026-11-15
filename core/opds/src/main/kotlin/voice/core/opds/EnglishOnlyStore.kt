package voice.core.opds

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.datastore.VoiceDataStoreFactory

/** Whether catalogs only show books in English. */
@SingleIn(AppScope::class)
public class EnglishOnlyStore(store: DataStore<Boolean>) : DataStore<Boolean> by store {

  @Inject
  public constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.boolean("opdsEnglishOnly", defaultValue = true),
  )
}
