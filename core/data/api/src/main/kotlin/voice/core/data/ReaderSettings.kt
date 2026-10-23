package voice.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the ebook reader lays out and shows the pages. */
@Serializable
public data class ReaderSettings(
  /** In sp. */
  val textSize: Float = 18F,
  val font: ReaderFont = ReaderFont.Serif,
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
)

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
}
