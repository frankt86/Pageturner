package voice.core.opds.download

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import voice.core.initializer.AppInitializer

@ContributesIntoSet(AppScope::class)
@Inject
public class OpdsAudiobookInitializer(private val downloads: OpdsAudiobookDownloads) :
  AppInitializer {

  override fun onAppStart(application: Application) {
    downloads.start()
    // while Pageturner is open, it shows the result of a download itself
    application.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        private var startedActivities = 0

        override fun onActivityStarted(activity: Activity) {
          if (startedActivities++ == 0) downloads.onAppVisibilityChange(true)
        }

        override fun onActivityStopped(activity: Activity) {
          if (--startedActivities == 0) downloads.onAppVisibilityChange(false)
        }

        override fun onActivityCreated(
          activity: Activity,
          savedInstanceState: Bundle?,
        ) {}

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivitySaveInstanceState(
          activity: Activity,
          outState: Bundle,
        ) {}

        override fun onActivityDestroyed(activity: Activity) {}
      },
    )
  }
}
