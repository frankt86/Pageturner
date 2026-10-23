@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import voice.core.data.ReaderFont
import voice.core.data.ReaderSettings
import voice.core.data.ReaderTheme
import voice.core.ui.VoiceTheme
import voice.core.ui.bookText
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

@Composable
internal fun DisplaySheet(
  settings: ReaderSettings,
  showTextOptions: Boolean,
  onSettingsChange: (ReaderSettings) -> Unit,
  onDismiss: () -> Unit,
) {
  // no scrim, so the pages show each change while it's made
  ModalBottomSheet(onDismissRequest = onDismiss, scrimColor = Color.Transparent) {
    DisplaySettings(
      settings = settings,
      showTextOptions = showTextOptions,
      onSettingsChange = onSettingsChange,
    )
  }
}

@Composable
private fun DisplaySettings(
  settings: ReaderSettings,
  showTextOptions: Boolean,
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
      text = stringResource(StringsR.string.reader_display),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.semantics { heading() },
    )
    ThemeChoice(
      theme = settings.theme,
      onThemeSelect = { onSettingsChange(settings.copy(theme = it)) },
    )
    if (showTextOptions) {
      FontChoice(
        font = settings.font,
        onFontSelect = { onSettingsChange(settings.copy(font = it)) },
      )
      SettingSlider(
        label = stringResource(StringsR.string.reader_text_size),
        value = settings.textSize,
        range = 14F..30F,
        onValueChange = { onSettingsChange(settings.copy(textSize = it.roundToInt().toFloat())) },
      )
      SettingSlider(
        label = stringResource(StringsR.string.reader_line_spacing),
        value = settings.lineSpacing,
        range = 1.15F..1.95F,
        steps = 7,
        onValueChange = { onSettingsChange(settings.copy(lineSpacing = (it * 20).roundToInt() / 20F)) },
      )
      SettingSlider(
        label = stringResource(StringsR.string.reader_margins),
        value = settings.margin.toFloat(),
        range = 8F..56F,
        steps = 5,
        onValueChange = { onSettingsChange(settings.copy(margin = it.roundToInt())) },
      )
    }
    Brightness(
      brightness = settings.brightness,
      onBrightnessChange = { onSettingsChange(settings.copy(brightness = it)) },
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .toggleable(
          value = settings.volumeKeysTurnPages,
          role = Role.Switch,
          onValueChange = { onSettingsChange(settings.copy(volumeKeysTurnPages = it)) },
        )
        .padding(vertical = 8.dp),
    ) {
      Text(
        text = stringResource(StringsR.string.reader_volume_keys),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
          .weight(1F)
          .padding(end = 16.dp),
      )
      Switch(checked = settings.volumeKeysTurnPages, onCheckedChange = null)
    }
  }
}

@Composable
private fun ThemeChoice(
  theme: ReaderTheme,
  onThemeSelect: (ReaderTheme) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .selectableGroup(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    ReaderTheme.entries.forEach { option ->
      val colors = option.pageColors()
      val selected = option == theme
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .clip(MaterialTheme.shapes.medium)
          .selectable(selected = selected, role = Role.RadioButton, onClick = { onThemeSelect(option) })
          .padding(4.dp),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(colors?.paper ?: MaterialTheme.colorScheme.surface)
            .border(
              width = if (selected) 3.dp else 1.dp,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
              shape = CircleShape,
            ),
        ) {
          Text(
            text = "Aa",
            style = MaterialTheme.typography.titleMedium.bookText(),
            color = colors?.ink ?: MaterialTheme.colorScheme.onSurface,
          )
        }
        Text(
          text = stringResource(option.label()),
          style = MaterialTheme.typography.labelMedium,
          color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    }
  }
}

private fun ReaderTheme.label(): Int = when (this) {
  ReaderTheme.Auto -> StringsR.string.reader_theme_auto
  ReaderTheme.Day -> StringsR.string.reader_theme_day
  ReaderTheme.Sepia -> StringsR.string.reader_theme_sepia
  ReaderTheme.Night -> StringsR.string.reader_theme_night
  ReaderTheme.Black -> StringsR.string.reader_theme_black
}

@Composable
private fun FontChoice(
  font: ReaderFont,
  onFontSelect: (ReaderFont) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
  ) {
    ReaderFont.entries.forEachIndexed { index, option ->
      ToggleButton(
        checked = option == font,
        onCheckedChange = { onFontSelect(option) },
        shapes = when (index) {
          0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
          else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        },
        modifier = Modifier
          .weight(1F)
          .semantics { role = Role.RadioButton },
      ) {
        val style = MaterialTheme.typography.labelLarge
        Text(
          text = stringResource(
            when (option) {
              ReaderFont.Serif -> StringsR.string.reader_font_serif
              ReaderFont.Sans -> StringsR.string.reader_font_sans
            },
          ),
          style = when (option) {
            ReaderFont.Serif -> style.bookText()
            ReaderFont.Sans -> style
          },
        )
      }
    }
  }
}

@Composable
private fun SettingSlider(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
  steps: Int = 0,
) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
    )
    val sliderState = rememberSliderState(value = value, steps = steps, trackRange = range)
    Slider(
      state = sliderState,
      onValueChange = {
        sliderState.value = it
        onValueChange(it)
      },
      modifier = Modifier.semantics { contentDescription = label },
    )
  }
}

@Composable
private fun Brightness(
  brightness: Float?,
  onBrightnessChange: (Float?) -> Unit,
) {
  val label = stringResource(StringsR.string.reader_brightness)
  val sliderState = rememberSliderState(value = brightness ?: 0.5F, trackRange = 0.02F..1F)
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1F),
      )
      FilterChip(
        selected = brightness == null,
        onClick = { onBrightnessChange(if (brightness == null) sliderState.value else null) },
        label = { Text(stringResource(StringsR.string.reader_brightness_system)) },
      )
    }
    Slider(
      state = sliderState,
      onValueChange = {
        sliderState.value = it
        onBrightnessChange(it)
      },
      modifier = Modifier.semantics { contentDescription = label },
    )
  }
}

@Composable
@Preview
private fun DisplaySettingsPreview() {
  VoiceTheme {
    DisplaySettings(
      settings = ReaderSettings(theme = ReaderTheme.Sepia),
      showTextOptions = true,
      onSettingsChange = {},
    )
  }
}
