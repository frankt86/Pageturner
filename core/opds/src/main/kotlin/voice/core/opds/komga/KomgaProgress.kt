package voice.core.opds.komga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import voice.core.data.EbookFormat
import java.net.URLDecoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.abs

private val fileLink = Regex("/books/([^/?#]+)/file(?:[/?#]|$)")
private val komgaId = Regex("[A-Za-z0-9]+")

/**
 * Komga links the file of a book at `/opds/v1.2/books/{id}/file/…` or `/api/v1/books/{id}/file`, and its OPDS entries
 * carry the book id too.
 */
public fun komgaBookId(
  acquisitionUrl: String,
  entryId: String,
): String? {
  fileLink.find(acquisitionUrl)?.groupValues?.get(1)?.takeIf { komgaId.matches(it) }?.let { return it }
  return entryId.substringAfterLast(':').takeIf { komgaId.matches(it) }
}

/** Where the server says the reader is. */
public data class RemotePlace(
  /** Epoch milliseconds. */
  val modified: Long,
  /** In the whole book, from 0 to 1. */
  val totalProgression: Double,
  /** The file in an EPUB and the progression in it, when the server knows them. */
  val href: String?,
  val progression: Double?,
  /** The page of a PDF, counted from 0. */
  val page: Int?,
  val completed: Boolean,
)

/** Places closer than this are the same page, so taking them would only move the reader a little. */
private const val SAME_PLACE = 0.005

public fun RemotePlace.isSamePlaceAs(progress: Float): Boolean = abs(totalProgression - progress) < SAME_PLACE

internal data class KomgaBookProgress(
  /** Counted from 1. */
  val page: Int,
  val pageCount: Int,
  val completed: Boolean,
  val modified: Long,
)

internal enum class SyncAction {
  Pull,
  Push,
  Nothing,
}

/** The newer place wins. Unsent changes here only go out when the server has nothing newer. */
internal fun syncAction(
  positionModified: Long,
  syncedPosition: Long,
  remoteModified: Long?,
): SyncAction = when {
  remoteModified != null && remoteModified > positionModified -> SyncAction.Pull
  positionModified > syncedPosition -> SyncAction.Push
  else -> SyncAction.Nothing
}

internal sealed interface KomgaUpdate {
  data object Unread : KomgaUpdate
  data object Read : KomgaUpdate

  /** Counted from 1. */
  data class Page(val page: Int) : KomgaUpdate

  data class Progression(
    val totalProgression: Double,
    val href: String?,
    val progression: Double?,
  ) : KomgaUpdate
}

/**
 * What to tell the server about the place in a book. [readiumLocator] only counts while it's where the reader is, as the
 * old book engine doesn't move it.
 */
internal fun komgaUpdate(
  format: EbookFormat,
  progress: Float,
  section: Int,
  lastOpened: Long,
  readiumLocator: String?,
): KomgaUpdate {
  return when {
    progress >= 0.99F -> KomgaUpdate.Read
    progress == 0F && lastOpened == 0L -> KomgaUpdate.Unread
    format == EbookFormat.Pdf -> KomgaUpdate.Page(section + 1)
    else -> {
      val locator = readiumLocator?.let(::parseObject)
      val locations = locator?.obj("locations")
      val current = locations?.double("totalProgression")?.let { abs(it - progress) < 0.0001 } == true
      KomgaUpdate.Progression(
        totalProgression = progress.toDouble(),
        href = locator?.string("href")?.takeIf { current }?.let(::decodeHref),
        progression = locations?.double("progression")?.takeIf { current },
      )
    }
  }
}

/**
 * The position of the server at or right before [update]. Komga only takes the progressions of its own positions, so
 * the one sent is always one of them.
 */
internal fun positionAt(
  positions: List<JsonObject>,
  update: KomgaUpdate.Progression,
): JsonObject? {
  if (positions.isEmpty()) return null
  if (update.href != null) {
    val inFile = positions.filter { it.string("href")?.let(::decodeHref) == update.href }
    if (inFile.isNotEmpty()) {
      val progression = update.progression ?: 0.0
      return inFile.lastOrNull { (it.obj("locations")?.double("progression") ?: 0.0) <= progression } ?: inFile.first()
    }
  }
  val index = (update.totalProgression * positions.size).toInt().coerceIn(0, positions.lastIndex)
  return positions[index]
}

internal fun remotePlace(
  format: EbookFormat,
  book: KomgaBookProgress,
  locator: JsonObject?,
): RemotePlace {
  val locations = locator?.obj("locations")
  val pageCount = book.pageCount.coerceAtLeast(1)
  val totalProgression = when {
    book.completed -> 1.0
    format == EbookFormat.Pdf -> (book.page - 1).toDouble() / (pageCount - 1).coerceAtLeast(1)
    else -> locations?.double("totalProgression") ?: (book.page.toDouble() / pageCount)
  }.coerceIn(0.0, 1.0)
  return RemotePlace(
    modified = book.modified,
    totalProgression = totalProgression,
    href = locator?.string("href")?.takeIf { it.isNotEmpty() && !book.completed }?.let(::decodeHref),
    progression = locations?.double("progression")?.takeIf { !book.completed },
    page = if (format == EbookFormat.Pdf) (if (book.completed) pageCount else book.page) - 1 else null,
    completed = book.completed,
  )
}

/** The progress of a book from `/api/v1/books/{id}`, null when it wasn't read on the server. */
internal fun parseBookProgress(json: String): KomgaBookProgress? {
  val root = parseObject(json) ?: throw IllegalArgumentException("Not a book")
  val progress = root.obj("readProgress") ?: return null
  return KomgaBookProgress(
    page = progress.int("page") ?: 1,
    pageCount = root.obj("media")?.int("pagesCount") ?: 1,
    completed = progress.boolean("completed") ?: false,
    modified = progress.string("readDate")?.let(::parseInstant) ?: return null,
  )
}

/** The locator from `/api/v1/books/{id}/progression`. */
internal fun parseProgressionLocator(json: String): JsonObject? = parseObject(json)?.obj("locator")

internal fun parsePositions(json: String): List<JsonObject> {
  return (parseObject(json)?.get("positions") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
}

internal fun parseInstant(text: String): Long? {
  return try {
    OffsetDateTime.parse(text).toInstant().toEpochMilli()
  } catch (_: DateTimeParseException) {
    try {
      Instant.parse(text).toEpochMilli()
    } catch (_: DateTimeParseException) {
      null
    }
  }
}

internal fun formatInstant(millis: Long): String = Instant.ofEpochMilli(millis).toString()

// Komga takes the paths decoded, Readium keeps them as urls
private fun decodeHref(href: String): String {
  val path = href.substringBefore('#').removePrefix("/")
  return try {
    URLDecoder.decode(path.replace("+", "%2B"), Charsets.UTF_8.name())
  } catch (_: IllegalArgumentException) {
    path
  }
}

private fun parseObject(json: String): JsonObject? {
  return try {
    Json.parseToJsonElement(json) as? JsonObject
  } catch (_: IllegalArgumentException) {
    // a SerializationException is one
    null
  }
}

private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

private fun JsonObject.primitive(key: String): JsonPrimitive? = get(key) as? JsonPrimitive

private fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.double(key: String): Double? = primitive(key)?.doubleOrNull

private fun JsonObject.int(key: String): Int? = primitive(key)?.intOrNull

private fun JsonObject.boolean(key: String): Boolean? = primitive(key)?.booleanOrNull
