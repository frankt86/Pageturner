@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import voice.core.data.PageTransition
import voice.core.data.ProgressBarStyle
import voice.core.data.ProgressVisibility
import voice.core.data.ReaderFont
import voice.core.data.ReaderFooter
import voice.core.data.ReaderOrientation
import voice.core.data.ReaderPages
import voice.core.data.ReaderSettings
import voice.core.data.ReaderTextAlign
import voice.core.data.ReaderTheme
import voice.core.ui.VoiceTheme
import voice.core.ui.bookText
import voice.core.ui.icons.VoiceIcons
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

@Composable
internal fun DisplaySheet(
  settings: ReaderSettings,
  showTextOptions: Boolean,
  newEngine: Boolean,
  fonts: List<String>,
  fontImportFailed: Boolean,
  onFontImport: (Uri) -> Unit,
  onSettingsChange: (ReaderSettings) -> Unit,
  onDismiss: () -> Unit,
) {
  // no scrim, so the pages show each change while it's made
  ModalBottomSheet(onDismissRequest = onDismiss, scrimColor = Color.Transparent) {
    DisplaySettings(
      settings = settings,
      showTextOptions = showTextOptions,
      newEngine = newEngine,
      fonts = fonts,
      fontImportFailed = fontImportFailed,
      onFontImport = onFontImport,
      onSettingsChange = onSettingsChange,
    )
  }
}

@Composable
private fun DisplaySettings(
  settings: ReaderSettings,
  showTextOptions: Boolean,
  newEngine: Boolean,
  fonts: List<String>,
  fontImportFailed: Boolean,
  onFontImport: (Uri) -> Unit,
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
    OptionChoice(
      label = stringResource(StringsR.string.reader_page_turn),
      options = PageTransition.entries,
      selected = settings.pageTransition,
      optionLabel = {
        stringResource(
          when (it) {
            PageTransition.Curl -> StringsR.string.reader_page_turn_curl
            PageTransition.Slide -> StringsR.string.reader_page_turn_slide
            PageTransition.None -> StringsR.string.reader_page_turn_none
          },
        )
      },
      onSelect = { onSettingsChange(settings.copy(pageTransition = it)) },
    )
    if (showTextOptions) {
      TextSettings(
        settings = settings,
        newEngine = newEngine,
        fonts = fonts,
        fontImportFailed = fontImportFailed,
        onFontImport = onFontImport,
        onSettingsChange = onSettingsChange,
      )
    }
    LayoutSettings(
      settings = settings,
      showTextOptions = showTextOptions,
      onSettingsChange = onSettingsChange,
    )
    ProgressSettings(
      settings = settings,
      showFooter = showTextOptions,
      showProgressScope = newEngine,
      onSettingsChange = onSettingsChange,
    )
    Brightness(
      brightness = settings.brightness,
      onBrightnessChange = { onSettingsChange(settings.copy(brightness = it)) },
    )
    SwitchRow(
      label = stringResource(StringsR.string.reader_volume_keys),
      checked = settings.volumeKeysTurnPages,
      onCheckedChange = { onSettingsChange(settings.copy(volumeKeysTurnPages = it)) },
    )
    if (showTextOptions) {
      SwitchRow(
        label = stringResource(StringsR.string.reader_new_engine),
        checked = settings.newBookEngine,
        onCheckedChange = { onSettingsChange(settings.copy(newBookEngine = it)) },
      )
    }
  }
}

