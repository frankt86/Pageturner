package vocie.core.data.store

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.runner.RunWith
import voice.core.data.ReaderSettings
import voice.core.data.store.ReaderTextSizeMigration
import voice.core.data.store.VoiceDataStoreFactory
import voice.core.data.store.booleanPrefsDataMigration
import voice.core.data.store.intPrefsDataMigration
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class DataMigrationTests {

  private lateinit var context: Application
  private lateinit var sharedPreferences: SharedPreferences
  private lateinit var factory: VoiceDataStoreFactory

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    sharedPreferences = context.getSharedPreferences("de.ph1b.audiobook_preferences", Context.MODE_PRIVATE)
    factory = VoiceDataStoreFactory(Json { ignoreUnknownKeys = true }, context)
  }

  @Test
  fun `intPrefsDataMigration migrates and cleans up arbitrary key`() = runTest {
    sharedPreferences.edit {
      clear()
      putInt("TEST_INT_KEY", 123)
    }
    val dataStore = factory.int(
      fileName = "testInt",
      defaultValue = 0,
      migrations = listOf(intPrefsDataMigration(sharedPreferences, "TEST_INT_KEY")),
    )
    assertEquals(expected = 123, actual = dataStore.data.first())
    assertEquals(expected = false, actual = sharedPreferences.contains("TEST_INT_KEY"))
  }

  @Test
  fun `booleanPrefsDataMigration migrates and cleans up arbitrary key`() = runTest {
    sharedPreferences.edit {
      clear()
      putBoolean("testBoolKey", true)
    }
    val dataStore = factory.boolean(
      fileName = "testBool",
      defaultValue = false,
      migrations = listOf(booleanPrefsDataMigration(sharedPreferences, "testBoolKey")),
    )
    assertEquals(expected = true, actual = dataStore.data.first())
    assertEquals(expected = false, actual = sharedPreferences.contains("testBoolKey"))
  }

  @Test
  fun `the reader settings take over the text size from its old store`() = runTest {
    val oldFile = File(context.filesDir, "datastore/readerTextSize")
    oldFile.parentFile!!.mkdirs()
    oldFile.writeText("22.0")
    val dataStore = factory.create(
      serializer = ReaderSettings.serializer(),
      defaultValue = ReaderSettings(),
      fileName = "testReaderSettings",
      migrations = listOf(ReaderTextSizeMigration(context)),
    )
    assertEquals(expected = ReaderSettings(textSize = 22F), actual = dataStore.data.first())
    assertFalse(oldFile.exists())
  }
}
