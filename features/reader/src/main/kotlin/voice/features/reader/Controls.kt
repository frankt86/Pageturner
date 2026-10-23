package voice.features.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import voice.core.data.ReaderSettings
import voice.core.data.TapAction
import voice.core.ui.bookText

/** What a tap at [fraction] of the page's width does. A book read from right to left swaps the sides, like left-handed. */
internal fun ReaderSettings.tapActionAt(
  fraction: Float,
  rightToLeft: Boolean,
): TapAction {
  val swapped = leftHanded != rightToLeft
  return when {
    fraction < 1F / 3 -> if (swapped) tapRight else tapLeft
    fraction > 2F / 3 -> if (swapped) tapLeft else tapRight
    else -> tapMiddle
  }
}

/**
 * Tells single and double taps apart. With a double tap in use, a single tap waits until no second one can come.
 */
@Stable
internal class Taps(private val scope: CoroutineScope) {

  private var pending: Job? = null

  fun tap(
    withDoubleTap: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
  ) {
    val waiting = pending
    pending = null
    if (waiting != null && waiting.isActive) {
      waiting.cancel()
      onDoubleTap()
      return
    }
    if (!withDoubleTap) {
      onTap()
      return
    }
    pending = scope.launch {
      delay(ViewConfiguration.getDoubleTapTimeout().toLong())
      onTap()
    }
  }
}

/** What a key does in the reader. */
internal enum class KeyAction {
  Next,
  Previous,
  Search,
  Bookmark,
  Display,
  Close,
}

/**
 * Page keys, arrow keys and the space bar turn the pages, as do the volume keys when [volumeKeys]. Bluetooth page turners
 * send arrow or page keys too.
 */
internal fun keyAction(
  keyCode: Int,
  ctrl: Boolean,
  shift: Boolean,
  volumeKeys: Boolean,
  rightToLeft: Boolean,
): KeyAction? {
  if (ctrl) {
    return when (keyCode) {
      KeyEvent.KEYCODE_F -> KeyAction.Search
      KeyEvent.KEYCODE_B -> KeyAction.Bookmark
      KeyEvent.KEYCODE_D -> KeyAction.Display
      else -> null
    }
  }
  val right = if (rightToLeft) KeyAction.Previous else KeyAction.Next
  val left = if (rightToLeft) KeyAction.Next else KeyAction.Previous
  return when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_DOWN -> KeyAction.Next.takeIf { volumeKeys }
    KeyEvent.KEYCODE_VOLUME_UP -> KeyAction.Previous.takeIf { volumeKeys }
    KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_NAVIGATE_NEXT -> KeyAction.Next
    KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> KeyAction.Previous
    KeyEvent.KEYCODE_DPAD_RIGHT -> right
    KeyEvent.KEYCODE_DPAD_LEFT -> left
    KeyEvent.KEYCODE_SPACE -> if (shift) KeyAction.Previous else KeyAction.Next
    KeyEvent.KEYCODE_ESCAPE -> KeyAction.Close
    else -> null
  }
}

/**
 * Takes the keys while nothing else has the focus. [onKey] gets the key code, whether Ctrl and Shift are held and whether
 * the key went down, and returns whether it took the key.
 */
@Composable
internal fun ReaderKeys(onKey: (keyCode: Int, ctrl: Boolean, shift: Boolean, down: Boolean) -> Boolean) {
  val focusRequester = remember { FocusRequester() }
  val latestOnKey by rememberUpdatedState(onKey)
  Box(
    Modifier
      .onPreviewKeyEvent { event ->
        latestOnKey(event.nativeKeyEvent.keyCode, event.isCtrlPressed, event.isShiftPressed, event.type == KeyEventType.KeyDown)
      }
      .focusRequester(focusRequester)
      .focusable(),
  )
  LaunchedEffect(focusRequester) {
    focusRequester.requestFocus()
  }
}