@Composable
private fun TextSettings(
  settings: ReaderSettings,
  newEngine: Boolean,
  fonts: List<String>,
  fontImportFailed: Boolean,
  onFontImport: (Uri) -> Unit,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  // the book's own style leaves only the text size to change
  val styled = !(newEngine && settings.publisherStyles)
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (newEngine) {
      SwitchRow(
        label = stringResource(StringsR.string.reader_publisher_styles),
        summary = stringResource(StringsR.string.reader_publisher_styles_summary),
        checked = settings.publisherStyles,
        onCheckedChange = { onSettingsChange(settings.copy(publisherStyles = it)) },
      )
    }
    FontChoice(
      settings = settings,
      newEngine = newEngine,
      fonts = fonts,
      enabled = styled,
      onFontImport = onFontImport,
      onSettingsChange = onSettingsChange,
    )
    if (fontImportFailed) {
      Text(
        text = stringResource(StringsR.string.reader_font_add_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
    TextSize(
      textSize = settings.textSize,
      onTextSizeChange = { onSettingsChange(settings.copy(textSize = it)) },
    )
    SettingSlider(
      label = stringResource(StringsR.string.reader_line_spacing),
      value = settings.lineSpacing,
      range = 1.15F..1.95F,
      steps = 7,
      snap = { (it * 20).roundToInt() / 20F },
      enabled = styled,
      onValueChange = { onSettingsChange(settings.copy(lineSpacing = it)) },
    )
    SettingSlider(
      label = stringResource(StringsR.string.reader_font_weight),
      value = (settings.fontWeight ?: 400).toFloat(),
      range = 300F..800F,
      steps = 4,
      snap = { (it / 100).roundToInt() * 100F },
      enabled = styled,
      onValueChange = { onSettingsChange(settings.copy(fontWeight = it.roundToInt())) },
    )
    SettingSlider(
      label = stringResource(StringsR.string.reader_letter_spacing),
      value = settings.letterSpacing ?: 0F,
      range = 0F..0.2F,
      steps = 7,
      snap = { (it * 40).roundToInt() / 40F },
      enabled = styled,
      onValueChange = { onSettingsChange(settings.copy(letterSpacing = it)) },
    )
    // Compose text has no word spacing, so the old engine can't do it
    if (newEngine) {
      SettingSlider(
        label = stringResource(StringsR.string.reader_word_spacing),
        value = settings.wordSpacing ?: 0F,
        range = 0F..1F,
        steps = 9,
        snap = { (it * 10).roundToInt() / 10F },
        enabled = styled,
        onValueChange = { onSettingsChange(settings.copy(wordSpacing = it)) },
      )
    }
    SettingSlider(
      label = stringResource(StringsR.string.reader_paragraph_spacing),
      value = settings.paragraphSpacing ?: 1F,
      range = 0F..2F,
      steps = 7,
      snap = { (it * 4).roundToInt() / 4F },
      enabled = styled,
      onValueChange = { onSettingsChange(settings.copy(paragraphSpacing = it)) },
    )
    SettingSlider(
      label = stringResource(StringsR.string.reader_paragraph_indent),
      value = settings.paragraphIndent ?: 0F,
      range = 0F..3F,
      steps = 5,
      snap = { (it * 2).roundToInt() / 2F },
      enabled = styled,
      onValueChange = { onSettingsChange(settings.copy(paragraphIndent = it)) },
    )
    OptionChoice(
      label = stringResource(StringsR.string.reader_text_align),
      // Readium can't center the text
      options = if (newEngine) {
        listOf(ReaderTextAlign.Left, ReaderTextAlign.Right, ReaderTextAlign.Justify)
      } else {
        ReaderTextAlign.entries
      },
      // the old engine starts the lines on the left without a choice, the new one keeps the book's
      selected = settings.textAlign ?: ReaderTextAlign.Left.takeUnless { newEngine },
      enabled = styled,
      optionLabel = {
        stringResource(
          when (it) {
            ReaderTextAlign.Left -> StringsR.string.reader_text_align_left
            ReaderTextAlign.Center -> StringsR.string.reader_text_align_center
            ReaderTextAlign.Right -> StringsR.string.reader_text_align_right
            ReaderTextAlign.Justify -> StringsR.string.reader_text_align_justify
          },
        )
      },
      onSelect = { onSettingsChange(settings.copy(textAlign = it)) },
    )
    SwitchRow(
      label = stringResource(StringsR.string.reader_hyphens),
      checked = settings.hyphens,
      enabled = styled,
      onCheckedChange = { onSettingsChange(settings.copy(hyphens = it)) },
    )
    SwitchRow(
      label = stringResource(StringsR.string.reader_ligatures),
      checked = settings.ligatures,
      enabled = styled,
      onCheckedChange = { onSettingsChange(settings.copy(ligatures = it)) },
    )
    TextButton(
      onClick = { onSettingsChange(settings.withDefaultTypography()) },
      enabled = settings != settings.withDefaultTypography(),
    ) {
      Icon(VoiceIcons.Replay, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
      Text(stringResource(StringsR.string.reader_text_reset))
    }
  }
}

@Composable
private fun TextSize(
  textSize: Float,
  onTextSizeChange: (Float) -> Unit,
) {
  val label = stringResource(StringsR.string.reader_text_size)
  val range = 14F..30F
  SettingSlider(
    label = label,
    value = textSize,
    range = range,
    snap = { it.roundToInt().toFloat() },
    onValueChange = onTextSizeChange,
  ) {
    IconButton(
      onClick = { onTextSizeChange((textSize - 1).coerceIn(range)) },
      enabled = textSize > range.start,
    ) {
      Icon(VoiceIcons.Remove, contentDescription = stringResource(StringsR.string.reader_text_size_smaller))
    }
    Text(
      text = textSize.roundToInt().toString(),
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(min = 32.dp),
    )
    IconButton(
      onClick = { onTextSizeChange((textSize + 1).coerceIn(range)) },
      enabled = textSize < range.endInclusive,
    ) {
      Icon(VoiceIcons.Add, contentDescription = stringResource(StringsR.string.reader_text_size_larger))
    }
  }
}

@Composable
private fun SwitchRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  summary: String? = null,
  enabled: Boolean = true,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = onCheckedChange,
      )
      .padding(vertical = 8.dp),
  ) {
    Column(
      Modifier
        .weight(1F)
        .padding(end = 16.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
      )
      if (summary != null) {
        Text(
          text = summary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
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
  settings: ReaderSettings,
  newEngine: Boolean,
  fonts: List<String>,
  enabled: Boolean,
  onFontImport: (Uri) -> Unit,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onFontImport(uri)
  }
  Column {
    Text(
      text = stringResource(StringsR.string.reader_font),
      style = MaterialTheme.typography.titleSmall,
    )
    FlowRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp)
        .selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // the old engine's serif is Literata already
      val options = ReaderFont.entries.filter { newEngine || it != ReaderFont.Literata }
      val selectedFont = settings.font.takeIf { it in options } ?: ReaderFont.Serif
      options.forEach { option ->
        FilterChip(
          selected = settings.customFont == null && option == selectedFont,
          onClick = { onSettingsChange(settings.copy(font = option, customFont = null)) },
          enabled = enabled,
          label = {
            val style = MaterialTheme.typography.labelLarge
            Text(
              text = fontName(option),
              style = style.copy(fontFamily = option.fontFamily(opticalSize = style.fontSize)),
            )
          },
          modifier = Modifier.semantics { role = Role.RadioButton },
        )
      }
      fonts.forEach { font ->
        FilterChip(
          selected = settings.customFont == font,
          onClick = { onSettingsChange(settings.copy(customFont = font)) },
          enabled = enabled,
          label = { Text(fontLabel(font), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          modifier = Modifier.semantics { role = Role.RadioButton },
        )
      }
      AssistChip(
        onClick = {
          picker.launch(
            arrayOf(
              "font/ttf",
              "font/otf",
              "font/sfnt",
              "application/x-font-ttf",
              "application/x-font-otf",
              "application/font-sfnt",
              "application/vnd.ms-opentype",
              // many file managers don't know fonts
              "application/octet-stream",
            ),
          )
        },
        enabled = enabled,
        leadingIcon = { Icon(VoiceIcons.Add, contentDescription = null) },
        label = { Text(stringResource(StringsR.string.reader_font_add)) },
      )
    }
  }
}

@Composable
private fun fontName(font: ReaderFont): String = when (font) {
  ReaderFont.Serif -> stringResource(StringsR.string.reader_font_serif)
  ReaderFont.Sans -> stringResource(StringsR.string.reader_font_sans)
  ReaderFont.Monospace -> stringResource(StringsR.string.reader_font_mono)
  ReaderFont.Literata -> "Literata"
  ReaderFont.AtkinsonHyperlegible -> "Atkinson Hyperlegible"
  ReaderFont.OpenDyslexic -> "OpenDyslexic"
}

@Composable
private fun LayoutSettings(
  settings: ReaderSettings,
  showTextOptions: Boolean,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = stringResource(StringsR.string.reader_layout),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier
        .padding(top = 8.dp)
        .semantics { heading() },
    )
    OptionChoice(
      label = stringResource(StringsR.string.reader_pages),
      options = ReaderPages.entries,
      selected = settings.pages,
      optionLabel = {
        stringResource(
          when (it) {
            ReaderPages.Auto -> StringsR.string.reader_theme_auto
            ReaderPages.One -> StringsR.string.reader_pages_one
            ReaderPages.Two -> StringsR.string.reader_pages_two
          },
        )
      },
      onSelect = { onSettingsChange(settings.copy(pages = it)) },
    )
    if (showTextOptions) {
      SettingSlider(
        label = stringResource(StringsR.string.reader_margins),
        value = settings.margin.toFloat(),
        range = 8F..56F,
        steps = 5,
        snap = { it.roundToInt().toFloat() },
        onValueChange = { onSettingsChange(settings.copy(margin = it.roundToInt())) },
      )
      SettingSlider(
        label = stringResource(StringsR.string.reader_margin_top),
        value = (settings.topMargin ?: 8).toFloat(),
        range = 0F..64F,
        steps = 7,
        snap = { it.roundToInt().toFloat() },
        onValueChange = { onSettingsChange(settings.copy(topMargin = it.roundToInt())) },
      )
      SettingSlider(
        label = stringResource(StringsR.string.reader_margin_bottom),
        value = (settings.bottomMargin ?: 8).toFloat(),
        range = 0F..64F,
        steps = 7,
        snap = { it.roundToInt().toFloat() },
        onValueChange = { onSettingsChange(settings.copy(bottomMargin = it.roundToInt())) },
      )
      SettingSlider(
        label = stringResource(StringsR.string.reader_page_gap),
        value = (settings.pageGap ?: 24).toFloat(),
        range = 0F..64F,
        steps = 7,
        snap = { it.roundToInt().toFloat() },
        onValueChange = { onSettingsChange(settings.copy(pageGap = it.roundToInt())) },
      )
      SwitchRow(
        label = stringResource(StringsR.string.reader_short_lines),
        checked = settings.shortLines,
        onCheckedChange = { onSettingsChange(settings.copy(shortLines = it)) },
      )
    }
    OptionChoice(
      label = stringResource(StringsR.string.reader_orientation),
      options = ReaderOrientation.entries,
      selected = settings.orientation,
      optionLabel = {
        stringResource(
          when (it) {
            ReaderOrientation.Auto -> StringsR.string.reader_theme_auto
            ReaderOrientation.Portrait -> StringsR.string.reader_orientation_portrait
            ReaderOrientation.Landscape -> StringsR.string.reader_orientation_landscape
          },
        )
      },
      onSelect = { onSettingsChange(settings.copy(orientation = it)) },
    )
  }
}

@Composable
private fun ProgressSettings(
  settings: ReaderSettings,
  showFooter: Boolean,
  showProgressScope: Boolean,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = stringResource(StringsR.string.reader_progress),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier
        .padding(top = 8.dp)
        .semantics { heading() },
    )
    OptionChoice(
      label = stringResource(StringsR.string.reader_progress_visibility),
      options = ProgressVisibility.entries,
      selected = settings.progressVisibility,
      optionLabel = {
        stringResource(
          when (it) {
            ProgressVisibility.Always -> StringsR.string.reader_progress_visibility_always
            ProgressVisibility.OnTap -> StringsR.string.reader_progress_visibility_on_tap
            ProgressVisibility.Hidden -> StringsR.string.reader_progress_visibility_hidden
          },
        )
      },
      onSelect = { onSettingsChange(settings.copy(progressVisibility = it)) },
    )
    OptionChoice(
      label = stringResource(StringsR.string.reader_progress_style),
      options = ProgressBarStyle.entries,
      selected = settings.progressBar,
      optionLabel = {
        stringResource(
          when (it) {
            ProgressBarStyle.Line -> StringsR.string.reader_progress_style_line
            ProgressBarStyle.Bar -> StringsR.string.reader_progress_style_bar
            ProgressBarStyle.Segmented -> StringsR.string.reader_progress_style_chapters
          },
        )
      },
      onSelect = { onSettingsChange(settings.copy(progressBar = it)) },
    )
    if (showFooter) {
      OptionChoice(
        label = stringResource(StringsR.string.reader_footer),
        options = ReaderFooter.entries,
        selected = settings.footer,
        optionLabel = {
          stringResource(
            when (it) {
              ReaderFooter.Page -> StringsR.string.reader_footer_page
              ReaderFooter.ChapterPage -> StringsR.string.reader_footer_chapter
              ReaderFooter.Percent -> StringsR.string.reader_footer_percent
              ReaderFooter.TimeLeft -> StringsR.string.reader_footer_time
            },
          )
        },
        onSelect = { onSettingsChange(settings.copy(footer = it)) },
      )
    }
    SwitchRow(
      label = stringResource(StringsR.string.reader_progress_marks),
      checked = settings.progressMarks,
      onCheckedChange = { onSettingsChange(settings.copy(progressMarks = it)) },
    )
    if (showProgressScope) {
      SwitchRow(
        label = stringResource(StringsR.string.reader_progress_body_only),
        checked = settings.progressBodyOnly,
        onCheckedChange = { onSettingsChange(settings.copy(progressBodyOnly = it)) },
      )
    }
  }
}

