@file:OptIn(ExperimentalMaterial3Api::class)

package voice.features.reader

import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import voice.core.data.HighlightColor
import voice.core.data.ReaderColors
import voice.core.data.ReaderImageFilter
import voice.core.data.ReaderSettings
import voice.core.data.ReaderTheme
import voice.core.ui.VoiceTheme
import voice.core.ui.icons.VoiceIcons
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import voice.core.strings.R as StringsR

/** What's open in the reader, as not every color applies to every book. */
internal enum class ColorsContent {
  OldEngine,
  NewEngine,
  Pdf,
}

@Composable
internal fun ColorsSheet(
  settings: ReaderSettings,
  content: ColorsContent,
  themesFileFailed: Boolean,
  onThemesExport: (Uri) -> Unit,
  onThemesImport: (Uri) -> Unit,
  onSettingsChange: (ReaderSettings) -> Unit,
  onDismiss: () -> Unit,
) {
  // no scrim, so the pages show each change while it's made
  ModalBottomSheet(onDismissRequest = onDismiss, scrimColor = Color.Transparent) {
    ColorSettings(
      settings = settings,
      content = content,
      themesFileFailed = themesFileFailed,
      onThemesExport = onThemesExport,
      onThemesImport = onThemesImport,
      onSettingsChange = onSettingsChange,
    )
  }
}

private enum class ColorRole {
  Background,
  Text,
  Link,
  Selection,
}

