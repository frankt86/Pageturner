package voice.features.reader

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.builtins.serializer
import voice.core.data.ReadingPace
import voice.core.datastore.VoiceDataStoreFactory

/** Whether reading aloud uses the downloaded natural voice instead of the phone's text-to-speech voice. */
@SingleIn(AppScope::class)
class ReaderNaturalVoiceStore(store: DataStore<Boolean>) : DataStore<Boolean> by store {

  @Inject
  constructor(factory: VoiceDataStoreFactory) : this(
    factory.boolean("readerNaturalVoice", defaultValue = true),
  )
}

/** Which voice of the natural voice reads aloud, -1 for the default one of the phone's language. */
@SingleIn(AppScope::class)
class ReaderNaturalSpeakerStore(store: DataStore<Int>) : DataStore<Int> by store {

  @Inject
  constructor(factory: VoiceDataStoreFactory) : this(
    factory.int("readerNaturalSpeaker", defaultValue = -1),
  )
}

/** How fast reading aloud speaks, 1 being normal. */
@SingleIn(AppScope::class)
class ReaderSpeechRateStore(store: DataStore<Float>) : DataStore<Float> by store {

  @Inject
  constructor(factory: VoiceDataStoreFactory) : this(
    factory.create(
      serializer = Float.serializer(),
      defaultValue = 1F,
      fileName = "readerSpeechRate",
    ),
  )
}

/** How fast the reader reads, to tell the reading time left. */
@SingleIn(AppScope::class)
class ReadingPaceStore(store: DataStore<ReadingPace>) : DataStore<ReadingPace> by store {

  @Inject
  constructor(factory: VoiceDataStoreFactory) : this(
    factory.create(
      serializer = ReadingPace.serializer(),
      defaultValue = ReadingPace(),
      fileName = "readingPace",
    ),
  )
}
