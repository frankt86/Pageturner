package voice.features.reader

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import voice.core.data.ProgressBarStyle
import voice.core.data.ReaderFooter
import voice.core.data.ReadingPace
import java.text.NumberFormat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import voice.core.strings.R as StringsR

/** Where the reader is in the chapter and the book, and how much is left. */
@Immutable
internal data class ReadingProgress(
  /** Starting at 1. */
  val page: Int,
  val pageCount: Int,
  val bookFraction: Float,
  val pagesLeftInBook: Int,
  /** Starting at 1, null for books without chapters. */
  val chapterPage: Int? = null,
  val chapterPageCount: Int? = null,
  val chapterFraction: Float? = null,
  val pagesLeftInChapter: Int? = null,
  val chapterTimeLeft: Duration? = null,
  val bookTimeLeft: Duration? = null,
  /** The page of the printed book, from the book's own page list. */
  val printPage: String? = null,
  val standardPages: Int? = null,
)

/** Where the chapters, bookmarks and highlights are along the bar, from 0 to 1. */
@Immutable
internal data class BarMarks(
  val chapters: List<Float> = emptyList(),
  val bookmarks: List<Float> = emptyList(),
  val highlights: List<Float> = emptyList(),
)

/** The characters of a standard page, as counted by publishers. */
internal const val STANDARD_PAGE_CHARACTERS = 1024

internal fun standardPages(characters: Int): Int = ceil(characters / STANDARD_PAGE_CHARACTERS.toDouble()).toInt()

/** How long reading [characters] takes at [pace], or null when there's nothing left to read. */
internal fun timeToRead(
  characters: Double,
  pace: ReadingPace,
): Duration? {
  if (characters <= 0) return null
  val minutes = (characters / pace.charactersPerMinute).roundToLong().coerceAtLeast(1)
  return minutes.minutes
}

internal fun fractionOf(
  index: Int,
  count: Int,
): Float = index.toFloat() / (count - 1).coerceAtLeast(1)

internal fun List<Page>.chapterStarts(): List<Int> = indices.filter { it == 0 || this[it].section != this[it - 1].section }

/** The progress of the old engine's [index], with [pagesPerSpread] pages on screen. */
internal fun ReaderContent.readingProgress(
  pages: List<Page>,
  index: Int,
  pagesPerSpread: Int,
  pace: ReadingPace,
): ReadingProgress {
  val lastShown = ((index / pagesPerSpread + 1) * pagesPerSpread - 1).coerceAtMost(pages.lastIndex)
  val bookProgress = ReadingProgress(
    page = index + 1,
    pageCount = pages.size,
    bookFraction = pages.progressAt(index, pagesPerSpread),
    pagesLeftInBook = pages.lastIndex - lastShown,
  )
  if (this !is ReaderContent.Epub) return bookProgress
  val section = pages[index].section
  val chapterFirst = pages.indexOfFirst { it.section == section }
  val chapterLast = pages.indexOfLast { it.section == section }
  val shownEnd = pages[lastShown]
  val leftInSection = chapters[shownEnd.section].length - shownEnd.end
  val leftInChapter = if (shownEnd.section == section) leftInSection else 0
  val leftInBook = leftInSection + chapters.drop(shownEnd.section + 1).sumOf { it.length }
  return bookProgress.copy(
    chapterPage = index - chapterFirst + 1,
    chapterPageCount = chapterLast - chapterFirst + 1,
    chapterFraction = (lastShown.coerceAtMost(chapterLast) - chapterFirst + 1).toFloat() / (chapterLast - chapterFirst + 1),
    pagesLeftInChapter = pages.pagesLeftInChapter(index, pagesPerSpread),
    chapterTimeLeft = timeToRead(leftInChapter.toDouble(), pace),
    bookTimeLeft = timeToRead(leftInBook.toDouble(), pace),
    standardPages = standardPages(totalLength),
  )
}

@Composable
internal fun formatPercent(fraction: Float): String {
  val locale = LocalConfiguration.current.locales[0]
  val format = remember(locale) { NumberFormat.getPercentInstance(locale) }
  // 100 % only on the last page
  return format.format(floor(fraction * 100) / 100)
}