@Composable
private fun ColorSettings(
  settings: ReaderSettings,
  content: ColorsContent,
  themesFileFailed: Boolean,
  onThemesExport: (Uri) -> Unit,
  onThemesImport: (Uri) -> Unit,
  onSettingsChange: (ReaderSettings) -> Unit,
  modifier: Modifier = Modifier,
) {
  val shown = settings.pageColors()
  val paper = shown?.paper ?: MaterialTheme.colorScheme.surface
  val ink = shown?.ink ?: MaterialTheme.colorScheme.onSurface
  val shownTheme = settings.shownTheme()
  val current = ReaderColors(
    background = paper.toHex(),
    text = ink.toHex(),
    link = shown?.link?.toHex(),
    selection = shown?.selection?.toHex(),
  )
  fun editColors(change: (ReaderColors) -> ReaderColors) {
    // the first change starts from the colors on screen
    val start = if (shownTheme == ReaderTheme.Custom) settings.customColors else current
    onSettingsChange(
      settings.copy(
        theme = if (shownTheme == ReaderTheme.Custom) settings.theme else ReaderTheme.Custom,
        customColors = change(start),
      ),
    )
  }
  Column(
    modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp)
      .padding(bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = stringResource(StringsR.string.reader_colors),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.semantics { heading() },
    )
    CustomColors(
      paper = paper,
      ink = ink,
      link = shown?.link,
      selection = shown?.selection,
      roles = buildList {
        add(ColorRole.Background)
        add(ColorRole.Text)
        // the old engine shows plain text without links
        if (content == ColorsContent.NewEngine) add(ColorRole.Link)
        if (content != ColorsContent.Pdf) add(ColorRole.Selection)
      },
      onColorChange = { role, color ->
        val hex = color?.toHex()
        editColors {
          when (role) {
            ColorRole.Background -> it.copy(background = hex ?: it.background)
            ColorRole.Text -> it.copy(text = hex ?: it.text)
            ColorRole.Link -> it.copy(link = hex)
            ColorRole.Selection -> it.copy(selection = hex)
          }
        }
      },
    )
    Contrast(paper = paper, ink = ink)
    if (content != ColorsContent.OldEngine) {
      OptionChoice(
        label = stringResource(StringsR.string.reader_images),
        options = ReaderImageFilter.entries,
        selected = settings.imageFilter,
        optionLabel = {
          stringResource(
            when (it) {
              ReaderImageFilter.Original -> StringsR.string.reader_images_original
              ReaderImageFilter.Dimmed -> StringsR.string.reader_images_dimmed
              ReaderImageFilter.Inverted -> StringsR.string.reader_images_inverted
            },
          )
        },
        onSelect = { onSettingsChange(settings.copy(imageFilter = it)) },
      )
    }
    if (content != ColorsContent.Pdf) {
      SettingSlider(
        label = stringResource(StringsR.string.reader_paper_texture),
        value = settings.paperTexture,
        range = 0F..1F,
        snap = { (it * 20).roundToInt() / 20F },
        onValueChange = { onSettingsChange(settings.copy(paperTexture = it)) },
      )
    }
    Schedule(settings = settings, onSettingsChange = onSettingsChange)
    if (content != ColorsContent.Pdf) {
      HighlightPaletteSettings(
        settings = settings,
        paper = paper,
        onSettingsChange = onSettingsChange,
      )
    }
    SavedThemes(
      settings = settings,
      current = current,
      themesFileFailed = themesFileFailed,
      onThemesExport = onThemesExport,
      onThemesImport = onThemesImport,
      onSettingsChange = onSettingsChange,
    )
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
private fun CustomColors(
  paper: Color,
  ink: Color,
  link: Color?,
  selection: Color?,
  roles: List<ColorRole>,
  onColorChange: (ColorRole, Color?) -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf<ColorRole?>(null) }
  Column {
    SectionTitle(stringResource(StringsR.string.reader_theme_custom))
    roles.forEach { role ->
      val color = when (role) {
        ColorRole.Background -> paper
        ColorRole.Text -> ink
        ColorRole.Link -> link
        ColorRole.Selection -> selection
      }
      val label = stringResource(
        when (role) {
          ColorRole.Background -> StringsR.string.reader_color_background
          ColorRole.Text -> StringsR.string.reader_color_text
          ColorRole.Link -> StringsR.string.reader_color_links
          ColorRole.Selection -> StringsR.string.reader_color_selection
        },
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.medium)
          .clickable { expanded = role.takeUnless { expanded == it } }
          .padding(vertical = 8.dp),
      ) {
        Swatch(color = color, size = 32)
        Text(
          text = label,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier
            .weight(1F)
            .padding(horizontal = 16.dp),
        )
        Text(
          text = color?.toHex() ?: stringResource(StringsR.string.reader_theme_auto),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (expanded == role) {
        ColorPicker(
          color = color,
          // background and text always have a color, links and selection can be left to the theme
          canBeAuto = role == ColorRole.Link || role == ColorRole.Selection,
          onColorChange = { onColorChange(role, it) },
        )
      }
    }
  }
}

@Composable
private fun Swatch(
  color: Color?,
  size: Int,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier
      .size(size.dp)
      .clip(CircleShape)
      .background(color ?: Color.Transparent)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
  )
}

private val PaletteColors = listOf(
  "#FFFFFF", "#F5F3EE", "#F4ECD8", "#E3EEDC", "#DDE6F0", "#B4B1AB",
  "#1A5FB4", "#8AB4F8", "#4B3B2A", "#2B3440", "#1F1E1C", "#000000",
)

