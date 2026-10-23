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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import voice.core.data.PageTransition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

// a turned page lies on the other side of the spine, which is [gap] left of the page
private fun Size.turned(gap: Float) = Fold(Offset(-gap, 0F), Offset(-gap, height))

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

/** A drag that started outside of [CurlPages], e.g. on the new book engine's web view. */
internal sealed interface CurlDrag {
  /** [forward] lifts the right page, otherwise the previous page comes back. */
  data class Start(
    val position: Offset,
    val forward: Boolean,
  ) : CurlDrag

  data class Move(
    val position: Offset,
    val uptimeMillis: Long,
  ) : CurlDrag

  data object End : CurlDrag

  data object Cancel : CurlDrag
}

private class DragTracking {
  var start = Offset.Zero
  var finger = Offset.Zero
  var lifted: Animatable<Fold, AnimationVector4D>? = null
  val velocity = VelocityTracker()
}

/**
 * Pages lying on top of each other, one at a time or two side by side like an open book. Dragging from the right lifts
 * the corner of the (right) page and curls it over, dragging from the left brings the previous page back. A tap on the
 * outer thirds turns a page, one in the middle calls [onCenterTap]. [onTurn] gets the first page of the new spread, and
 * [onTurnCancel] is called when a drag lets the page fall back. [transition] slides the pages or swaps them at once
 * instead of curling them.
 */