// "2 hr, 5 min" in the reader's language, without own translations
@Composable
internal fun formatDuration(duration: Duration): String {
  val locale = LocalConfiguration.current.locales[0]
  val format = remember(locale) { MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT) }
  val hours = duration.inWholeHours
  val minutes = duration.inWholeMinutes % 60
  val measures = when {
    hours > 0 && minutes > 0 -> listOf(Measure(hours, MeasureUnit.HOUR), Measure(minutes, MeasureUnit.MINUTE))
    hours > 0 -> listOf(Measure(hours, MeasureUnit.HOUR))
    else -> listOf(Measure(minutes, MeasureUnit.MINUTE))
  }
  return format.formatMeasures(*measures.toTypedArray())
}

/** What the line at the bottom of the page shows, or null when there's nothing to show. */
@Composable
internal fun footerText(
  footer: ReaderFooter,
  progress: ReadingProgress,
): String? {
  val chapterPage = progress.chapterPage
  val chapterPageCount = progress.chapterPageCount
  return when (footer) {
    ReaderFooter.Page -> stringResource(StringsR.string.reader_page, progress.page, progress.pageCount)
    ReaderFooter.ChapterPage -> if (chapterPage != null && chapterPageCount != null) {
      stringResource(StringsR.string.reader_footer_chapter_page, chapterPage, chapterPageCount)
    } else {
      stringResource(StringsR.string.reader_page, progress.page, progress.pageCount)
    }
    ReaderFooter.Percent -> formatPercent(progress.bookFraction)
    ReaderFooter.TimeLeft -> progress.chapterTimeLeft?.let {
      stringResource(StringsR.string.reader_footer_time_left, formatDuration(it))
    }
  }
}

