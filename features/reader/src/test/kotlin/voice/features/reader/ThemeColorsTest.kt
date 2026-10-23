package voice.features.reader

import androidx.compose.ui.graphics.Color
import voice.core.data.ReaderColors
import voice.core.data.ReaderSettings
import voice.core.data.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeColorsTest {

  @Test
  fun `hex codes are read with or without the hash`() {
    assertEquals(Color(0xFF2B3440), parseHexColor("#2B3440"))
    assertEquals(Color(0xFF2B3440), parseHexColor("2b3440"))
    assertNull(parseHexColor("#2B34"))
    assertNull(parseHexColor("#GGGGGG"))
  }

  @Test
  fun `colors are written as hex codes`() {
    assertEquals("#2B3440", Color(0xFF2B3440).toHex())
  }

  @Test
  fun `black on white has the highest contrast`() {
    assertEquals(21F, contrastRatio(Color.Black, Color.White), 0.01F)
    assertEquals(1F, contrastRatio(Color.Gray, Color.Gray), 0.01F)
  }

  @Test
  fun `the presets are easy to read`() {
    listOf(ReaderTheme.SoftPaper, ReaderTheme.Slate).forEach { theme ->
      val colors = checkNotNull(theme.pageColors())
      assertTrue(contrastRatio(colors.paper, colors.ink) >= MIN_CONTRAST)
    }
  }

  @Test
  fun `the night can run past midnight`() {
    val settings = ReaderSettings(nightStart = 21 * 60, nightEnd = 7 * 60)
    assertTrue(settings.isNight(23 * 60))
    assertTrue(settings.isNight(3 * 60))
    assertFalse(settings.isNight(7 * 60))
    assertFalse(settings.isNight(12 * 60))
  }

  @Test
  fun `the night can be within one day`() {
    val settings = ReaderSettings(nightStart = 1 * 60, nightEnd = 5 * 60)
    assertTrue(settings.isNight(2 * 60))
    assertFalse(settings.isNight(23 * 60))
  }

  @Test
  fun `saved themes survive an export and import`() {
    val themes = listOf(
      ReaderColors(name = "Evening", background = "#2B3440", text = "#CDD3DA", link = "#8AB4F8"),
      ReaderColors(name = "Paper", background = "#F5F3EE", text = "#3B3B3B"),
    )
    assertEquals(themes, decodeThemes(encodeThemes(themes)))
  }

  @Test
  fun `themes with broken colors or files without themes are left out`() {
    assertEquals(emptyList<ReaderColors>(), decodeThemes("""[{"name":"Odd","background":"red","text":"#000000"}]"""))
    assertEquals(emptyList<ReaderColors>(), decodeThemes("not a themes file"))
  }
}
