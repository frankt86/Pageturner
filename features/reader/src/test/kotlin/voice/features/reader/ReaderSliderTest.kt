package voice.features.reader

import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.runner.RunWith
import voice.core.ui.VoiceTheme

@RunWith(AndroidJUnit4::class)
class ReaderSliderTest {

  @get:Rule val compose = createComposeRule()

  @Test
  fun `TalkBack on a slider of the display sheet says its name, and a swipe moves it`() {
    lateinit var view: View
    var changedTo: Float? = null
    compose.setContent {
      view = LocalView.current
      VoiceTheme {
        SettingSlider(
          label = "Line spacing",
          value = 1.4F,
          range = 1F..2F,
          onValueChange = { changedTo = it },
        )
      }
    }

    val provider = ViewCompat.getAccessibilityDelegate(view)!!.getAccessibilityNodeProvider(view)!!
    val id =
      compose
        .onNode(
          SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
          useUnmergedTree = true,
        )
        .fetchSemanticsNode()
        .id
    assertEquals("Line spacing", provider.createAccessibilityNodeInfo(id)!!.text.toString())

    provider.performAction(id, AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, null)
    compose.waitForIdle()
    assertNotNull(changedTo)
  }
}
