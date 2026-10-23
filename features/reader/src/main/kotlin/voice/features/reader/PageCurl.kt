package voice.features.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// The curl geometry follows the pagecurl library by Oleksandr Balan (Apache License 2.0),
// https://github.com/oleksandrbalan/pagecurl

/** The line the page is folded along, from where it crosses the top to where it crosses the bottom. */
private data class Fold(
  val top: Offset,
  val bottom: Offset,
)

private val FoldConverter = TwoWayConverter<Fold, AnimationVector4D>(
  convertToVector = { AnimationVector4D(it.top.x, it.top.y, it.bottom.x, it.bottom.y) },
  convertFromVector = { Fold(Offset(it.v1, it.v2), Offset(it.v3, it.v4)) },
)

private fun Size.flat() = Fold(Offset(width, 0F), Offset(width, height))

private fun Size.turned() = Fold(Offset.Zero, Offset(0F, height))

private fun Size.corner() = Fold(Offset(width, height), Offset(width, height))

private fun Size.halfway() = Fold(Offset(width, height / 2), Offset(width / 2, height))

// the fold is where the lifted corner would lie flat: halfway between the corner's place on the page and where it is now
private fun Size.foldFor(
  corner: Offset,
  row: Float,
): Fold {
  val home = Offset(width, row)
  val toHome = home - corner
  val middle = corner + toHome / 2F
  val along = Offset(-toHome.y, toHome.x)
  return Fold(middle - along, middle + along)
}

/**
 * Pages lying on top of each other. Dragging from the right lifts the corner of the page and curls it over,
 * dragging from the left brings the previous page back. A tap on the outer thirds turns a page, one in the middle
 * calls [onCenterTap].
 */
@Composable
internal fun CurlPages(
  count: Int,
  current: Int,
  onTurn: (Int) -> Unit,
  backColor: Color,
  modifier: Modifier = Modifier,
  onCenterTap: () -> Unit = {},
  // previews can't drag, this shows the page curled this far
  previewTurn: Float? = null,
  page: @Composable (index: Int) -> Unit,
) {
  BoxWithConstraints(modifier) {
    val pageSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
    val forward = remember(pageSize) {
      val start = previewTurn?.let {
        pageSize.foldFor(corner = Offset(pageSize.width * (1 - 2 * it), pageSize.height * 0.8F), row = pageSize.height)
      }
      Animatable(start ?: pageSize.flat(), FoldConverter)
    }
    val backward = remember(pageSize) { Animatable(pageSize.turned(), FoldConverter) }
    val latestCurrent by rememberUpdatedState(current)
    val latestOnTurn by rememberUpdatedState(onTurn)
    val latestOnCenterTap by rememberUpdatedState(onCenterTap)
    val scope = rememberCoroutineScope()

    fun turnForward(fromCorner: Boolean) {
      if (latestCurrent >= count - 1) return
      scope.launch {
        val turnFrom = latestCurrent
        forward.animateTo(
          pageSize.turned(),
          if (fromCorner) {
            keyframes {
              durationMillis = TURN_MILLIS
              pageSize.corner() at 0
              pageSize.halfway() at TURN_MILLIS / 3
            }
          } else {
            tween(TURN_MILLIS / 2)
          },
        )
        latestOnTurn(turnFrom + 1)
        forward.snapTo(pageSize.flat())
      }
    }

    fun turnBackward(fromCorner: Boolean) {
      if (latestCurrent <= 0) return
      scope.launch {
        val turnFrom = latestCurrent
        backward.animateTo(
          pageSize.flat(),
          if (fromCorner) {
            keyframes {
              durationMillis = TURN_MILLIS
              pageSize.turned() at 0
              pageSize.halfway() at TURN_MILLIS * 2 / 3
            }
          } else {
            tween(TURN_MILLIS / 2)
          },
        )
        latestOnTurn(turnFrom - 1)
        backward.snapTo(pageSize.turned())
      }
    }

    Box(
      Modifier
        .fillMaxSize()
        .pointerInput(pageSize, count) {
          detectTapGestures { offset ->
            when {
              offset.x > pageSize.width * 2 / 3 -> turnForward(fromCorner = true)
              offset.x < pageSize.width / 3 -> turnBackward(fromCorner = true)
              else -> latestOnCenterTap()
            }
          }
        }
        .pointerInput(pageSize, count) {
          val velocity = VelocityTracker()
          var start = Offset.Zero
          var finger = Offset.Zero
          var lifted: Animatable<Fold, AnimationVector4D>? = null
          detectDragGestures(
            onDragStart = { offset ->
              start = offset
              finger = offset
              velocity.resetTracking()
              lifted = when {
                offset.x >= pageSize.width / 2 && latestCurrent < count - 1 -> forward
                offset.x < pageSize.width / 2 && latestCurrent > 0 -> backward
                else -> null
              }
            },
            onDrag = { change, _ ->
              val fold = lifted ?: return@detectDragGestures
              change.consume()
              finger = change.position
              velocity.addPosition(change.uptimeMillis, finger)
              val moved = finger - start
              // a turned page lies mirrored left of the spine, so its corner starts a page width out and comes back twice as fast
              val corner = if (fold === forward) {
                Offset(pageSize.width, start.y) + moved
              } else {
                Offset(-pageSize.width, start.y) + moved * 2F
              }
              scope.launch { fold.snapTo(pageSize.foldFor(corner, row = start.y)) }
            },
            onDragEnd = {
              val fold = lifted ?: return@detectDragGestures
              lifted = null
              val travelled = finger.x + velocity.calculateVelocity().x * FLING_SECONDS - start.x
              if (fold === forward) {
                if (-travelled > pageSize.width * TURN_DISTANCE) {
                  turnForward(fromCorner = false)
                } else {
                  scope.launch { forward.animateTo(pageSize.flat()) }
                }
              } else {
                if (travelled > pageSize.width * TURN_DISTANCE) {
                  turnBackward(fromCorner = false)
                } else {
                  scope.launch { backward.animateTo(pageSize.turned()) }
                }
              }
            },
            onDragCancel = {
              lifted = null
              scope.launch { forward.animateTo(pageSize.flat()) }
              scope.launch { backward.animateTo(pageSize.turned()) }
            },
          )
        },
    ) {
      // keyed, so a page keeps its state when it comes to lie on top
      for (index in (current + 1) downTo (current - 1)) {
        if (index !in 0 until count) continue
        key(index) {
          val fold = when (index) {
            current -> forward
            current - 1 -> backward
            else -> null
          }
          Box(if (fold == null) Modifier else Modifier.curl(backColor) { fold.value }) {
            page(index)
          }
        }
      }
    }
  }
}

