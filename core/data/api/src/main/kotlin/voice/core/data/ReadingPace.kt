package voice.core.data

import kotlinx.serialization.Serializable

/**
 * How fast the reader reads, as the characters read over the time it took. Starts at about 1,200 characters a
 * minute and follows the last hour of reading.
 */
@Serializable
public data class ReadingPace(
  val characters: Double = 6_000.0,
  val millis: Long = 5 * 60_000L,
) {

  public val charactersPerMinute: Double get() = characters / millis.coerceAtLeast(1) * 60_000

  public fun plus(
    characters: Double,
    millis: Long,
  ): ReadingPace {
    val totalMillis = this.millis + millis
    val keep = if (totalMillis > WINDOW_MILLIS) WINDOW_MILLIS.toDouble() / totalMillis else 1.0
    return ReadingPace(
      characters = (this.characters + characters) * keep,
      millis = (totalMillis * keep).toLong(),
    )
  }
}

private const val WINDOW_MILLIS = 60 * 60_000L