/** Hue, saturation and value sliders, a few colors to start from and the HEX code. */
@Composable
private fun ColorPicker(
  color: Color?,
  canBeAuto: Boolean,
  onColorChange: (Color?) -> Unit,
) {
  // kept apart from the color, so the hue stays while the color is gray
  var hsv by remember { mutableStateOf(hsvOf(color ?: Color.Gray)) }
  var hex by remember { mutableStateOf(color?.toHex().orEmpty()) }
  LaunchedEffect(color) {
    if (color != null && hsvColor(hsv) != color) hsv = hsvOf(color)
    if (color != null && parseHexColor(hex) != color) hex = color.toHex()
  }
  val change = { values: FloatArray ->
    hsv = values
    onColorChange(hsvColor(values))
  }
  Column(
    Modifier.padding(bottom = 8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.selectableGroup(),
    ) {
      if (canBeAuto) {
        FilterChip(
          selected = color == null,
          onClick = { onColorChange(null) },
          label = { Text(stringResource(StringsR.string.reader_theme_auto)) },
        )
      }
      PaletteColors.forEach { code ->
        val option = parseHexColor(code) ?: return@forEach
        val selected = option == color
        Swatch(
          color = option,
          size = 32,
          modifier = Modifier
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onColorChange(option) })
            .semantics { contentDescription = code },
        )
      }
    }
    ColorSlider(
      label = stringResource(StringsR.string.reader_color_hue),
      value = hsv[0],
      range = 0F..360F,
      onValueChange = { change(floatArrayOf(it, hsv[1], hsv[2])) },
    )
    ColorSlider(
      label = stringResource(StringsR.string.reader_color_saturation),
      value = hsv[1],
      range = 0F..1F,
      onValueChange = { change(floatArrayOf(hsv[0], it, hsv[2])) },
    )
    ColorSlider(
      label = stringResource(StringsR.string.reader_color_brightness),
      value = hsv[2],
      range = 0F..1F,
      onValueChange = { change(floatArrayOf(hsv[0], hsv[1], it)) },
    )
    OutlinedTextField(
      value = hex,
      onValueChange = { text ->
        hex = text.take(7)
        parseHexColor(hex)?.let(onColorChange)
      },
      label = { Text(stringResource(StringsR.string.reader_color_hex)) },
      isError = hex.isNotEmpty() && parseHexColor(hex) == null,
      singleLine = true,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun ColorSlider(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.widthIn(min = 88.dp),
    )
    val sliderState = rememberSliderState(value = value, trackRange = range)
    LaunchedEffect(value) {
      if (sliderState.value != value) sliderState.value = value
    }
    Slider(
      state = sliderState,
      onValueChange = {
        sliderState.value = it
        onValueChange(it)
      },
      modifier = Modifier
        .weight(1F)
        .semantics { contentDescription = label },
    )
  }
}

private fun hsvOf(color: Color): FloatArray = FloatArray(3).also { AndroidColor.colorToHSV(color.toArgb(), it) }

private fun hsvColor(hsv: FloatArray): Color = Color(AndroidColor.HSVToColor(hsv))

