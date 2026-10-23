@file:OptIn(ExperimentalMaterial3Api::class)

package voice.features.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import voice.core.data.DoubleTapAction
import voice.core.data.MouseWheel
import voice.core.data.ReaderAction
import voice.core.data.ReaderSettings
import voice.core.data.ScreenAwake
import voice.core.data.TapAction
import voice.core.ui.VoiceTheme
import voice.core.ui.icons.VoiceIcons
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

/** How taps, keys, the mouse wheel and the buttons work in the reader, and what keeps them from going off by mistake. */
@Composable
internal fun ControlsSheet(
  scale: Float,
  settings: ReaderSettings,
  onSettingsChange: (ReaderSettings) -> Unit,
  onDismiss: () -> Unit,
) {
  // no scrim, so the bars show each change while it's made
  ModalBottomSheet(onDismissRequest = onDismiss, scrimColor = Color.Transparent) {
    ScaledControls(scale) {
      ControlsSettings(settings = settings, onSettingsChange = onSettingsChange)
    }
  }
}

@Composable
private fun ControlsSettings(
  settings: ReaderSettings,
  onSettingsChange: (ReaderSettings) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp)
      .padding(bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = stringResource(StringsR.string.reader_controls),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.semantics { heading() },
    )
    TapSettings(settings, onSettingsChange)
    KeySettings(settings, onSettingsChange)
    ToolbarSettings(settings, onSettingsChange)
    ScreenSettings(settings, onSettingsChange)
    ProtectionSettings(settings, onSettingsChange)
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier
      .padding(top = 8.dp)
      .semantics { heading() },
  )
}

@Composable
private fun TapSettings(
  settings: ReaderSettings,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SectionTitle(stringResource(StringsR.string.reader_taps))
    TapChoice(StringsR.string.reader_tap_left, settings.tapLeft) { onSettingsChange(settings.copy(tapLeft = it)) }
    TapChoice(StringsR.string.reader_tap_middle, settings.tapMiddle) { onSettingsChange(settings.copy(tapMiddle = it)) }
    TapChoice(StringsR.string.reader_tap_right, settings.tapRight) { onSettingsChange(settings.copy(tapRight = it)) }
    SwitchRow(
      label = stringResource(StringsR.string.reader_left_handed),
      summary = stringResource(StringsR.string.reader_left_handed_summary),
      checked = settings.leftHanded,
      onCheckedChange = { onSettingsChange(settings.copy(leftHanded = it)) },
    )
    ChipChoice(
      label = stringResource(StringsR.string.reader_double_tap),
      options = DoubleTapAction.entries,
      selected = settings.doubleTap,
      optionLabel = {
        stringResource(
          when (it) {
            DoubleTapAction.Nothing -> StringsR.string.reader_nothing
            DoubleTapAction.Bars -> StringsR.string.reader_tap_bars
            DoubleTapAction.Bookmark -> StringsR.string.reader_tap_bookmark
          },
        )
      },
      onSelect = { onSettingsChange(settings.copy(doubleTap = it)) },
    )
  }
}

@Composable
private fun TapChoice(
  label: Int,
  selected: TapAction,
  onSelect: (TapAction) -> Unit,
) {
  ChipChoice(
    label = stringResource(label),
    options = TapAction.entries,
    selected = selected,
    optionLabel = {
      stringResource(
        when (it) {
          TapAction.Previous -> StringsR.string.reader_tap_previous
          TapAction.Next -> StringsR.string.reader_tap_next
          TapAction.Bars -> StringsR.string.reader_tap_bars
          TapAction.Nothing -> StringsR.string.reader_nothing
        },
      )
    },
    onSelect = onSelect,
  )
}

@Composable
private fun KeySettings(
  settings: ReaderSettings,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SectionTitle(stringResource(StringsR.string.reader_keys))
    SwitchRow(
      label = stringResource(StringsR.string.reader_volume_keys),
      checked = settings.volumeKeysTurnPages,
      onCheckedChange = { onSettingsChange(settings.copy(volumeKeysTurnPages = it)) },
    )
    Text(
      text = stringResource(StringsR.string.reader_keys_summary),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OptionChoice(
      label = stringResource(StringsR.string.reader_mouse_wheel),
      options = MouseWheel.entries,
      selected = settings.mouseWheel,
      optionLabel = {
        stringResource(
          when (it) {
            MouseWheel.Scroll -> StringsR.string.reader_reading_mode_scroll
            MouseWheel.Turn -> StringsR.string.reader_mouse_wheel_turn
          },
        )
      },
      onSelect = { onSettingsChange(settings.copy(mouseWheel = it)) },
    )
  }
}

