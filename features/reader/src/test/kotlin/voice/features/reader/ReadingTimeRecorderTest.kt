package voice.features.reader

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import voice.core.data.BookId
import voice.core.data.ListeningSession
import voice.core.data.ListeningTally
import voice.core.data.repo.ListeningStatsRepo
import voice.core.data.repo.ListeningSummary

class ReadingTimeRecorderTest {

  private val scope = TestScope()
  private val start = Instant.parse("2026-11-20T18:00:00Z")
  private val clock =
    object : Clock {
      override fun now(): Instant = start + scope.testScheduler.currentTime.milliseconds
    }
  private val repo = MemoryRepo()
  private val recorder = ReadingTimeRecorder(repo, clock, { ZoneOffset.UTC }, scope.backgroundScope)
  private val book = BookId("book")

  private fun session(
    ended: Duration,
    read: Duration,
    reachedEnd: Boolean = false,
  ) =
    ListeningSession(
      bookId = book,
      startedAtMillis = start.toEpochMilliseconds(),
      endedAtMillis = start.toEpochMilliseconds() + ended.inWholeMilliseconds,
      listenedMillis = read.inWholeMilliseconds,
      audioMillis = 0,
      utcOffsetSeconds = 0,
      reachedEnd = reachedEnd,
      id = 1,
    )

  @Test
  fun `open a book, turn pages for 10 minutes, leave, one session of 10 minutes without audio`() =
    scope.runTest {
      recorder.opened(book, progress = 0.2F)
      repeat(10) {
        advanceTimeBy(1.minutes)
        recorder.moved(book, progress = 0.2F + it / 100F)
      }
      recorder.paused()
      runCurrent()

      assertEquals(expected = listOf(session(ended = 10.minutes, read = 10.minutes)), repo.all)
    }

  @Test
  fun `open a book, put the phone down on one page for an hour, only 5 minutes count`() =
    scope.runTest {
      recorder.opened(book, progress = 0.2F)
      advanceTimeBy(60.minutes)
      recorder.paused()
      runCurrent()

      assertEquals(expected = listOf(session(ended = 60.minutes, read = 5.minutes)), repo.all)
    }

  @Test
  fun `read, a short break, read on, one session`() = scope.runTest {
    recorder.opened(book, progress = 0.2F)
    advanceTimeBy(2.minutes)
    recorder.paused()
    advanceTimeBy(1.minutes)
    recorder.opened(book, progress = 0.2F)
    advanceTimeBy(2.minutes)
    recorder.paused()
    runCurrent()

    assertEquals(expected = listOf(session(ended = 5.minutes, read = 4.minutes)), repo.all)
  }

  @Test
  fun `read to the end, the session reached the end`() = scope.runTest {
    recorder.opened(book, progress = 0.97F)
    advanceTimeBy(30.seconds)
    recorder.moved(book, progress = 1F)
    recorder.paused()
    runCurrent()

    assertEquals(
      expected = listOf(session(ended = 30.seconds, read = 30.seconds, reachedEnd = true)),
      repo.all,
    )
  }

  @Test
  fun `open a book at its end, it wasn't finished just now`() = scope.runTest {
    recorder.opened(book, progress = 1F)
    advanceTimeBy(30.seconds)
    recorder.moved(book, progress = 1F)
    recorder.paused()
    runCurrent()

    assertEquals(expected = listOf(session(ended = 30.seconds, read = 30.seconds)), repo.all)
  }

  @Test
  fun `open a book and leave right away, nothing is saved`() = scope.runTest {
    recorder.opened(book, progress = 0.2F)
    recorder.paused()
    runCurrent()

    assertEquals(expected = emptyList(), repo.all)
  }

  private class MemoryRepo : ListeningStatsRepo {

    val all = mutableListOf<ListeningSession>()

    override suspend fun save(session: ListeningSession): Long {
      val id = if (session.id == 0L) all.size + 1L else session.id
      val index = all.indexOfFirst { it.id == id }
      if (index == -1) all += session.copy(id = id) else all[index] = session.copy(id = id)
      return id
    }

    override suspend fun summary(
      from: Instant,
      to: Instant,
    ): ListeningSummary = error("Not used")

    override fun sessions(): Flow<List<ListeningSession>> = error("Not used")

    override fun listened(
      from: LocalDate,
      to: LocalDate,
    ): Flow<Duration?> = error("Not used")

    override suspend fun add(tally: ListeningTally) = error("Not used")

    override suspend fun moveToBook(
      from: List<BookId>,
      to: BookId,
    ) = error("Not used")
  }
}
