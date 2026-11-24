package voice.features.reader

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.core.data.BookId
import voice.core.data.ListeningSession
import voice.core.data.repo.ListeningStatsRepo

/**
 * Records the time spent reading an ebook for the stats, next to the listening. A reading session
 * has no audio.
 *
 * The time counts while the reader shows, from one move in the book to the next. A long time
 * without a move counts only in part, as the reader may have put the phone down.
 */
@SingleIn(AppScope::class)
@Inject
class ReadingTimeRecorder(
  private val repo: ListeningStatsRepo,
  private val clock: Clock,
  private val zone: () -> ZoneId,
  private val scope: CoroutineScope,
) {

  private var session: OpenSession? = null
  private var reading = false
  private var countedUntil = 0L
  private var savedAt = 0L

  // a book opened at its end wasn't finished just now
  private var atEnd = false
  private val writes = Mutex()

  fun opened(
    bookId: BookId,
    progress: Float,
  ) {
    if (reading && session?.bookId == bookId) return
    paused()
    val nowInstant = clock.now()
    val now = nowInstant.toEpochMilliseconds()
    val previous = session
    session =
      if (
        previous != null &&
          previous.bookId == bookId &&
          now - previous.endedAtMillis <= CONTINUE_WITHIN
      ) {
        previous
      } else {
        OpenSession(
          bookId = bookId,
          startedAtMillis = now,
          utcOffsetSeconds = zone().rules.getOffset(nowInstant.toJavaInstant()).totalSeconds,
        )
      }
    reading = true
    countedUntil = now
    savedAt = now
    atEnd = progress >= FINISHED
  }

  fun moved(
    bookId: BookId,
    progress: Float,
  ) {
    val session = session
    if (!reading || session?.bookId != bookId) return
    count()
    val nowAtEnd = progress >= FINISHED
    if (nowAtEnd && !atEnd) session.reachedEnd = true
    atEnd = nowAtEnd
    if (clock.now().toEpochMilliseconds() - savedAt >= CHECKPOINT_INTERVAL || session.reachedEnd) {
      save()
    }
  }

  fun paused() {
    if (!reading) return
    count()
    reading = false
    save()
  }

  private fun count() {
    val session = session ?: return
    val now = clock.now().toEpochMilliseconds()
    // the clock can be changed, and a long time on a page is a break
    val elapsed = (now - countedUntil).coerceIn(0, MAX_COUNTED_AT_ONCE)
    session.readMillis += elapsed
    session.endedAtMillis = session.endedAtMillis.coerceAtLeast(now)
    countedUntil = now
  }

  private fun save() {
    val session = session ?: return
    savedAt = clock.now().toEpochMilliseconds()
    if (session.readMillis == 0L && !session.reachedEnd) return
    val snapshot = session.toListeningSession()
    scope.launch {
      writes.withLock {
        // the first save gives the session its id
        session.id = repo.save(snapshot.copy(id = session.id))
      }
    }
  }

  private class OpenSession(
    val bookId: BookId,
    val startedAtMillis: Long,
    val utcOffsetSeconds: Int,
  ) {
    var endedAtMillis = startedAtMillis
    var readMillis = 0L
    var reachedEnd = false
    var id = 0L

    fun toListeningSession() =
      ListeningSession(
        bookId = bookId,
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
        listenedMillis = readMillis,
        audioMillis = 0,
        utcOffsetSeconds = utcOffsetSeconds,
        reachedEnd = reachedEnd,
        id = id,
      )
  }

  private companion object {
    val CHECKPOINT_INTERVAL = 30.seconds.inWholeMilliseconds
    val MAX_COUNTED_AT_ONCE = 5.minutes.inWholeMilliseconds

    // a short break, like answering a message, doesn't end the session
    val CONTINUE_WITHIN = 5.minutes.inWholeMilliseconds

    // like the library, which counts a book read to here as finished
    const val FINISHED = 0.99F
  }
}
