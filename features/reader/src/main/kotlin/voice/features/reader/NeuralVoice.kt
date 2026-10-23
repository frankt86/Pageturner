package voice.features.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import voice.core.logging.api.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

private const val MODEL = "kokoro-int8-multi-lang-v1_0"
private const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL.tar.bz2"

/**
 * A natural sounding voice that runs on the phone. The voice itself is too big for the app, so it's downloaded once.
 */
internal class NeuralVoice(context: Context) {

  private val voicesFolder = File(context.filesDir, "voices")
  private val folder = File(voicesFolder, MODEL)
  private val doneMarker = File(folder, ".complete")
  private val downloadFile = File(context.cacheDir, "$MODEL.tar.bz2")
  private val mutex = Mutex()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var tts: OfflineTts? = null
  private var upcoming: Pair<Speech, Deferred<GeneratedAudio?>>? = null

  val isDownloaded: Boolean get() = doneMarker.exists()

  /** Calls [onProgress] with 0 to 1 while downloading. Returns whether the voice is ready. */
  suspend fun download(onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
    if (isDownloaded) return@withContext true
    try {
      val connection = URI(MODEL_URL).toURL().openConnection() as HttpURLConnection
      connection.instanceFollowRedirects = true
      val total = connection.contentLengthLong
      connection.inputStream.use { input ->
        downloadFile.outputStream().use { output ->
          val buffer = ByteArray(64 * 1024)
          var read = 0L
          while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count == -1) break
            output.write(buffer, 0, count)
            read += count
            if (total > 0) onProgress(read.toFloat() / total)
          }
        }
      }
      extract()
      doneMarker.createNewFile()
      // voices of earlier versions take up a lot of space
      voicesFolder.listFiles()?.filter { it.name != MODEL }?.forEach { it.deleteRecursively() }
      true
    } catch (e: Exception) {
      Logger.w(e, "Could not download the voice")
      folder.deleteRecursively()
      false
    } finally {
      downloadFile.delete()
    }
  }

  private fun extract() {
    folder.deleteRecursively()
    folder.mkdirs()
    val root = folder.canonicalFile
    TarArchiveInputStream(BZip2CompressorInputStream(downloadFile.inputStream().buffered())).use { tar ->
      while (true) {
        val entry = tar.nextEntry ?: break
        // the archive holds everything in a folder named like the model
        val name = entry.name.substringAfter('/', missingDelimiterValue = "")
        if (name.isEmpty()) continue
        val target = File(root, name).canonicalFile
        check(target.path.startsWith(root.path + File.separator)) { "Entry outside of the voice folder" }
        if (entry.isDirectory) {
          target.mkdirs()
        } else if (entry.isFile) {
          target.parentFile?.mkdirs()
          target.outputStream().use { tar.copyTo(it) }
        }
      }
    }
  }

  /**
   * Speaks [text] with [speaker] and returns once it was played, or false if the voice can't speak.
   * [next] is what comes after, so it can be prepared while [text] plays and there is no pause in between.
   */
  suspend fun speak(
    text: String,
    next: String?,
    speed: Float,
    speaker: NaturalSpeaker,
  ): Boolean {
    val speech = Speech(text, speed, speaker)
    val pending = upcoming
    val audio = if (pending != null && pending.first == speech) {
      pending.second.await()
    } else {
      pending?.second?.cancel()
      generate(speech)
    }
    upcoming = next?.let { Speech(it, speed, speaker) }?.let { it to scope.async { generate(it) } }
    if (audio == null) return false
    play(audio)
    return true
  }

  private suspend fun generate(speech: Speech): GeneratedAudio? = withContext(Dispatchers.Default) {
    mutex.withLock {
      val tts = tts ?: createTts() ?: return@withContext null
      try {
        tts.generateWithConfig(
          text = speech.text,
          config = GenerationConfig(
            sid = speech.speaker.id,
            speed = speech.speed,
            extra = mapOf("lang" to speech.speaker.espeakLanguage),
          ),
        ).takeIf { it.samples.isNotEmpty() }
      } catch (e: Exception) {
        Logger.w(e, "Could not speak with the voice")
        null
      }
    }
  }

  private fun createTts(): OfflineTts? {
    if (!isDownloaded) return null
    val locale = Locale.getDefault()
    val english = if (locale.prefersBritishEnglish()) "gb" else "us"
    return try {
      val config = OfflineTtsConfig(
        model = OfflineTtsModelConfig(
          kokoro = OfflineTtsKokoroModelConfig(
            model = "$folder/model.int8.onnx",
            voices = "$folder/voices.bin",
            tokens = "$folder/tokens.txt",
            dataDir = "$folder/espeak-ng-data",
            dictDir = "$folder/dict",
            lexicon = "$folder/lexicon-$english-en.txt,$folder/lexicon-zh.txt",
            lang = naturalSpeakers(locale).firstOrNull()?.espeakLanguage ?: "en-us",
          ),
          numThreads = 4,
        ),
        // reads numbers and dates in Chinese text the Chinese way
        ruleFsts = if (locale.language == "zh") {
          listOf("date", "phone", "number").joinToString(",") { "$folder/$it-zh.fst" }
        } else {
          ""
        },
      )
      OfflineTts(config = config).also { tts = it }
    } catch (e: Throwable) {
      // also covers the native library missing from the build
      Logger.w(e, "Could not start the voice")
      null
    }
  }

  private suspend fun play(audio: GeneratedAudio) = withContext(Dispatchers.IO) {
    val track = AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build(),
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
          .setSampleRate(audio.sampleRate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build(),
      )
      .setTransferMode(AudioTrack.MODE_STREAM)
      .setBufferSizeInBytes(
        AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT),
      )
      .build()
    try {
      track.play()
      var offset = 0
      val samples = audio.samples
      while (offset < samples.size) {
        ensureActive()
        val written = track.write(samples, offset, minOf(4096, samples.size - offset), AudioTrack.WRITE_BLOCKING)
        if (written <= 0) break
        offset += written
      }
      // the written samples are still in the buffer, wait until they're heard
      while (track.playbackHeadPosition < samples.size) {
        delay(20)
      }
    } finally {
      track.stop()
      track.release()
    }
  }

  fun release() {
    scope.cancel()
    upcoming = null
    // a voice still preparing the next passage holds the lock, freeing it under that would crash
    CoroutineScope(Dispatchers.Default).launch {
      mutex.withLock {
        tts?.free()
        tts = null
      }
    }
  }
}

private data class Speech(
  val text: String,
  val speed: Float,
  val speaker: NaturalSpeaker,
)
