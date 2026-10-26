package voice.features.reader

import android.app.Application
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.data.ReaderSettings
import voice.core.datastore.VoiceDataStoreFactory
import java.io.File

/** How the ebook reader shows the pages: text, colors, brightness and turning pages with the volume keys. */
@SingleIn(AppScope::class)
class ReaderSettingsStore(store: DataStore<ReaderSettings>) : DataStore<ReaderSettings> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
    application: Application,
  ) : this(
    factory.create(
      serializer = ReaderSettings.serializer(),
      defaultValue = ReaderSettings(),
      fileName = "readerSettings",
      migrations = listOf(ReaderTextSizeMigration(application)),
    ),
  )
}

// the text size had a store of its own before the other reader settings came
internal class ReaderTextSizeMigration(application: Application) : DataMigration<ReaderSettings> {

  private val oldFile = File(application.filesDir, "datastore/readerTextSize")

  override suspend fun shouldMigrate(currentData: ReaderSettings): Boolean = oldFile.exists()

  override suspend fun migrate(currentData: ReaderSettings): ReaderSettings {
    val textSize = oldFile.readText().trim().toFloatOrNull() ?: return currentData
    return currentData.copy(textSize = textSize)
  }

  override suspend fun cleanUp() {
    oldFile.delete()
  }
}