/** The chapter and the book in numbers, under the slider of the page bar. */
@Composable
internal fun ProgressDetails(
  progress: ReadingProgress,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    val chapterPage = progress.chapterPage
    val chapterPageCount = progress.chapterPageCount
    if (chapterPage != null && chapterPageCount != null) {
      ProgressRow(
        label = stringResource(StringsR.string.reader_progress_chapter),
        values = listOfNotNull(
          stringResource(StringsR.string.reader_progress_page, chapterPage, chapterPageCount),
          progress.chapterFraction?.let { formatPercent(it) },
          progress.pagesLeftInChapter?.let { pluralStringResource(StringsR.plurals.reader_progress_pages_left, it, it) },
          progress.chapterTimeLeft?.let { stringResource(StringsR.string.reader_progress_time_left, formatDuration(it)) },
        ),
      )
    }
    ProgressRow(
      label = stringResource(StringsR.string.reader_progress_book),
      values = listOfNotNull(
        formatPercent(progress.bookFraction),
        pluralStringResource(StringsR.plurals.reader_progress_pages_left, progress.pagesLeftInBook, progress.pagesLeftInBook),
        progress.bookTimeLeft?.let { stringResource(StringsR.string.reader_progress_time_left, formatDuration(it)) },
      ),
    )
    val extras = listOfNotNull(
      progress.printPage?.let { stringResource(StringsR.string.reader_progress_print_page, it) },
      progress.standardPages?.let { pluralStringResource(StringsR.plurals.reader_progress_standard_pages, it, it) },
    )
    if (extras.isNotEmpty()) {
      Text(
        text = extras.joinToString(separator = " · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}

@Composable
private fun ProgressRow(
  label: String,
  values: List<String>,
) {
  Row(Modifier.padding(top = 2.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .widthIn(min = 96.dp)
        .padding(end = 8.dp),
    )
    Text(
      text = values.joinToString(separator = " · "),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** The thin line at the bottom of the page that shows how far into the book the reader is. */
@Composable
internal fun ProgressLine(
  fraction: Float,
  style: ProgressBarStyle,
  chapters: List<Float>,
  color: Color,
  modifier: Modifier = Modifier,
) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Canvas(
    modifier
      .fillMaxWidth()
      .height(3.dp),
  ) {
    drawProgressTrack(
      fraction = fraction,
      segments = if (style == ProgressBarStyle.Segmented) chapters else emptyList(),
      active = color,
      inactive = color.copy(alpha = 0.2F),
      thickness = size.height,
      rtl = rtl,
    )
  }
}

/** A track of the page bar's slider for the [ProgressBarStyle.Line] and [ProgressBarStyle.Segmented] styles. */
@Composable
internal fun ProgressTrack(
  fraction: Float,
  style: ProgressBarStyle,
  chapters: List<Float>,
  modifier: Modifier = Modifier,
) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val active = MaterialTheme.colorScheme.primary
  val inactive = MaterialTheme.colorScheme.secondaryContainer
  val thickness: Dp = if (style == ProgressBarStyle.Line) 4.dp else 12.dp
  Canvas(
    modifier
      .fillMaxWidth()
      .height(16.dp),
  ) {
    drawProgressTrack(
      fraction = fraction,
      segments = if (style == ProgressBarStyle.Segmented) chapters else emptyList(),
      active = active,
      inactive = inactive,
      thickness = thickness.toPx(),
      rtl = rtl,
    )
  }
}

/** Draws the chapters, bookmarks and highlights over the slider of the page bar. */
@Composable
internal fun ProgressMarks(
  marks: BarMarks,
  modifier: Modifier = Modifier,
) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val chapterColor = MaterialTheme.colorScheme.outline
  val bookmarkColor = MaterialTheme.colorScheme.primary
  val highlightColor = MaterialTheme.colorScheme.tertiary
  Canvas(modifier) {
    fun x(fraction: Float) = (if (rtl) 1F - fraction else fraction) * size.width
    val center = size.height / 2
    // too many chapters would fill the bar
    if (marks.chapters.size * 4.dp.toPx() < size.width) {
      marks.chapters.forEach { fraction ->
        drawRect(
          color = chapterColor,
          topLeft = Offset(x(fraction) - 1.dp.toPx() / 2, center + 10.dp.toPx()),
          size = Size(1.dp.toPx(), 5.dp.toPx()),
        )
      }
    }
    marks.highlights.forEach { fraction ->
      drawCircle(color = highlightColor, radius = 2.5.dp.toPx(), center = Offset(x(fraction), center + 18.dp.toPx()))
    }
    marks.bookmarks.forEach { fraction ->
      // a small bookmark ribbon above the bar
      val left = x(fraction) - 3.dp.toPx()
      val top = center - 19.dp.toPx()
      val width = 6.dp.toPx()
      val height = 8.dp.toPx()
      val ribbon = Path().apply {
        moveTo(left, top)
        lineTo(left + width, top)
        lineTo(left + width, top + height)
        lineTo(left + width / 2, top + height * 0.7F)
        lineTo(left, top + height)
        close()
      }
      drawPath(ribbon, bookmarkColor)
    }
  }
}

private fun DrawScope.drawProgressTrack(
  fraction: Float,
  segments: List<Float>,
  active: Color,
  inactive: Color,
  thickness: Float,
  rtl: Boolean,
) {
  val top = (size.height - thickness) / 2
  val radius = CornerRadius(thickness / 2)
  val gap = 2.dp.toPx()
  // too many chapters would leave no bar between the gaps
  val starts = segments.filter { it > 0F }.takeIf { (it.size + 1) * gap * 3 < size.width }.orEmpty()
  val bounds = listOf(0F) + starts + 1F
  bounds.zipWithNext().forEachIndexed { index, (from, to) ->
    val start = from * size.width + if (index == 0) 0F else gap / 2
    val end = to * size.width - if (index == bounds.lastIndex - 1) 0F else gap / 2
    if (end <= start) return@forEachIndexed
    val filledEnd = (fraction * size.width).coerceIn(start, end)
    fun drawPart(
      partStart: Float,
      partEnd: Float,
      color: Color,
    ) {
      if (partEnd <= partStart) return
      val left = if (rtl) size.width - partEnd else partStart
      drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(partEnd - partStart, thickness), cornerRadius = radius)
    }
    drawPart(start, end, inactive)
    drawPart(start, filledEnd, active)
  }
}
