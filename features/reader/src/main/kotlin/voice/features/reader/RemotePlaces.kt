package voice.features.reader

import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import voice.core.data.EbookPosition
import voice.core.opds.komga.RemotePlace
import kotlin.math.roundToInt

/** The place [fraction] of the way through chapters of these lengths, counted like the progress of the old book engine. */
internal fun ebookPositionAt(
  chapterLengths: List<Int>,
  fraction: Double,
): EbookPosition {
  val total = chapterLengths.sumOf { it.toLong() }
  if (total <= 0L) return EbookPosition()
  var left = (fraction.coerceIn(0.0, 1.0) * total).toLong()
  chapterLengths.forEachIndexed { section, length ->
    if (left < length) return EbookPosition(section, left.toInt())
    left -= length
  }
  val last = chapterLengths.indexOfLast { it > 0 }
  return EbookPosition(last, chapterLengths[last] - 1)
}

/** The place in the old book engine. The server counts the whole book, so it's about the same spot. */
internal fun RemotePlace.position(content: ReaderContent): EbookPosition = when (content) {
  is ReaderContent.Epub -> ebookPositionAt(content.chapters.map { it.length }, totalProgression)
  is ReaderContent.Pdf -> {
    val lastPage = (content.sectionCount - 1).coerceAtLeast(0)
    EbookPosition(section = (page ?: (totalProgression * lastPage).roundToInt()).coerceIn(0, lastPage))
  }
}

/** The place in the file the server names, when the book has that file. */
internal fun Publication.locatorAt(place: RemotePlace): Locator? {
  val href = place.href ?: return null
  val link = readingOrder.firstOrNull { it.url().path?.removePrefix("/") == href } ?: return null
  return locatorFromLink(link)?.copyWithLocations(
    progression = place.progression ?: 0.0,
    totalProgression = place.totalProgression,
  )
}
