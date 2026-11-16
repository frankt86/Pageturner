package voice.features.reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

/** A part of the book to read aloud, and the [place] in the book it comes from. */
internal data class Passage<T>(
  val text: String,
  val place: T,
)

/**
 * Speaks the [passages] in turn until [speak] returns false. [speak] also gets the text that comes
 * after, so the voice can prepare it while the passage plays. Returns false if there was nothing to
 * read.
 */
internal suspend fun <T> speakInTurn(
  passages: Flow<Passage<T>>,
  speak: suspend (passage: Passage<T>, next: String?) -> Boolean,
): Boolean {
  var pending: Passage<T>? = null
  var stopped = false
  passages
    .takeWhile { passage ->
      val current = pending
      pending = passage
      stopped = current != null && !speak(current, passage.text)
      !stopped
    }
    .collect()
  val last = pending ?: return false
  if (!stopped) {
    val _ = speak(last, null)
  }
  return true
}
