package voice.features.reader

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import voice.core.data.ReaderOrientation

/**
 * Hides the system bars while [immersive], sets the screen [brightness] and keeps the screen in the chosen [orientation],
 * all only while the reader is open.
 */
// the listener picks the orientation, so it's fine on large screens too
@SuppressLint("SourceLockedOrientationActivity")
@Composable
internal fun ReaderWindow(
  immersive: Boolean,
  brightness: Float?,
  orientation: ReaderOrientation,
) {
  val activity = LocalActivity.current ?: return
  val window = activity.window
  val view = LocalView.current
  val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
  DisposableEffect(controller) {
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    onDispose {
      controller.show(WindowInsetsCompat.Type.systemBars())
      controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }
  }
  SideEffect {
    if (immersive) {
      controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      controller.show(WindowInsetsCompat.Type.systemBars())
    }
  }
  DisposableEffect(activity, orientation) {
    activity.requestedOrientation = when (orientation) {
      ReaderOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      ReaderOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
      ReaderOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }
    onDispose {
      // turning the screen recreates the activity, the reader sets the orientation again then
      if (!activity.isChangingConfigurations) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      }
    }
  }
  DisposableEffect(window, brightness) {
    window.attributes = window.attributes.apply {
      screenBrightness = brightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
    onDispose {
      window.attributes = window.attributes.apply {
        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
      }
    }
  }
}
