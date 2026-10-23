package voice.features.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.createBitmap
import voice.core.data.HighlightColor
import voice.core.data.ReaderImageFilter
import java.util.Locale
import kotlin.random.Random
import voice.core.strings.R as StringsR

/** Reads `#RRGGBB` or `RRGGBB`. */
internal fun parseHexColor(hex: String): Color? {
  val digits = hex.trim().removePrefix("#")
  if (digits.length != 6) return null
  val rgb = digits.toLongOrNull(16) ?: return null
  return Color(0xFF000000 or rgb)
}

internal fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

/** The WCAG contrast ratio, from 1 to 21. */
internal fun contrastRatio(
  a: Color,
  b: Color,
): Float {
  val lighter = maxOf(a.luminance(), b.luminance())
  val darker = minOf(a.luminance(), b.luminance())
  return (lighter + 0.05F) / (darker + 0.05F)
}

/** WCAG AA asks this much for body text. */
internal const val MIN_CONTRAST = 4.5F

@Composable
internal fun highlightColorName(
  color: HighlightColor,
  names: Map<HighlightColor, String>,
): String = names[color]?.takeIf { it.isNotBlank() } ?: highlightColorDefaultName(color)

@Composable
internal fun highlightColorDefaultName(color: HighlightColor): String = stringResource(
  when (color) {
    HighlightColor.Yellow -> StringsR.string.reader_highlight_yellow
    HighlightColor.Green -> StringsR.string.reader_highlight_green
    HighlightColor.Blue -> StringsR.string.reader_highlight_blue
    HighlightColor.Pink -> StringsR.string.reader_highlight_pink
  },
)

// see-through, so the text stays readable on light and dark pages
internal fun HighlightColor.background(opacity: Float): Color = ink().copy(alpha = opacity)

/** How images look on [paper], they only change on dark pages. */
internal fun ReaderImageFilter.colorFilter(paper: Color): ColorFilter? {
  if (paper.luminance() >= 0.5F) return null
  return when (this) {
    ReaderImageFilter.Original -> null
    ReaderImageFilter.Dimmed -> ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.7F, 0.7F, 0.7F, 1F) })
    ReaderImageFilter.Inverted -> ColorFilter.colorMatrix(
      ColorMatrix().apply {
        setToScale(-1F, -1F, -1F, 1F)
        for (row in 0..2) this[row, 4] = 255F
      },
    )
  }
}

private val noise: ImageBitmap by lazy {
  val size = 128
  val random = Random(7)
  val pixels = IntArray(size * size) { (random.nextInt(256) shl 24) or 0xFFFFFF }
  createBitmap(size, size).apply { setPixels(pixels, 0, size, 0, 0, size, size) }.asImageBitmap()
}

/** A fine grain in [color] like the fibers of paper, from 0 for none to 1. */
@Composable
internal fun PaperTexture(
  intensity: Float,
  color: Color,
  modifier: Modifier = Modifier,
) {
  if (intensity <= 0F) return
  val brush = remember { ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)) }
  Canvas(modifier) {
    drawRect(brush = brush, alpha = intensity * 0.18F, colorFilter = ColorFilter.tint(color, BlendMode.SrcIn))
  }
}
