package voice.features.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

internal class ReadAloud(context: Context) {

  private val ready = CompletableDeferred<Boolean>()
  private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
  private val textToSpeech = TextToSpeech(context.applicationContext) { status ->
    ready.complete(status == TextToSpeech.SUCCESS)
  }

  init {
    textToSpeech.setOnUtteranceProgressListener(
      object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {}

        override fun onDone(utteranceId: String) {
          pending.remove(utteranceId)?.resume(Unit)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
          pending.remove(utteranceId)?.resume(Unit)
        }
      },
    )
  }

  /** Returns once the text was spoken, or false if this device can't speak. */
  suspend fun speak(text: String): Boolean {
    if (!ready.await()) return false
    val id = UUID.randomUUID().toString()
    suspendCancellableCoroutine { continuation ->
      pending[id] = continuation
      continuation.invokeOnCancellation {
        pending.remove(id)
        textToSpeech.stop()
      }
      if (textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
        pending.remove(id)?.resume(Unit)
      }
    }
    return true
  }

  fun shutdown() {
    textToSpeech.shutdown()
  }
}