@Composable
private fun ToolbarSettings(
  settings: ReaderSettings,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    SectionTitle(stringResource(StringsR.string.reader_buttons))
    val order = settings.orderedActions()
    order.forEachIndexed { index, action ->
      // the display button stays, it's the way back here
      val required = action == ReaderAction.Display
      val shown = required || action !in settings.hiddenActions
      fun move(by: Int) {
        val moved = order.toMutableList()
        moved.add(index + by, moved.removeAt(index))
        onSettingsChange(settings.copy(toolbarOrder = moved))
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .weight(1F)
            .toggleable(
              value = shown,
              enabled = !required,
              role = Role.Checkbox,
              onValueChange = { show ->
                val hidden = if (show) settings.hiddenActions - action else settings.hiddenActions + action
                onSettingsChange(settings.copy(hiddenActions = hidden))
              },
            )
            .padding(vertical = 4.dp),
        ) {
          Checkbox(checked = shown, onCheckedChange = null, enabled = !required)
          Icon(action.icon(), contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp))
          Text(
            text = stringResource(action.label()),
            style = MaterialTheme.typography.bodyLarge,
          )
        }
        IconButton(onClick = { move(-1) }, enabled = index > 0) {
          Icon(
            VoiceIcons.KeyboardArrowDown,
            contentDescription = stringResource(StringsR.string.reader_move_up),
            modifier = Modifier.rotate(180F),
          )
        }
        IconButton(onClick = { move(1) }, enabled = index < order.lastIndex) {
          Icon(VoiceIcons.KeyboardArrowDown, contentDescription = stringResource(StringsR.string.reader_move_down))
        }
      }
    }
    SwitchRow(
      label = stringResource(StringsR.string.reader_one_handed),
      summary = stringResource(StringsR.string.reader_one_handed_summary),
      checked = settings.oneHanded,
      onCheckedChange = { onSettingsChange(settings.copy(oneHanded = it)) },
    )
    SettingSlider(
      label = stringResource(StringsR.string.reader_interface_size),
      value = settings.interfaceScale,
      range = 0.8F..1.4F,
      steps = 5,
      snap = { (it * 10).roundToInt() / 10F },
      onValueChange = { onSettingsChange(settings.copy(interfaceScale = it)) },
    )
  }
}

internal fun ReaderAction.icon(): ImageVector = when (this) {
  ReaderAction.Search -> VoiceIcons.Search
  ReaderAction.Bookmark -> VoiceIcons.Bookmark
  ReaderAction.Contents -> VoiceIcons.ViewList
  ReaderAction.Display -> VoiceIcons.Title
  ReaderAction.HandsFree -> VoiceIcons.Speed
  ReaderAction.ReadAloud -> VoiceIcons.VolumeUp
}

private fun ReaderAction.label(): Int = when (this) {
  ReaderAction.Search -> StringsR.string.reader_search
  ReaderAction.Bookmark -> StringsR.string.reader_tap_bookmark
  ReaderAction.Contents -> StringsR.string.reader_contents
  ReaderAction.Display -> StringsR.string.reader_display
  ReaderAction.HandsFree -> StringsR.string.reader_hands_free
  ReaderAction.ReadAloud -> StringsR.string.reader_read_aloud
}

@Composable
private fun ScreenSettings(
  settings: ReaderSettings,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OptionChoice(
      label = stringResource(StringsR.string.reader_screen_awake),
      options = ScreenAwake.entries,
      selected = settings.screenAwake,
      optionLabel = {
        stringResource(
          when (it) {
            ScreenAwake.System -> StringsR.string.reader_brightness_system
            ScreenAwake.WhileReading -> StringsR.string.reader_screen_awake_reading
            ScreenAwake.UntilIdle -> StringsR.string.reader_screen_awake_idle
          },
        )
      },
      onSelect = { onSettingsChange(settings.copy(screenAwake = it)) },
    )
    if (settings.screenAwake == ScreenAwake.UntilIdle) {
      ChipChoice(
        label = stringResource(StringsR.string.reader_screen_awake_minutes),
        options = listOf(5, 10, 30),
        selected = settings.screenAwakeMinutes,
        optionLabel = { pluralStringResource(StringsR.plurals.duration_minutes, it, it) },
        onSelect = { onSettingsChange(settings.copy(screenAwakeMinutes = it)) },
      )
    }
  }
}

@Composable
private fun ProtectionSettings(
  settings: ReaderSettings,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SectionTitle(stringResource(StringsR.string.reader_protection))
    SettingSlider(
      label = stringResource(StringsR.string.reader_min_swipe),
      value = settings.minSwipe.toFloat(),
      range = 0F..160F,
      steps = 7,
      snap = { it.roundToInt().toFloat() },
      onValueChange = { onSettingsChange(settings.copy(minSwipe = it.roundToInt())) },
    )
    SettingSlider(
      label = stringResource(StringsR.string.reader_edge_margin),
      value = settings.edgeMargin.toFloat(),
      range = 0F..48F,
      steps = 5,
      snap = { it.roundToInt().toFloat() },
      onValueChange = { onSettingsChange(settings.copy(edgeMargin = it.roundToInt())) },
    )
    // 0 stands for never asking
    ChipChoice(
      label = stringResource(StringsR.string.reader_confirm_jump),
      options = listOf(0, 10, 50, 100),
      selected = settings.confirmJumpPages ?: 0,
      optionLabel = {
        if (it == 0) {
          stringResource(StringsR.string.reader_progress_visibility_hidden)
        } else {
          pluralStringResource(StringsR.plurals.reader_pages_count, it, it)
        }
      },
      onSelect = { onSettingsChange(settings.copy(confirmJumpPages = it.takeIf { pages -> pages > 0 })) },
    )
  }
}

@Composable
@Preview
private fun ControlsSettingsPreview() {
  VoiceTheme {
    ControlsSettings(
      settings = ReaderSettings(hiddenActions = setOf(ReaderAction.Search), screenAwake = ScreenAwake.UntilIdle),
      onSettingsChange = {},
    )
  }
}