@Composable
internal fun CurlPages(
  count: Int,
  current: Int,
  pagesPerSpread: Int,
  onTurn: (Int) -> Unit,
  backColor: Color,
  modifier: Modifier = Modifier,
  transition: PageTransition = PageTransition.Curl,
  onCenterTap: () -> Unit = {},
  onTurnCancel: () -> Unit = {},
  // each request turns a page forward (positive) or back (negative), like a tap on the edge
  turnRequests: Flow<Int> = emptyFlow(),
  drags: Flow<CurlDrag> = emptyFlow(),
  // previews can't drag, this shows the page curled this far
  previewTurn: Float? = null,
  spineWidth: Dp = 0.dp,
  // a shorter drag lets the page fall back
  minSwipe: Dp = 0.dp,
  spine: @Composable () -> Unit = {},
  page: @Composable (index: Int) -> Unit,
) {
  val spreadCount = (count + pagesPerSpread - 1) / pagesPerSpread
  val spread = current / pagesPerSpread
  BoxWithConstraints(modifier) {
    val twoPages = pagesPerSpread == 2
    val spinePx = if (twoPages) with(LocalDensity.current) { spineWidth.toPx() } else 0F
    val width = constraints.maxWidth.toFloat()
    val pageWidth = if (twoPages) (maxWidth - spineWidth) / 2 else maxWidth
    val rightPageX = maxWidth - pageWidth
    // all folds are measured on the (right) page that turns, the spine lies half its width left of it
    val pageSize = Size(if (twoPages) (width - spinePx) / 2 else width, constraints.maxHeight.toFloat())
    val gap = spinePx / 2
    val pageStart = width - pageSize.width
    val forward = remember(pageSize) {
      val start = previewTurn?.let {
        pageSize.foldFor(corner = Offset(pageSize.width * (1 - 2 * it), pageSize.height * 0.8F), row = pageSize.height)
      }
      Animatable(start ?: pageSize.flat(), FoldConverter)
    }
    val backward = remember(pageSize) { Animatable(pageSize.turned(gap), FoldConverter) }
    val drag = remember(pageSize, spreadCount) { DragTracking() }
    val latestSpread by rememberUpdatedState(spread)
    val latestOnTurn by rememberUpdatedState(onTurn)
    val latestOnTurnCancel by rememberUpdatedState(onTurnCancel)
    val latestOnCenterTap by rememberUpdatedState(onCenterTap)
    val latestTransition by rememberUpdatedState(transition)
    val latestMinSwipe by rememberUpdatedState(with(LocalDensity.current) { minSwipe.toPx() })
    val scope = rememberCoroutineScope()

    fun turnForward(fromCorner: Boolean) {
      if (latestSpread >= spreadCount - 1) return
      scope.launch {
        val turnFrom = latestSpread
        if (latestTransition != PageTransition.None) {
          forward.animateTo(
            pageSize.turned(gap),
            if (fromCorner && latestTransition == PageTransition.Curl) {
              keyframes {
                durationMillis = TURN_MILLIS
                pageSize.corner() at 0
                pageSize.halfway() at TURN_MILLIS / 3
              }
            } else {
              tween(TURN_MILLIS / 2)
            },
          )
        }
        latestOnTurn((turnFrom + 1) * pagesPerSpread)
        forward.snapTo(pageSize.flat())
      }
    }

    fun turnBackward(fromCorner: Boolean) {
      if (latestSpread <= 0) return
      scope.launch {
        val turnFrom = latestSpread
        if (latestTransition != PageTransition.None) {
          backward.animateTo(
            pageSize.flat(),
            if (fromCorner && latestTransition == PageTransition.Curl) {
              keyframes {
                durationMillis = TURN_MILLIS
                pageSize.turned(gap) at 0
                pageSize.halfway() at TURN_MILLIS * 2 / 3
              }
            } else {
              tween(TURN_MILLIS / 2)
            },
          )
        }
        latestOnTurn((turnFrom - 1) * pagesPerSpread)
        backward.snapTo(pageSize.turned(gap))
      }
    }

    fun dragStart(
      offset: Offset,
      forwardHint: Boolean?,
    ) {
      drag.start = offset
      drag.finger = offset
      drag.velocity.resetTracking()
      val liftsForward = forwardHint ?: (offset.x >= width / 2)
      drag.lifted = when {
        liftsForward && latestSpread < spreadCount - 1 -> forward
        !liftsForward && latestSpread > 0 -> backward
        else -> null
      }
    }

    fun dragMove(
      position: Offset,
      uptimeMillis: Long,
    ): Boolean {
      val fold = drag.lifted ?: return false
      drag.finger = position
      drag.velocity.addPosition(uptimeMillis, position)
      // with no animation, the page only turns once the finger lets go
      if (latestTransition == PageTransition.None) return true
      val moved = position - drag.start
      val row = drag.start.y
      // a turned page lies mirrored left of the spine, so its corner starts a page width out and comes back twice as fast
      val corner = if (fold === forward) {
        Offset(pageSize.width, row) + moved
      } else {
        Offset(-pageSize.width - 2 * gap, row) + moved * 2F
      }
      scope.launch { fold.snapTo(pageSize.foldFor(corner, row)) }
      return true
    }

    fun dragEnd() {
      val fold = drag.lifted ?: return
      drag.lifted = null
      val travelled = drag.finger.x + drag.velocity.calculateVelocity().x * FLING_SECONDS - drag.start.x
      val farEnough = abs(drag.finger.x - drag.start.x) >= latestMinSwipe
      if (fold === forward) {
        if (farEnough && -travelled > pageSize.width * TURN_DISTANCE) {
          turnForward(fromCorner = false)
        } else {
          scope.launch {
            forward.animateTo(pageSize.flat())
            latestOnTurnCancel()
          }
        }
      } else {
        if (farEnough && travelled > pageSize.width * TURN_DISTANCE) {
          turnBackward(fromCorner = false)
        } else {
          scope.launch {
            backward.animateTo(pageSize.turned(gap))
            latestOnTurnCancel()
          }
        }
      }
    }

    fun dragCancel() {
      if (drag.lifted == null) return
      drag.lifted = null
      scope.launch {
        forward.animateTo(pageSize.flat())
        backward.animateTo(pageSize.turned(gap))
        latestOnTurnCancel()
      }
    }

    LaunchedEffect(turnRequests, pageSize) {
      turnRequests.collect { step ->
        if (step > 0) turnForward(fromCorner = true) else turnBackward(fromCorner = true)
      }
    }

    LaunchedEffect(drags, pageSize) {
      drags.collect { event ->
        when (event) {
          is CurlDrag.Start -> dragStart(event.position, event.forward)
          is CurlDrag.Move -> {
            val _ = dragMove(event.position, event.uptimeMillis)
          }
          CurlDrag.End -> dragEnd()
          CurlDrag.Cancel -> dragCancel()
        }
      }
    }

    Box(
      Modifier
        .fillMaxSize()
        .pointerInput(pageSize, spreadCount) {
          detectTapGestures { offset ->
            when {
              offset.x > width * 2 / 3 -> turnForward(fromCorner = true)
              offset.x < width / 3 -> turnBackward(fromCorner = true)
              else -> latestOnCenterTap()
            }
          }
        }
        .pointerInput(pageSize, spreadCount) {
          detectDragGestures(
            onDragStart = { offset -> dragStart(offset, forwardHint = null) },
            onDrag = { change, _ ->
              if (dragMove(change.position, change.uptimeMillis)) change.consume()
            },
            onDragEnd = { dragEnd() },
            onDragCancel = { dragCancel() },
          )
        },
    ) {
      if (twoPages) {
        Box(
          Modifier
            .offset(x = pageWidth)
            .width(spineWidth)
            .fillMaxHeight(),
        ) {
          spine()
        }
      }
      val slide = transition == PageTransition.Slide
      for (index in (spread + 1) downTo (spread - 1)) {
        if (index !in 0 until spreadCount) continue
        val fold = when (index) {
          spread -> forward
          spread - 1 -> backward
          else -> null
        }
        val slots = if (slide) {
          // the whole spread slides, nothing curls
          val slid = if (fold == null) Modifier else Modifier.slide(pageSize, gap, width) { fold.value }
          listOfNotNull(
            Placed(page = index * pagesPerSpread, x = 0.dp, modifier = slid),
            if (twoPages) Placed(page = index * 2 + 1, x = rightPageX, modifier = slid) else null,
          )
        } else if (twoPages) {
          listOfNotNull(
            Placed(
              page = index * 2,
              x = 0.dp,
              modifier = if (fold === backward) Modifier.hiddenWhenTurned(gap) { backward.value } else Modifier,
            ),
            Placed(
              page = index * 2 + 1,
              x = rightPageX,
              modifier = if (fold == null) Modifier else Modifier.curl(gap, Offset.Zero, front = true, flap = null) { fold.value },
            ),
            // the back of the turning page is the left page of the spread after it
            fold?.let {
              Placed(
                page = index * 2 + 2,
                x = 0.dp,
                modifier = Modifier.curl(gap, Offset(pageStart, 0F), front = false, flap = Flap(mirrored = true, paper = null)) {
                  it.value
                },
              )
            },
          )
        } else {
          listOf(
            Placed(
              page = index,
              x = 0.dp,
              modifier = if (fold == null) {
                Modifier
              } else {
                Modifier.curl(0F, Offset.Zero, front = true, flap = Flap(mirrored = false, paper = backColor)) { fold.value }
              },
            ),
          )
        }
        // keyed, so a page keeps its state when it comes to lie on top
        key(index) {
          for (slot in slots) {
            Box(
              Modifier
                .offset(x = slot.x)
                .width(pageWidth)
                .fillMaxHeight()
                .then(slot.modifier),
            ) {
              page(slot.page)
            }
          }
        }
      }
    }
  }
}

