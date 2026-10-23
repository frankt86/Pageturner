package voice.features.reader

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

/** Hides the system bars while [immersive] and sets the screen [brightness], both only while the reader is open. */
@Composable
internal fun ReaderWindow(
  immersive: Boolean,
  brightness: Float?,
) {
  val window = LocalActivity.current?.window ?: return
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
