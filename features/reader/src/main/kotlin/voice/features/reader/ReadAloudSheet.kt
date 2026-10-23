@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package voice.features.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

@Composable
internal fun ReadAloudSheet(
  // the size of the reader's controls
  scale: Float,
  voice: VoiceState,
  onVoiceSelect: (natural: Boolean) -> Unit,
  onSpeakerSelect: (NaturalSpeaker) -> Unit,
  onSpeechRateChange: (Float) -> Unit,
  onStartClick: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    ScaledControls(scale) {
      Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
          text = stringResource(StringsR.string.reader_read_aloud),
          style = MaterialTheme.typography.titleLarge,
        )
        Column(
          Modifier
            .padding(vertical = 8.dp)
            .selectableGroup(),
        ) {
          VoiceOption(
            title = stringResource(StringsR.string.reader_voice_natural),
            summary = when {
              voice.downloadProgress != null -> stringResource(
                StringsR.string.reader_voice_natural_downloading,
                (voice.downloadProgress * 100).toInt(),
              )
              voice.downloadFailed -> stringResource(StringsR.string.reader_voice_natural_failed)
              voice.naturalReady -> null
              else -> stringResource(StringsR.string.reader_voice_natural_summary)
            },
            selected = voice.natural,
            progress = voice.downloadProgress,
            onClick = { onVoiceSelect(true) },
          )
          if (voice.natural && voice.naturalReady && voice.speakers.isNotEmpty()) {
            SpeakerPicker(
              speakers = voice.speakers,
              selected = voice.speaker,
              onSelect = onSpeakerSelect,
            )
          }
          VoiceOption(
            title = stringResource(StringsR.string.reader_voice_phone),
            summary = stringResource(StringsR.string.reader_voice_phone_summary),
            selected = !voice.natural,
            progress = null,
            onClick = { onVoiceSelect(false) },
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(StringsR.string.reader_voice_speed),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1F),
          )
          Text(
            text = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(voice.speechRate) + "×",
            style = MaterialTheme.typography.labelLarge,
          )
        }
        val sliderState = rememberSliderState(value = voice.speechRate, trackRange = 0.6F..1.8F)
        Slider(
          state = sliderState,
          onValueChange = {
            sliderState.value = it
            onSpeechRateChange((it * 10).roundToInt() / 10F)
          },
        )
        Button(
          onClick = onStartClick,
          enabled = !voice.natural || voice.naturalReady,
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        ) {
          Text(stringResource(StringsR.string.reader_voice_start))
        }
      }
    }
  }
}

@Composable
private fun SpeakerPicker(
  speakers: List<NaturalSpeaker>,
  selected: NaturalSpeaker?,
  onSelect: (NaturalSpeaker) -> Unit,
) {
  val accents = speakers.groupBy { it.language }
  Column(Modifier.padding(start = 40.dp, bottom = 8.dp)) {
    accents.forEach { (language, accentSpeakers) ->
      if (accents.size > 1) {
        Text(
          text = Locale.forLanguageTag(language).displayCountry,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        accentSpeakers.forEach { speaker ->
          FilterChip(
            selected = speaker == selected,
            onClick = { onSelect(speaker) },
            label = { Text(speaker.name) },
          )
        }
      }
    }
  }
}

@Composable
private fun VoiceOption(
  title: String,
  summary: String?,
  selected: Boolean,
  progress: Float?,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Column(
      Modifier
        .padding(start = 16.dp)
        .weight(1F),
    ) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      if (summary != null) {
        Text(
          text = summary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (progress != null) {
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          gapSize = 0.dp,
          drawStopIndicator = {},
        )
      }
    }
  }
}