/** Each step of a mouse wheel calls [onTurn], forward for a positive step, at most a few times a second. */
internal fun Modifier.wheelTurns(
  enabled: Boolean,
  onTurn: (Int) -> Unit,
): Modifier = if (!enabled) {
  this
} else {
  pointerInput(onTurn) {
    var last = 0L
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.type != PointerEventType.Scroll) continue
        val change = event.changes.firstOrNull() ?: continue
        val delta = change.scrollDelta.y
        if (delta == 0F) continue
        change.consume()
        if (change.uptimeMillis - last >= WHEEL_TURN_MILLIS) {
          last = change.uptimeMillis
          onTurn(if (delta > 0) 1 else -1)
        }
      }
    }
  }
}

internal const val WHEEL_TURN_MILLIS = 250L

/** Touches that start within [margin] of the left or right edge do nothing, like a palm resting on the screen. */
internal fun Modifier.ignoreEdgeTouches(margin: Dp): Modifier = if (margin <= 0.dp) {
  this
} else {
  pointerInput(margin) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      val edge = margin.toPx()
      if (down.position.x >= edge && down.position.x <= size.width - edge) return@awaitEachGesture
      down.consume()
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) break
      }
    }
  }
}

/** Calls [onTouch] when a finger touches the screen, without taking the touch. */
internal fun Modifier.onAnyTouch(onTouch: () -> Unit): Modifier = pointerInput(onTouch) {
  awaitPointerEventScope {
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      if (event.type == PointerEventType.Press) onTouch()
    }
  }
}

/** Makes the bars and sheets [scale] times as large, and leaves the book's text as it is. */
@Composable
internal fun ScaledControls(
  scale: Float,
  content: @Composable () -> Unit,
) {
  val density = LocalDensity.current
  CompositionLocalProvider(
    LocalDensity provides Density(density.density * scale, density.fontScale),
    content = content,
  )
}

/** The line at the top of the page. */
@Composable
internal fun PageHeader(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium.bookText(),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
      .fillMaxWidth()
      .height(PageNumberHeight)
      .padding(top = 8.dp),
  )
}

/** The chapter, what [ReaderSettings.footer] picks, and the time and battery when they're on. */
@Composable
internal fun PageFooter(
  chapter: String,
  footer: String?,
  settings: ReaderSettings,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = chapter,
      style = MaterialTheme.typography.labelMedium.bookText(),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1F)
        .padding(end = 12.dp),
    )
    if (footer != null) FooterText(footer)
    if (settings.clock) FooterText(clockText(), Modifier.padding(start = 12.dp))
    if (settings.battery) {
      batteryLevel()?.let { FooterText(formatPercent(it), Modifier.padding(start = 12.dp)) }
    }
  }
}

@Composable
private fun RowScope.FooterText(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    modifier = modifier.align(Alignment.CenterVertically),
  )
}

/** The time in the phone's format, which changes every minute. */
@Composable
private fun clockText(): String {
  val context = LocalContext.current
  val now by produceState(System.currentTimeMillis()) {
    while (true) {
      delay(60_000 - System.currentTimeMillis() % 60_000)
      value = System.currentTimeMillis()
    }
  }
  return DateUtils.formatDateTime(context, now, DateUtils.FORMAT_SHOW_TIME)
}

/** From 0 to 1, or null while it's unknown. */
@Composable
private fun batteryLevel(): Float? {
  val context = LocalContext.current
  var level by remember { mutableStateOf<Float?>(null) }
  DisposableEffect(context) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(
        context: Context,
        intent: Intent,
      ) {
        level = intent.batteryFraction()
      }
    }
    val sticky = ContextCompat.registerReceiver(
      context,
      receiver,
      IntentFilter(Intent.ACTION_BATTERY_CHANGED),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    sticky?.batteryFraction()?.let { level = it }
    onDispose { context.unregisterReceiver(receiver) }
  }
  return level
}

private fun Intent.batteryFraction(): Float? {
  val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
  val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
  return if (level < 0 || scale <= 0) null else level.toFloat() / scale
}
