package voice.features.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import kotlin.math.min

private const val MAX_ZOOM = 5F

/**
 * A PDF page that zooms with two fingers and pans with one while zoomed. A double tap switches between the whole page
 * and its full width. While not zoomed, drags and taps go on to turn the pages.
 */
@Composable
internal fun ZoomablePdfPage(
  pdf: PdfFile,
  index: Int,
  contentDescription: String,
  colorFilter: ColorFilter?,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
    var scale by remember(index) { mutableFloatStateOf(1F) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }
    // zoomed in, the page is drawn at twice the size so the text stays sharp
    val detail = if (scale > 1.5F) 2 else 1
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val bitmap by produceState<ImageBitmap?>(null, pdf, index, width, height, detail) {
      value = pdf.render(index, width * detail, height * detail)?.asImageBitmap()
    }
    val image = bitmap ?: return@BoxWithConstraints
    val latestImage by rememberUpdatedState(image)
    fun clamp(target: Offset): Offset {
      val maxX = (scale - 1) * width / 2
      val maxY = (scale - 1) * height / 2
      return Offset(target.x.coerceIn(-maxX, maxX), target.y.coerceIn(-maxY, maxY))
    }
    Image(
      bitmap = image,
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      colorFilter = colorFilter,
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          translationX = offset.x
          translationY = offset.y
        }
        .pointerInput(index, width, height) {
          var lastTapMillis = 0L
          var lastTap = Offset.Zero
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var moved = false
            var multiTouch = false
            do {
              val event = awaitPointerEvent()
              val fingers = event.changes.count { it.pressed }
              if (fingers > 1) multiTouch = true
              if (event.changes.any { it.positionChanged() }) moved = true
              if (fingers > 1 || scale > 1F) {
                scale = (scale * event.calculateZoom()).coerceIn(1F, MAX_ZOOM)
                offset = if (scale == 1F) Offset.Zero else clamp(offset + event.calculatePan())
                event.changes.forEach { if (it.positionChanged()) it.consume() }
              }
            } while (event.changes.any { it.pressed })
            if (!moved && !multiTouch) {
              val now = down.uptimeMillis
              val near = (down.position - lastTap).getDistance() < viewConfiguration.touchSlop * 4
              if (now - lastTapMillis < viewConfiguration.doubleTapTimeoutMillis && near) {
                if (scale > 1F) {
                  scale = 1F
                  offset = Offset.Zero
                } else {
                  // a page narrower than the screen fills its width, a wide one doubles
                  val shownWidth = min(width.toFloat(), height * latestImage.width.toFloat() / latestImage.height)
                  scale = (width / shownWidth).takeIf { it > 1.05F } ?: 2F
                  // starts at the top of the page
                  offset = clamp(Offset(0F, Float.MAX_VALUE))
                }
                lastTapMillis = 0L
              } else {
                lastTapMillis = now
                lastTap = down.position
              }
            }
          }
        },
    )
  }
}
