package voice.core.common

import android.os.Build
import androidx.core.net.toUri

/**
 * Where listeners can get help and tell about their ideas and problems.
 */
object FeedbackLinks {

  const val QUESTIONS = "https://github.com/frankt86/Pageturner/issues/new"
  const val IDEAS = "https://github.com/frankt86/Pageturner/issues/new"
  const val FAQ = "https://github.com/frankt86/Pageturner/blob/main/docs/faq.md"

  /**
   * A new bug report, filled in with the app version and the device.
   */
  fun bugReport(versionName: String): String {
    return "https://github.com/frankt86/Pageturner/issues/new".toUri()
      .buildUpon()
      .appendQueryParameter("template", "bug.yml")
      .appendQueryParameter("version", versionName)
      .appendQueryParameter("androidversion", Build.VERSION.SDK_INT.toString())
      .appendQueryParameter("device", Build.MODEL)
      .toString()
  }
}