@Composable
private fun <T : Any> OptionChoice(
  label: String,
  options: List<T>,
  selected: T?,
  optionLabel: @Composable (T) -> String,
  onSelect: (T) -> Unit,
  enabled: Boolean = true,
) {
  Column {
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp)
        .selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
      options.forEachIndexed { index, option ->
        ToggleButton(
          checked = option == selected,
          onCheckedChange = { onSelect(option) },
          enabled = enabled,
          shapes = when (index) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
          },
          modifier = Modifier
            .weight(1F)
            .semantics { role = Role.RadioButton },
        ) {
          Text(
            text = optionLabel(option),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
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
  snap: (Float) -> Float = { it },
  enabled: Boolean = true,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1F),
      )
      actions()
    }
    val sliderState = rememberSliderState(value = value, steps = steps, trackRange = range)
    val latestSnap by rememberUpdatedState(snap)
    // follows changes from elsewhere, like the reset button, but not the rounding while it's dragged
    LaunchedEffect(value) {
      if (latestSnap(sliderState.value) != value) sliderState.value = value
    }
    Slider(
      state = sliderState,
      enabled = enabled,
      onValueChange = {
        sliderState.value = it
        onValueChange(snap(it))
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
      newEngine = true,
      fonts = listOf("My_font.ttf"),
      fontImportFailed = false,
      onFontImport = {},
      onSettingsChange = {},
    )
  }
}
