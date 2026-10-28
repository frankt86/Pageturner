package voice.core.opds.download

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import voice.core.initializer.AppInitializer

@ContributesIntoSet(AppScope::class)
@Inject
public class OpdsAudiobookInitializer(private val downloads: OpdsAudiobookDownloads) : AppInitializer {

  override fun onAppStart(application: Application) {
    downloads.start()
  }
}
