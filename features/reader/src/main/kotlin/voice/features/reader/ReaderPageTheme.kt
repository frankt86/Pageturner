@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import voice.core.data.ReaderSettings
import voice.core.data.ReaderTextAlign
import voice.core.data.ReaderTheme

internal data class PageColors(
  val paper: Color,
  val ink: Color,
  /** The back of a page while it turns. */
  val back: Color,
  /** Behind what's read aloud. */
  val highlight: Color,
  val spine: Color,
)

// paper and ink are what readers know from printed books and other reading apps, so they don't follow the app colors
internal fun ReaderTheme.pageColors(): PageColors? = when (this) {
  ReaderTheme.Auto -> null
  ReaderTheme.Day -> PageColors(
    paper = Color(0xFFFDFCF9),
    ink = Color(0xFF1F1E1C),
    back = Color(0xFFEDEBE6),
    highlight = Color(0xFFFFE7A3),
    spine = Color(0xFFD6D3CC),
  )
  ReaderTheme.Sepia -> PageColors(
    paper = Color(0xFFF4ECD8),
    ink = Color(0xFF4B3B2A),
    back = Color(0xFFE6D9BC),
    highlight = Color(0xFFE9CF8C),
    spine = Color(0xFFCDBD9B),
  )
  ReaderTheme.Night -> PageColors(
    paper = Color(0xFF222326),
    ink = Color(0xFFD5D2CC),
    back = Color(0xFF2E2F33),
    highlight = Color(0xFF51462A),
    spine = Color(0xFF3B3C40),
  )
  ReaderTheme.Black -> PageColors(
    paper = Color.Black,
    ink = Color(0xFFB4B1AB),
    back = Color(0xFF161616),
    highlight = Color(0xFF3D3420),
    spine = Color(0xFF262626),
  )
}

/** Shows the pages in the colors of [theme], the app's controls keep their own. */
@Composable
internal fun ReaderPageTheme(
  theme: ReaderTheme,
  content: @Composable () -> Unit,
) {
  val colors = theme.pageColors()
  val colorScheme = MaterialTheme.colorScheme.let { scheme ->
    if (colors == null) {
      scheme
    } else {
      scheme.copy(
        surface = colors.paper,
        onSurface = colors.ink,
        onSurfaceVariant = colors.ink.copy(alpha = 0.7F),
        surfaceContainer = colors.back,
        primaryContainer = colors.highlight,
        outlineVariant = colors.spine,
      )
    }
  }
  MaterialExpressiveTheme(colorScheme = colorScheme) {
    CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface, content = content)
  }
}

@Composable
internal fun ReaderSettings.pageTextStyle(): TextStyle {
  val base = MaterialTheme.typography.bodyLarge
  return base.copy(
    fontFamily = pageFontFamily(opticalSize = base.fontSize),
    fontSize = textSize.sp,
    lineHeight = (textSize * lineSpacing).sp,
    fontWeight = fontWeight?.let(::FontWeight) ?: base.fontWeight,
    letterSpacing = letterSpacing?.em ?: base.letterSpacing,
    textIndent = paragraphIndent?.let { TextIndent(firstLine = it.em) },
    textAlign = when (textAlign) {
      ReaderTextAlign.Left -> TextAlign.Left
      ReaderTextAlign.Center -> TextAlign.Center
      ReaderTextAlign.Right -> TextAlign.Right
      ReaderTextAlign.Justify -> TextAlign.Justify
      null -> base.textAlign
    },
    fontFeatureSettings = if (ligatures) base.fontFeatureSettings else "\"liga\" 0, \"clig\" 0",
    // the same breaks on screen as when the pages were measured, so no line spills over
    lineBreak = LineBreak.Simple,
    hyphens = if (hyphens) Hyphens.Auto else Hyphens.None,
  )
}

/**
 * The height of each line between two paragraphs, or unspecified to keep the blank line in the text. Laid out apart,
 * the two line breaks between paragraphs make three lines, which together take one line as before.
 */
internal fun ReaderSettings.paragraphGap(): TextUnit {
  if (paragraphSpacing == null && paragraphIndent == null) return TextUnit.Unspecified
  val lines = (paragraphSpacing ?: 1F).coerceAtLeast(0.05F)
  return (textSize * lineSpacing * lines / 3).sp
}