private const val TURN_MILLIS = 450
private const val FLING_SECONDS = 0.15F
private const val TURN_DISTANCE = 0.2F

// the back of the page is the page mirrored, mostly covered by its paper so the print shows through faintly
private const val PRINT_THROUGH = 0.1F

private fun Modifier.curl(
  backColor: Color,
  fold: () -> Fold,
): Modifier = drawWithCache {
  val current = fold()
  if (current == size.turned()) {
    return@drawWithCache onDrawWithContent {}
  }
  if (current == size.flat()) {
    return@drawWithCache onDrawWithContent { drawContent() }
  }
  val topCross = intersection(Offset.Zero, Offset(size.width, 0F), current.top, current.bottom)
  val bottomCross = intersection(Offset(0F, size.height), Offset(size.width, size.height), current.top, current.bottom)
  if (topCross == null || bottomCross == null) {
    return@drawWithCache onDrawWithContent { drawContent() }
  }
  // the fold can't pass the spine, or the page would look torn off
  val top = Offset(max(0F, topCross.x), topCross.y)
  val bottom = Offset(max(0F, bottomCross.x), bottomCross.y)

  val front = Path().apply {
    lineTo(top.x, top.y)
    lineTo(bottom.x, bottom.y)
    lineTo(0F, size.height)
    close()
  }

  val rightCross = intersection(top, bottom, Offset(size.width, 0F), Offset(size.width, size.height))
  // always four corners, switching between three and four makes the shadow jump
  val page = size
  val back = buildList {
    if (top.x < page.width) {
      add(top)
      add(Offset(page.width, top.y))
    } else if (rightCross != null) {
      add(rightCross)
      add(rightCross)
    }
    if (bottom.x < page.width) {
      add(Offset(page.width, page.height))
      add(bottom)
    } else if (rightCross != null) {
      add(rightCross)
      add(rightCross)
    }
  }
  val backPath = back.toPath()
  val line = top - bottom
  val angle = PI.toFloat() - atan2(line.y, line.x) * 2

  val shadowRadius = 15.dp.toPx()
  val shadowOffset = Offset(5.dp.toPx(), 0F).rotate(2 * PI.toFloat() - angle)
  val shadowPaint = android.graphics.Paint().apply {
    color = Color.Transparent.toArgb()
    setShadowLayer(shadowRadius, shadowOffset.x, shadowOffset.y, Color.Black.copy(alpha = 0.25F).toArgb())
  }
  val shadowPath = back.grow(shadowRadius).toPath().asAndroidPath()

  onDrawWithContent {
    clipPath(front) {
      this@onDrawWithContent.drawContent()
    }
    withTransform({
      scale(-1F, 1F, pivot = bottom)
      rotateRad(angle, pivot = bottom)
    }) {
      drawIntoCanvas { it.nativeCanvas.drawPath(shadowPath, shadowPaint) }
      clipPath(backPath) {
        this@onDrawWithContent.drawContent()
        drawRect(backColor.copy(alpha = 1F - PRINT_THROUGH))
      }
    }
  }
}

private fun List<Offset>.toPath() = Path().apply {
  forEachIndexed { index, point ->
    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
  }
  close()
}

// moves every corner outwards, so the shadow reaches past the page edge
private fun List<Offset>.grow(by: Float): List<Offset> {
  fun at(index: Int) = this[(index % size + size) % size]
  val normals = List(size) {
    val edge = at(it + 1) - at(it)
    Offset(edge.y, -edge.x).normalized()
  }
  return mapIndexed { index, point ->
    point + (normals[(index - 1 + size) % size] + normals[index]).normalized() * by
  }
}

private fun Offset.normalized(): Offset {
  val length = getDistance()
  return if (length != 0F) this / length else this
}

private fun Offset.rotate(angle: Float) = Offset(x * cos(angle) - y * sin(angle), x * sin(angle) + y * cos(angle))

private fun intersection(
  a1: Offset,
  a2: Offset,
  b1: Offset,
  b2: Offset,
): Offset? {
  val denominator = (a1.x - a2.x) * (b1.y - b2.y) - (a1.y - a2.y) * (b1.x - b2.x)
  if (denominator == 0F) return null
  val a = a1.x * a2.y - a1.y * a2.x
  val b = b1.x * b2.y - b1.y * b2.x
  return Offset(
    (a * (b1.x - b2.x) - (a1.x - a2.x) * b) / denominator,
    (a * (b1.y - b2.y) - (a1.y - a2.y) * b) / denominator,
  )
}
