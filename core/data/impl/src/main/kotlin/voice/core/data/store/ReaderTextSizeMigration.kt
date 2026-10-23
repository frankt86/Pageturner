package voice.core.data.store

import android.app.Application
import androidx.datastore.core.DataMigration
import voice.core.data.ReaderSettings
import java.io.File

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
