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
)

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