// moves a whole spread sideways as far as the fold has come over the page
private fun Modifier.slide(
  page: Size,
  gap: Float,
  spreadWidth: Float,
  fold: () -> Fold,
): Modifier = graphicsLayer {
  val current = fold()
  val x = min(current.top.x, current.bottom.x)
  val progress = ((page.width - x) / (page.width + gap)).coerceIn(0F, 1F)
  translationX = -progress * spreadWidth
}

private class Placed(
  val page: Int,
  val x: Dp,
  val modifier: Modifier,
)

private const val TURN_MILLIS = 450
private const val FLING_SECONDS = 0.15F
private const val TURN_DISTANCE = 0.2F

// a single page shows itself mirrored on its back, mostly covered by its paper so the print shows through faintly
private const val PRINT_THROUGH = 0.1F

/**
 * What the back of the turning page shows. A [mirrored] back shows the page on the other side of the spine, a [paper]
 * color covers it.
 */
private class Flap(
  val mirrored: Boolean,
  val paper: Color?,
)

private fun Modifier.hiddenWhenTurned(
  gap: Float,
  fold: () -> Fold,
): Modifier = drawWithContent {
  if (fold() != size.turned(gap)) drawContent()
}

/**
 * Draws a page against the [fold] of the turning page, which lies at [origin] in this page's coordinates. With [front]
 * the page is the turning one and only its part before the fold shows. With a [flap] the page is drawn onto the back of
 * the turning page as it curls over.
 */
