package voice.features.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "NeuralVoiceProbe"

@RunWith(AndroidJUnit4::class)
class NeuralVoiceProbeTest {

  @Test
  fun audioTrackFinishes() {
    val rate = 24000
    val samples = FloatArray(rate * 2) { (0.3 * sin(2 * PI * 440 * it / rate)).toFloat() }
    val minBuffer =
      AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
    val track =
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(minBuffer)
        .build()
    Log.i(TAG, "minBuffer=$minBuffer state=${track.state} frames=${track.bufferSizeInFrames}")
    track.play()
    var offset = 0
    while (offset < samples.size) {
      val written =
        track.write(samples, offset, minOf(4096, samples.size - offset), AudioTrack.WRITE_BLOCKING)
      Log.i(TAG, "written=$written offset=$offset")
      if (written <= 0) break
      offset += written
    }
    val start = SystemClock.elapsedRealtime()
    while (track.playbackHeadPosition < samples.size && SystemClock.elapsedRealtime() - start < 15_000) {
      Thread.sleep(20)
    }
    Log.i(TAG, "head=${track.playbackHeadPosition} of ${samples.size} after ${SystemClock.elapsedRealtime() - start} ms")
    track.stop()
    track.release()
  }

  @Test
  fun speaks() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    Log.i(TAG, "abis=${android.os.Build.SUPPORTED_ABIS.toList()} lib=${context.applicationInfo.nativeLibraryDir}")
    val voice = NeuralVoice(context)
    var start = SystemClock.elapsedRealtime()
    val downloaded = voice.download {}
    Log.i(TAG, "downloaded=$downloaded in ${SystemClock.elapsedRealtime() - start} ms")
    assertTrue(downloaded)
    val speaker = naturalSpeakers(Locale.US).first()
    start = SystemClock.elapsedRealtime()
    val first = withTimeoutOrNull(25.minutes) { voice.speak("Hello there.", "It works.", 1F, speaker) }
    Log.i(TAG, "first=$first in ${SystemClock.elapsedRealtime() - start} ms")
    start = SystemClock.elapsedRealtime()
    val second = withTimeoutOrNull(10.minutes) { voice.speak("It works.", null, 1F, speaker) }
    Log.i(TAG, "second=$second in ${SystemClock.elapsedRealtime() - start} ms")
    assertTrue(first == true && second == true)
  }
}
