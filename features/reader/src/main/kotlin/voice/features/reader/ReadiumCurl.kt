@file:OptIn(ExperimentalReadiumApi::class)

package voice.features.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.createBitmap
import androidx.core.view.drawToBitmap
import androidx.fragment.app.FragmentContainerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import voice.core.data.PageTransition
import kotlin.coroutines.resume
import kotlin.math.abs

/** A page turn of the new book engine shown with the curl: pictures of the page before the turn and, going back, after it. */
internal class CurlTurn(
  val before: Bitmap,
  val after: Bitmap?,
  val forward: Boolean,
  val taps: Flow<Int>,
  val drags: Flow<CurlDrag>,
)

/**
 * Turns the pages of the new book engine. Readium can only slide them, so for the curl the page is captured as a
 * picture, Readium turns at once underneath, and the picture curls away above it.
 */
@Stable
internal class ReadiumTurns(private val scope: CoroutineScope) {

  var navigator: EpubNavigatorFragment? = null
  var view: View? = null
  var window: Window? = null
  var transition: PageTransition = PageTransition.Curl

  /** The page before the turn, shown while the next one gets ready underneath. */
  var cover by mutableStateOf<Bitmap?>(null)
    private set
  var curl by mutableStateOf<CurlTurn?>(null)
    private set

  private var busy = false
  private var drags: Channel<CurlDrag>? = null
  private var dragForward = false

  /** Turns forward for a positive [step] and back for a negative one, like the volume keys. */
  fun turn(step: Int) {
    val navigator = navigator ?: return
    if (transition == PageTransition.Curl && !navigator.rightToLeft) {
      curlTurn(step, drags = null)
    } else {
      plainTurn(navigator, step)
    }
  }

  /** Turns to the page on the [right] or the left, like a tap on that side. */
  fun turnTowards(right: Boolean) {
    val navigator = navigator ?: return
    turn(if (right != navigator.rightToLeft) 1 else -1)
  }

  fun onDrag(event: CurlDrag) {
    val navigator = navigator ?: return
    if (transition != PageTransition.Curl || navigator.rightToLeft) {
      when (event) {
        is CurlDrag.Start -> dragForward = event.forward != navigator.rightToLeft
        CurlDrag.End -> plainTurn(navigator, if (dragForward) 1 else -1)
        else -> Unit
      }
      return
    }
    when (event) {
      is CurlDrag.Start -> {
        if (busy) {
          drags = null
          return
        }
        val channel = Channel<CurlDrag>(Channel.UNLIMITED)
        val _ = channel.trySend(event)
        drags = channel
        curlTurn(if (event.forward) 1 else -1, channel)
      }
      else -> {
        val _ = drags?.trySend(event)
      }
    }
  }

  fun onCurlDone() {
    curl = null
    drags = null
    busy = false
  }

  /** The drag let the page fall back, so Readium goes back to it before the picture goes away. */
  fun onCurlCancel() {
    val turn = curl ?: return
    val navigator = navigator ?: return onCurlDone()
    scope.launch {
      val before = navigator.currentLocator.value
      val _ = if (turn.forward) navigator.goBackward(animated = false) else navigator.goForward(animated = false)
      awaitPage(navigator, before)
      onCurlDone()
    }
  }

  private fun plainTurn(
    navigator: EpubNavigatorFragment,
    step: Int,
  ) {
    val animated = transition == PageTransition.Slide
    val _ = if (step > 0) navigator.goForward(animated) else navigator.goBackward(animated)
  }

  private fun curlTurn(
    step: Int,
    drags: Channel<CurlDrag>?,
  ) {
    val navigator = navigator ?: return
    val view = view ?: return
    val window = window ?: return
    if (busy) return
    busy = true
    scope.launch {
      val before = capture(view, window)
      if (before == null) {
        plainTurn(navigator, step)
        onCurlDone()
        return@launch
      }
      cover = before
      val from = navigator.currentLocator.value
      val moved = if (step > 0) navigator.goForward(animated = false) else navigator.goBackward(animated = false)
      if (!moved) {
        cover = null
        onCurlDone()
        return@launch
      }
      awaitPage(navigator, from)
      // going back, the page that comes back curls over the one from before
      val after = if (step < 0) view.drawToBitmap() else null
      curl = CurlTurn(
        before = before,
        after = after,
        forward = step > 0,
        taps = if (drags == null) flowOf(step) else emptyFlow(),
        drags = drags?.receiveAsFlow() ?: emptyFlow(),
      )
      cover = null
    }
  }
}

private val EpubNavigatorFragment.rightToLeft: Boolean
  get() = overflow.value.readingProgression == ReadingProgression.RTL