@Composable
private fun Contrast(
  paper: Color,
  ink: Color,
) {
  val ratio = contrastRatio(paper, ink)
  val low = ratio < MIN_CONTRAST
  Row(verticalAlignment = Alignment.CenterVertically) {
    // the icon tells a low contrast apart, not only the color
    Icon(
      imageVector = if (low) VoiceIcons.Error else VoiceIcons.Contrast,
      contentDescription = null,
      tint = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
      Modifier
        .weight(1F)
        .padding(start = 16.dp),
    ) {
      Text(
        text = stringResource(StringsR.string.reader_contrast, String.format(LocalConfiguration.current.locales.get(0), "%.1f:1", ratio)),
        style = MaterialTheme.typography.bodyLarge,
      )
      if (low) {
        Text(
          text = stringResource(StringsR.string.reader_contrast_low),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

private val ScheduleThemes = ReaderTheme.entries - ReaderTheme.Auto - ReaderTheme.Scheduled

@Composable
private fun Schedule(
  settings: ReaderSettings,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  val shownTheme = settings.shownTheme()
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    SectionTitle(stringResource(StringsR.string.reader_schedule))
    SwitchRow(
      label = stringResource(StringsR.string.reader_schedule_switch),
      checked = settings.theme == ReaderTheme.Scheduled,
      onCheckedChange = { on ->
        onSettingsChange(settings.copy(theme = if (on) ReaderTheme.Scheduled else shownTheme))
      },
    )
    if (settings.theme == ReaderTheme.Scheduled) {
      Text(stringResource(StringsR.string.reader_schedule_day), style = MaterialTheme.typography.titleSmall)
      ThemeChoice(
        settings = settings,
        options = ScheduleThemes,
        selected = settings.dayTheme,
        onThemeSelect = { onSettingsChange(settings.copy(dayTheme = it)) },
      )
      Text(stringResource(StringsR.string.reader_schedule_night), style = MaterialTheme.typography.titleSmall)
      ThemeChoice(
        settings = settings,
        options = ScheduleThemes,
        selected = settings.nightTheme,
        onThemeSelect = { onSettingsChange(settings.copy(nightTheme = it)) },
      )
      TimeStepper(
        label = stringResource(StringsR.string.reader_schedule_night_start),
        minutes = settings.nightStart,
        onMinutesChange = { onSettingsChange(settings.copy(nightStart = it)) },
      )
      TimeStepper(
        label = stringResource(StringsR.string.reader_schedule_night_end),
        minutes = settings.nightEnd,
        onMinutesChange = { onSettingsChange(settings.copy(nightEnd = it)) },
      )
    }
  }
}

private const val MINUTES_PER_DAY = 24 * 60
private const val TIME_STEP = 30

@Composable
private fun TimeStepper(
  label: String,
  minutes: Int,
  onMinutesChange: (Int) -> Unit,
) {
  val context = LocalContext.current
  val locale = LocalConfiguration.current.locales.get(0)
  val formatter = remember(context, locale) {
    DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale)
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.weight(1F),
    )
    IconButton(onClick = { onMinutesChange((minutes - TIME_STEP).mod(MINUTES_PER_DAY)) }) {
      Icon(VoiceIcons.Remove, contentDescription = stringResource(StringsR.string.reader_schedule_earlier))
    }
    Text(
      text = LocalTime.of(minutes / 60, minutes % 60).format(formatter),
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(min = 72.dp),
    )
    IconButton(onClick = { onMinutesChange((minutes + TIME_STEP).mod(MINUTES_PER_DAY)) }) {
      Icon(VoiceIcons.Add, contentDescription = stringResource(StringsR.string.reader_schedule_later))
    }
  }
}

@Composable
private fun HighlightPaletteSettings(
  settings: ReaderSettings,
  paper: Color,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionTitle(stringResource(StringsR.string.reader_highlights))
    HighlightColor.entries.forEach { color ->
      val defaultName = highlightColorDefaultName(color)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(paper)
            .background(color.ink().copy(alpha = settings.highlightOpacity)),
        )
        OutlinedTextField(
          value = settings.highlightNames[color].orEmpty(),
          onValueChange = { name ->
            val names = if (name.isBlank()) settings.highlightNames - color else settings.highlightNames + (color to name.take(30))
            onSettingsChange(settings.copy(highlightNames = names))
          },
          placeholder = { Text(defaultName) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
          modifier = Modifier
            .weight(1F)
            .padding(start = 16.dp)
            .semantics { contentDescription = defaultName },
        )
      }
    }
    SettingSlider(
      label = stringResource(StringsR.string.reader_highlight_opacity),
      value = settings.highlightOpacity,
      range = 0.15F..0.9F,
      snap = { (it * 20).roundToInt() / 20F },
      onValueChange = { onSettingsChange(settings.copy(highlightOpacity = it)) },
    )
  }
}

