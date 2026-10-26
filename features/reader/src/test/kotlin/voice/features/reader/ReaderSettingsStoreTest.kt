package voice.features.reader

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import voice.core.data.ReaderSettings
import voice.core.datastore.VoiceDataStoreFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class ReaderSettingsStoreTest {

  private val application: Application = ApplicationProvider.getApplicationContext()
  private val factory = VoiceDataStoreFactory(Json { ignoreUnknownKeys = true }, application)

  @Test
  fun `the reader settings take over the text size from its old store`() = runTest {
    val oldFile = File(application.filesDir, "datastore/readerTextSize")
    oldFile.parentFile!!.mkdirs()
    oldFile.writeText("22.0")

    val store = ReaderSettingsStore(factory, application)

    assertEquals(expected = ReaderSettings(textSize = 22F), actual = store.data.first())
    assertFalse(oldFile.exists())
  }

  @Test
  fun `the reader settings start with the defaults without an old store`() = runTest {
    val store = ReaderSettingsStore(factory, application)

    assertEquals(expected = ReaderSettings(), actual = store.data.first())
  }
}
