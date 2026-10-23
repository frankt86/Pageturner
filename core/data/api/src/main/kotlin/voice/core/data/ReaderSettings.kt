package voice.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the ebook reader lays out and shows the pages. */
@Serializable
public data class ReaderSettings(
  /** In sp. */
  val textSize: Float = 18F,
  val font: ReaderFont = ReaderFont.Serif,
  /** The file name of a font the reader added, in the app's fonts folder. It's used instead of [font]. */
  val customFont: String? = null,
  /** The line height as a multiple of the text size. */
  val lineSpacing: Float = 1.55F,
  /** The space left and right of the text, in dp. */
  val margin: Int = 24,
  val theme: ReaderTheme = ReaderTheme.Auto,
  /** The screen brightness while reading, from 0 to 1, or null to leave it to the phone. */
  val brightness: Float? = null,
  val volumeKeysTurnPages: Boolean = true,
  /** Shows EPUBs with Readium, which keeps the book's own layout, instead of as plain text. */
  val newBookEngine: Boolean = false,
  val pageTransition: PageTransition = PageTransition.Curl,
  val pages: ReaderPages = ReaderPages.Auto,
  /** The space between two pages side by side, in dp, or null for the reader's own. */
  val pageGap: Int? = null,
  /** The space above the text, in dp, or null for the reader's own. */
  val topMargin: Int? = null,
  /** The space below the text, in dp, or null for the reader's own. */
  val bottomMargin: Int? = null,
  /** Keeps lines to about 65 characters on wide screens. */
  val shortLines: Boolean = false,
  val orientation: ReaderOrientation = ReaderOrientation.Auto,
  val progressBar: ProgressBarStyle = ProgressBarStyle.Bar,
  val progressVisibility: ProgressVisibility = ProgressVisibility.OnTap,
  /** Shows the chapters, bookmarks and highlights along the page bar. */
  val progressMarks: Boolean = false,
  /** Counts the book's progress over its main text only, without front and back matter like the title page or index. */
  val progressBodyOnly: Boolean = false,
  /** What the line at the bottom of the page shows next to the chapter. */
  val footer: ReaderFooter = ReaderFooter.Page,
  /** Keeps the book's own fonts and formatting in the new book engine, only the text size and colors change. */
  val publisherStyles: Boolean = false,
  /** From 100 to 900, or null for the font's regular weight. */
  val fontWeight: Int? = null,
  /** In em, or null to leave it to the font and the book. */
  val letterSpacing: Float? = null,
  /** In em, or null to leave it to the book. Only the new book engine has it. */
  val wordSpacing: Float? = null,
  /** As a multiple of the line height, or null for one blank line in the old engine and the book's own in the new one. */
  val paragraphSpacing: Float? = null,
  /** The indent of a paragraph's first line in em, or null for none in the old engine and the book's own in the new one. */
  val paragraphIndent: Float? = null,
  /** Null for the book's own, which is the start of the line in the old engine. */
  val textAlign: ReaderTextAlign? = null,
  val hyphens: Boolean = false,
  val ligatures: Boolean = true,
  /** The colors of [ReaderTheme.Custom]. */
  val customColors: ReaderColors = ReaderColors(),
  val savedThemes: List<ReaderColors> = emptyList(),
  /** The themes of [ReaderTheme.Scheduled], neither Auto nor Scheduled. */
  val dayTheme: ReaderTheme = ReaderTheme.Day,
  val nightTheme: ReaderTheme = ReaderTheme.Night,
  /** In minutes after midnight. */
  val nightStart: Int = 21 * 60,
  val nightEnd: Int = 7 * 60,
  /** The names the reader gave the highlight colors, the others keep their color's name. */
  val highlightNames: Map<HighlightColor, String> = emptyMap(),
  val highlightOpacity: Float = 0.45F,
  /** How images and PDF pages look on dark pages. */
  val imageFilter: ReaderImageFilter = ReaderImageFilter.Original,
  /** From 0 for none to 1. */
  val paperTexture: Float = 0F,
  val readingMode: ReadingMode = ReadingMode.Pages,
  /** How the volume keys, taps and swipes move the text in [ReadingMode.Scroll]. */
  val scrollBehavior: ScrollBehavior = ScrollBehavior.Smooth,
  /** How fast automatic scrolling moves the text, in dp per second. */
  val autoScrollSpeed: Int = 20,
  /** The seconds between automatic page turns. */
  val autoTurnSeconds: Int = 30,
) {
  /** These settings with the text looking like it does at first. */
  public fun withDefaultTypography(): ReaderSettings {
    val defaults = ReaderSettings()
    return copy(
      textSize = defaults.textSize,
      font = defaults.font,
      customFont = defaults.customFont,
      lineSpacing = defaults.lineSpacing,
      publisherStyles = defaults.publisherStyles,
      fontWeight = defaults.fontWeight,
      letterSpacing = defaults.letterSpacing,
      wordSpacing = defaults.wordSpacing,
      paragraphSpacing = defaults.paragraphSpacing,
      paragraphIndent = defaults.paragraphIndent,
      textAlign = defaults.textAlign,
      hyphens = defaults.hyphens,
      ligatures = defaults.ligatures,
    )
  }
}