private suspend fun awaitPage(
  navigator: EpubNavigatorFragment,
  before: Locator,
) {
  val _ = withTimeoutOrNull(1_500) { navigator.currentLocator.first { it != before } }
  // the web view draws the new page in the next frames
  repeat(2) { withFrameNanos { } }
}

private suspend fun capture(
  view: View,
  window: Window,
): Bitmap? {
  if (view.width == 0 || view.height == 0) return null
  val bitmap = createBitmap(view.width, view.height)
  val location = IntArray(2)
  view.getLocationInWindow(location)
  val area = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
  return suspendCancellableCoroutine { continuation ->
    PixelCopy.request(
      window,
      area,
      bitmap,
      { result -> continuation.resume(bitmap.takeIf { result == PixelCopy.SUCCESS }) },
      Handler(Looper.getMainLooper()),
    )
  }
}

/** The page before the turn, while the next one gets ready underneath. */
@Composable
internal fun TurnCover(
  bitmap: Bitmap,
  modifier: Modifier = Modifier,
) {
  val image = remember(bitmap) { bitmap.asImageBitmap() }
  Image(bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = modifier)
}

@Composable
internal fun CurlOverlay(
  turn: CurlTurn,
  backColor: Color,
  onDone: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val before = remember(turn) { turn.before.asImageBitmap() }
  val after = remember(turn) { turn.after?.asImageBitmap() }
  CurlPages(
    count = 2,
    current = if (turn.forward) 0 else 1,
    pagesPerSpread = 1,
    onTurn = { onDone() },
    onTurnCancel = onCancel,
    backColor = backColor,
    turnRequests = turn.taps,
    drags = turn.drags,
    modifier = modifier,
  ) { index ->
    // going forward, the next page is the book itself underneath
    val image = when {
      turn.forward -> before.takeIf { index == 0 }
      index == 0 -> after
      else -> before
    }
    if (image != null) {
      Image(bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
    }
  }
}

/**
 * Holds the book's pages. Takes the volume keys also while the web view has the focus, e.g. after selecting text, and,
 * with [interceptDrags], sideways drags before Readium slides the page, so they can curl it instead.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class NavigatorContainer(context: Context) : FrameLayout(context) {

  /** Called with 1 or -1 when a volume key goes down and 0 when it goes up, returns whether it took the key. */
  var onVolumeKey: (step: Int) -> Boolean = { false }
  var interceptDrags = false
  var onDrag: (CurlDrag) -> Unit = {}

  /** Called once per touch when the finger moves. */
  var onFingerMove: () -> Unit = {}
  var fingerDown by mutableStateOf(false)
    private set

  val fragments = FragmentContainerView(context).apply { id = View.generateViewId() }

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private var down = Offset.Zero
  private var dragging = false
  private var touchStart = Offset.Zero
  private var fingerMoved = false

  init {
    addView(fragments)
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        fingerDown = true
        fingerMoved = false
        touchStart = Offset(event.x, event.y)
      }
      MotionEvent.ACTION_MOVE -> if (!fingerMoved && (Offset(event.x, event.y) - touchStart).getDistance() > touchSlop) {
        fingerMoved = true
        onFingerMove()
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> fingerDown = false
    }
    return super.dispatchTouchEvent(event)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val step = when (event.keyCode) {
      KeyEvent.KEYCODE_VOLUME_DOWN -> 1
      KeyEvent.KEYCODE_VOLUME_UP -> -1
      else -> return super.dispatchKeyEvent(event)
    }
    // the key up too, or the phone shows its volume panel
    return onVolumeKey(if (event.action == KeyEvent.ACTION_DOWN) step else 0) || super.dispatchKeyEvent(event)
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (!interceptDrags) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        down = Offset(event.x, event.y)
        dragging = false
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = event.x - down.x
        val dy = event.y - down.y
        // a drag after a long press selects text
        val beforeLongPress = event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()
        if (beforeLongPress && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.5F) {
          dragging = true
          onDrag(CurlDrag.Start(down, forward = dx < 0))
          onDrag(CurlDrag.Move(Offset(event.x, event.y), event.eventTime))
          return true
        }
      }
    }
    return false
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!dragging) return super.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_MOVE -> onDrag(CurlDrag.Move(Offset(event.x, event.y), event.eventTime))
      MotionEvent.ACTION_UP -> {
        dragging = false
        onDrag(CurlDrag.End)
      }
      MotionEvent.ACTION_CANCEL -> {
        dragging = false
        onDrag(CurlDrag.Cancel)
      }
    }
    return true
  }
}