@Composable
private fun SavedThemes(
  settings: ReaderSettings,
  current: ReaderColors,
  themesFileFailed: Boolean,
  onThemesExport: (Uri) -> Unit,
  onThemesImport: (Uri) -> Unit,
  onSettingsChange: (ReaderSettings) -> Unit,
) {
  val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) onThemesExport(uri)
  }
  val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onThemesImport(uri)
  }
  val themes = settings.savedThemes
  val newName = stringResource(StringsR.string.reader_theme_saved_name, themes.size + 1)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    SectionTitle(stringResource(StringsR.string.reader_themes_saved))
    Column(Modifier.selectableGroup()) {
      themes.forEachIndexed { index, theme ->
        SavedThemeRow(
          theme = theme,
          selected = settings.theme == ReaderTheme.Custom && settings.customColors == theme.copy(name = ""),
          onSelect = { onSettingsChange(settings.copy(theme = ReaderTheme.Custom, customColors = theme.copy(name = ""))) },
          onRename = { name ->
            onSettingsChange(settings.copy(savedThemes = themes.toMutableList().also { it[index] = theme.copy(name = name) }))
          },
          onDuplicate = { copyName ->
            onSettingsChange(
              settings.copy(savedThemes = themes.toMutableList().also { it.add(index + 1, theme.copy(name = copyName)) }),
            )
          },
          onDelete = {
            onSettingsChange(settings.copy(savedThemes = themes.toMutableList().also { it.removeAt(index) }))
          },
        )
      }
    }
    FilledTonalButton(
      onClick = { onSettingsChange(settings.copy(savedThemes = themes + current.copy(name = newName))) },
    ) {
      Icon(VoiceIcons.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
      Text(stringResource(StringsR.string.reader_theme_save))
    }
    Row {
      TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
        Text(stringResource(StringsR.string.reader_themes_import))
      }
      TextButton(
        onClick = { exporter.launch("pageturner-themes.json") },
        enabled = themes.isNotEmpty(),
      ) {
        Text(stringResource(StringsR.string.reader_themes_export))
      }
    }
    if (themesFileFailed) {
      Text(
        text = stringResource(StringsR.string.reader_themes_file_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
}

@Composable
private fun SavedThemeRow(
  theme: ReaderColors,
  selected: Boolean,
  onSelect: () -> Unit,
  onRename: (String) -> Unit,
  onDuplicate: (String) -> Unit,
  onDelete: () -> Unit,
) {
  var renaming by rememberSaveable(theme) { mutableStateOf(false) }
  var menuOpen by remember { mutableStateOf(false) }
  val colors = theme.pageColors()
  val copyName = stringResource(StringsR.string.reader_theme_copy_name, theme.name)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect, enabled = !renaming)
      .padding(vertical = 4.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(colors.paper)
        .border(
          width = if (selected) 3.dp else 1.dp,
          color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          shape = CircleShape,
        ),
    ) {
      Text("Aa", style = MaterialTheme.typography.labelLarge, color = colors.ink)
    }
    if (renaming) {
      var name by rememberSaveable { mutableStateOf(theme.name) }
      OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(40) },
        label = { Text(stringResource(StringsR.string.book_edit_rename)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier
          .weight(1F)
          .padding(horizontal = 16.dp),
      )
      IconButton(
        onClick = {
          renaming = false
          if (name.isNotBlank()) onRename(name.trim())
        },
      ) {
        Icon(VoiceIcons.Check, contentDescription = stringResource(StringsR.string.common_action_done))
      }
    } else {
      Text(
        text = theme.name,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
          .weight(1F)
          .padding(horizontal = 16.dp),
      )
      Box {
        IconButton(onClick = { menuOpen = true }) {
          Icon(VoiceIcons.MoreVert, contentDescription = stringResource(StringsR.string.common_action_more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(StringsR.string.book_edit_rename)) },
            onClick = {
              menuOpen = false
              renaming = true
            },
          )
          DropdownMenuItem(
            text = { Text(stringResource(StringsR.string.reader_theme_duplicate)) },
            onClick = {
              menuOpen = false
              onDuplicate(copyName)
            },
          )
          DropdownMenuItem(
            text = { Text(stringResource(StringsR.string.common_action_delete)) },
            onClick = {
              menuOpen = false
              onDelete()
            },
          )
        }
      }
    }
  }
}

@Composable
@Preview
private fun ColorSettingsPreview() {
  VoiceTheme {
    ColorSettings(
      settings = ReaderSettings(
        theme = ReaderTheme.Scheduled,
        savedThemes = listOf(ReaderColors(name = "Evening", background = "#2B3440", text = "#CDD3DA")),
        highlightNames = mapOf(HighlightColor.Yellow to "Important"),
      ),
      content = ColorsContent.NewEngine,
      themesFileFailed = false,
      onThemesExport = {},
      onThemesImport = {},
      onSettingsChange = {},
    )
  }
}