private fun Modifier.curl(
  gap: Float,
  origin: Offset,
  front: Boolean,
  flap: Flap?,
  fold: () -> Fold,
): Modifier = drawWithCache {
  val current = fold()
  if (current == size.turned(gap)) {
    return@drawWithCache onDrawWithContent {}
  }
  if (current == size.flat()) {
    return@drawWithCache onDrawWithContent { if (front) drawContent() }
  }
  val topCross = intersection(Offset.Zero, Offset(size.width, 0F), current.top, current.bottom)
  val bottomCross = intersection(Offset(0F, size.height), Offset(size.width, size.height), current.top, current.bottom)
  if (topCross == null || bottomCross == null) {
    return@drawWithCache onDrawWithContent { if (front) drawContent() }
  }
  // the fold can't pass the spine, or the page would look torn off
  val top = Offset(max(-gap, topCross.x), topCross.y)
  val bottom = Offset(max(-gap, bottomCross.x), bottomCross.y)

  val page = size
  val rightCross = intersection(top, bottom, Offset(page.width, 0F), Offset(page.width, page.height))
  // the part of the page past the fold, always four corners: switching between three and four makes the shadow jump
  val lifted = buildList {
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
  val reflection = reflectionAcross(top, bottom)
  val back = lifted.map { reflection.map(it) }
  val middle = (top + bottom) / 2F
  val along = (bottom - top).normalized()
  // points from the fold to the lifted part
  val outwards = Offset(-along.y, along.x).let { if ((lifted.centroid() - middle).dot(it) < 0) -it else it }
  val backDepth = back.maxOf { (middle - it).dot(outwards) }.coerceAtLeast(1F)

  val shading = 24.dp.toPx()
  val shadowRadius = 15.dp.toPx()
  val shadowOffset = outwards * -4.dp.toPx()
  val shadowPaint = android.graphics.Paint().apply {
    color = Color.Transparent.toArgb()
    setShadowLayer(shadowRadius, shadowOffset.x, shadowOffset.y, Color.Black.copy(alpha = 0.3F).toArgb())
  }

  val frontPath = Path().apply {
    moveTo(-gap, 0F)
    lineTo(top.x, top.y)
    lineTo(bottom.x, bottom.y)
    lineTo(-gap, page.height)
    close()
  }
  val liftedPath = lifted.toPath()
  // the page bends away from the light towards the fold
  val frontShade = Brush.linearGradient(
    listOf(Color.Black.copy(alpha = 0.12F), Color.Transparent),
    start = middle,
    end = middle - outwards * shading,
  )
  // the curled page casts a shadow on the page underneath
  val underShadow = Brush.linearGradient(
    listOf(Color.Black.copy(alpha = 0.3F), Color.Transparent),
    start = middle,
    end = middle + outwards * shading,
  )

  val backPath = back.map { it + origin }.toPath()
  val shadowPath = back.grow(shadowRadius).map { it + origin }.toPath().asAndroidPath()
  val backShade = Brush.linearGradient(
    listOf(Color.Black.copy(alpha = 0.18F), Color.Transparent),
    start = middle + origin,
    end = middle + origin - outwards * (backDepth * 0.6F),
  )
  // the back shows this page's own content, moved to the turning page, mirrored to the other side of the spine
  // when it is the next page, and then folded over
  val toBack = android.graphics.Matrix().apply {
    setTranslate(-origin.x, -origin.y)
    if (flap?.mirrored == true) postScale(-1F, 1F, -gap, 0F)
    postConcat(reflection)
    postTranslate(origin.x, origin.y)
  }

  onDrawWithContent {
    if (front) {
      clipPath(frontPath) {
        this@onDrawWithContent.drawContent()
        drawRect(frontShade, topLeft = Offset(-gap, 0F), size = Size(page.width + gap, page.height))
      }
      clipPath(liftedPath) {
        drawRect(underShadow)
      }
    }
    if (flap != null) {
      drawIntoCanvas { it.nativeCanvas.drawPath(shadowPath, shadowPaint) }
      clipPath(backPath) {
        drawIntoCanvas {
          it.save()
          it.nativeCanvas.concat(toBack)
        }
        this@onDrawWithContent.drawContent()
        drawIntoCanvas { it.restore() }
        flap.paper?.let {
          drawRect(it.copy(alpha = 1F - PRINT_THROUGH), topLeft = -Offset(page.width * 3, 0F), size = Size(page.width * 6, page.height))
        }
        drawRect(backShade, topLeft = -Offset(page.width * 3, 0F), size = Size(page.width * 6, page.height))
      }
    }
  }
}

// the reflection across the line through [a] and [b]
private fun reflectionAcross(
  a: Offset,
  b: Offset,
): android.graphics.Matrix {
  val d = (b - a).normalized()
  val xx = 2 * d.x * d.x - 1
  val xy = 2 * d.x * d.y
  val yy = 2 * d.y * d.y - 1
  return android.graphics.Matrix().apply {
    setValues(
      floatArrayOf(
        xx, xy, a.x - (xx * a.x + xy * a.y),
        xy, yy, a.y - (xy * a.x + yy * a.y),
        0F, 0F, 1F,
      ),
    )
  }
}

private fun android.graphics.Matrix.map(point: Offset): Offset {
  val points = floatArrayOf(point.x, point.y)
  mapPoints(points)
  return Offset(points[0], points[1])
}

private fun List<Offset>.centroid() = Offset(sumOf { it.x.toDouble() }.toFloat() / size, sumOf { it.y.toDouble() }.toFloat() / size)

private fun Offset.dot(other: Offset) = x * other.x + y * other.y

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