@Serializable
public enum class ReaderTextAlign {
  @SerialName("left")
  Left,

  @SerialName("center")
  Center,

  @SerialName("right")
  Right,

  @SerialName("justify")
  Justify,
}

@Serializable
public enum class ProgressBarStyle {
  @SerialName("line")
  Line,

  @SerialName("bar")
  Bar,

  /** One segment per chapter. */
  @SerialName("segmented")
  Segmented,
}

@Serializable
public enum class ProgressVisibility {
  /** A thin line at the bottom of the page, and the page bar on a tap. */
  @SerialName("always")
  Always,

  /** The page bar on a tap. */
  @SerialName("on_tap")
  OnTap,

  @SerialName("hidden")
  Hidden,
}

@Serializable
public enum class ReaderFooter {
  /** The page of the book. */
  @SerialName("page")
  Page,

  /** The page of the chapter. */
  @SerialName("chapter_page")
  ChapterPage,

  @SerialName("percent")
  Percent,

  /** The reading time left in the chapter. */
  @SerialName("time_left")
  TimeLeft,
}

/** One page or two side by side. */
@Serializable
public enum class ReaderPages {
  /** Two pages when the screen is wide. */
  @SerialName("auto")
  Auto,

  @SerialName("one")
  One,

  @SerialName("two")
  Two,
}

@Serializable
public enum class ReadingMode {
  @SerialName("pages")
  Pages,

  /** The text runs on from top to bottom. */
  @SerialName("scroll")
  Scroll,
}

@Serializable
public enum class ScrollBehavior {
  @SerialName("smooth")
  Smooth,

  /** The volume keys and taps move the text one screen at once, without an animation. */
  @SerialName("stepped")
  Stepped,

  /** A swipe moves the text one screen. */
  @SerialName("snap")
  Snap,
}

@Serializable
public enum class ReaderOrientation {
  @SerialName("auto")
  Auto,

  @SerialName("portrait")
  Portrait,

  @SerialName("landscape")
  Landscape,
}

@Serializable
public enum class PageTransition {
  @SerialName("curl")
  Curl,

  @SerialName("slide")
  Slide,

  @SerialName("none")
  None,
}

@Serializable
public enum class ReaderFont {
  @SerialName("serif")
  Serif,

  @SerialName("sans")
  Sans,

  @SerialName("monospace")
  Monospace,

  @SerialName("literata")
  Literata,

  @SerialName("atkinson_hyperlegible")
  AtkinsonHyperlegible,

  @SerialName("open_dyslexic")
  OpenDyslexic,
}

@Serializable
public enum class ReaderTheme {
  /** The app's own colors. */
  @SerialName("auto")
  Auto,

  @SerialName("day")
  Day,

  @SerialName("sepia")
  Sepia,

  @SerialName("night")
  Night,

  @SerialName("black")
  Black,

  @SerialName("soft_paper")
  SoftPaper,

  @SerialName("slate")
  Slate,

  /** [ReaderSettings.customColors]. */
  @SerialName("custom")
  Custom,

  /** [ReaderSettings.dayTheme] by day and [ReaderSettings.nightTheme] at night. */
  @SerialName("scheduled")
  Scheduled,
}

/** Colors as #RRGGBB. Without a link or selection color, the theme's own are used. */
@Serializable
public data class ReaderColors(
  val name: String = "",
  val background: String = "#FDFCF9",
  val text: String = "#1F1E1C",
  val link: String? = null,
  val selection: String? = null,
)

@Serializable
public enum class ReaderImageFilter {
  @SerialName("original")
  Original,

  @SerialName("dimmed")
  Dimmed,

  @SerialName("inverted")
  Inverted,
}
