package voice.features.reader

import android.view.KeyEvent
import voice.core.data.ReaderAction
import voice.core.data.ReaderSettings
import voice.core.data.TapAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ControlsTest {

  @Test
  fun `the thirds of the page turn back, show the bars and turn forward`() {
    val settings = ReaderSettings()
    assertEquals(TapAction.Previous, settings.tapActionAt(0.1F, rightToLeft = false))
    assertEquals(TapAction.Bars, settings.tapActionAt(0.5F, rightToLeft = false))
    assertEquals(TapAction.Next, settings.tapActionAt(0.9F, rightToLeft = false))
  }

  @Test
  fun `left-handed and right-to-left books swap the sides`() {
    val leftHanded = ReaderSettings(leftHanded = true)
    assertEquals(TapAction.Next, leftHanded.tapActionAt(0.1F, rightToLeft = false))
    assertEquals(TapAction.Previous, leftHanded.tapActionAt(0.9F, rightToLeft = false))
    assertEquals(TapAction.Next, ReaderSettings().tapActionAt(0.1F, rightToLeft = true))
    assertEquals(TapAction.Previous, leftHanded.tapActionAt(0.1F, rightToLeft = true))
  }

  @Test
  fun `page keys, arrows and space turn pages`() {
    fun key(
      code: Int,
      shift: Boolean = false,
      rightToLeft: Boolean = false,
    ) = keyAction(code, ctrl = false, shift = shift, volumeKeys = true, rightToLeft = rightToLeft)
    assertEquals(KeyAction.Next, key(KeyEvent.KEYCODE_PAGE_DOWN))
    assertEquals(KeyAction.Previous, key(KeyEvent.KEYCODE_PAGE_UP))
    assertEquals(KeyAction.Next, key(KeyEvent.KEYCODE_DPAD_RIGHT))
    assertEquals(KeyAction.Previous, key(KeyEvent.KEYCODE_DPAD_RIGHT, rightToLeft = true))
    assertEquals(KeyAction.Next, key(KeyEvent.KEYCODE_SPACE))
    assertEquals(KeyAction.Previous, key(KeyEvent.KEYCODE_SPACE, shift = true))
    assertEquals(KeyAction.Close, key(KeyEvent.KEYCODE_ESCAPE))
  }

  @Test
  fun `the volume keys only turn pages when they should`() {
    assertEquals(
      KeyAction.Next,
      keyAction(KeyEvent.KEYCODE_VOLUME_DOWN, ctrl = false, shift = false, volumeKeys = true, rightToLeft = false),
    )
    assertNull(keyAction(KeyEvent.KEYCODE_VOLUME_DOWN, ctrl = false, shift = false, volumeKeys = false, rightToLeft = false))
  }

  @Test
  fun `ctrl shortcuts search, bookmark and open the display settings`() {
    fun ctrl(code: Int) = keyAction(code, ctrl = true, shift = false, volumeKeys = true, rightToLeft = false)
    assertEquals(KeyAction.Search, ctrl(KeyEvent.KEYCODE_F))
    assertEquals(KeyAction.Bookmark, ctrl(KeyEvent.KEYCODE_B))
    assertEquals(KeyAction.Display, ctrl(KeyEvent.KEYCODE_D))
    assertNull(ctrl(KeyEvent.KEYCODE_SPACE))
  }

  @Test
  fun `the top bar keeps the display button and adds new buttons at the end`() {
    val settings = ReaderSettings(
      toolbarOrder = listOf(ReaderAction.ReadAloud, ReaderAction.Display),
      hiddenActions = setOf(ReaderAction.Display, ReaderAction.Search),
    )
    assertEquals(
      listOf(ReaderAction.ReadAloud, ReaderAction.Display, ReaderAction.Bookmark, ReaderAction.Contents, ReaderAction.HandsFree),
      settings.toolbarActions(),
    )
  }
}
