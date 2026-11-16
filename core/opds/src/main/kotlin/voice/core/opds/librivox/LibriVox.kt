package voice.core.opds.librivox

import dev.zacsweers.metro.Inject
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import voice.core.logging.api.Logger
import voice.core.opds.FeedResult
import voice.core.opds.feed.OpdsAcquisition
import voice.core.opds.feed.OpdsFeed
import voice.core.opds.feed.OpdsPublication
import voice.core.opds.feed.OpdsSearch
import voice.core.opds.feed.htmlToText
import voice.core.opds.feed.languageCodes
import voice.core.opds.http.OpdsHttpClient

internal const val LIBRIVOX_URL = "https://librivox.org/"
internal const val LIBRIVOX_PAGE_SIZE = 50

private const val API = "https://librivox.org/api/feed/audiobooks"
private const val SEARCH = "search"

private fun apiUrl(): HttpUrl.Builder =
  API.toHttpUrl()
    .newBuilder()
    .addQueryParameter("format", "json")
    .addQueryParameter("extended", "1")
    .addQueryParameter("limit", LIBRIVOX_PAGE_SIZE.toString())

/** The newest books first. */
internal fun libriVoxLatestUrl(): String =
  apiUrl()
    // only books that are done have a catalog date, so this leaves out the ones still recorded
    .addQueryParameter("since", "1")
    .addQueryParameter("sort_field", "id")
    .addQueryParameter("sort_order", "desc")
    .addQueryParameter("offset", "0")
    .build()
    .toString()

private val libriVoxSearch = OpdsSearch.Template("$API?$SEARCH={searchTerms}")

private val leadingArticle = Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE)

/**
 * The api only finds titles that start with the words, without the article, and authors by their
 * last name. So a search looks for both and takes the last word as the name.
 */
internal fun libriVoxSearchUrls(query: String): List<String> {
  val terms = query.trim().replace(Regex("\\s+"), " ")
  if (terms.isEmpty()) return emptyList()
  return listOf(
    apiUrl().addQueryParameter("title", "^" + terms.replace(leadingArticle, "")).build().toString(),
    apiUrl().addQueryParameter("author", terms.substringAfterLast(' ')).build().toString(),
  )
}

/** Reads LibriVox through its own api, as LibriVox has no OPDS catalog. */
@Inject
public class LibriVoxClient internal constructor(private val http: OpdsHttpClient) {

  internal suspend fun feed(url: String): FeedResult =
    withContext(Dispatchers.IO) {
      val httpUrl = url.toHttpUrlOrNull() ?: return@withContext FeedResult.NotACatalog
      try {
        val query = httpUrl.queryParameter(SEARCH)
        val feed =
          if (query != null) {
            val books = libriVoxSearchUrls(query).flatMap { books(it) }
            libriVoxFeed(books, nextUrl = null)
          } else {
            val books = books(url)
            val offset = httpUrl.queryParameter("offset")?.toIntOrNull() ?: 0
            val nextUrl =
              if (books.size < LIBRIVOX_PAGE_SIZE) {
                null
              } else {
                httpUrl
                  .newBuilder()
                  .setQueryParameter("offset", (offset + LIBRIVOX_PAGE_SIZE).toString())
                  .build()
                  .toString()
              }
            libriVoxFeed(books, nextUrl)
          }
        FeedResult.Success(feed)
      } catch (_: IOException) {
        FeedResult.Unreachable
      } catch (e: SerializationException) {
        Logger.w(e, "Could not read LibriVox")
        FeedResult.NotACatalog
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Logger.w(e, "Could not load LibriVox")
        FeedResult.Unreachable
      }
    }

  private fun books(url: String): List<LibriVoxBook> {
    val request = Request.Builder().url(url).header("Accept", "application/json").build()
    return http.client.newCall(request).execute().use { response ->
      when {
        // the api answers a search without results with an error
        response.code == 404 -> emptyList()
        !response.isSuccessful -> throw IOException("HTTP ${response.code}")
        else -> parseLibriVoxBooks(response.body.string())
      }
    }
  }
}

internal fun libriVoxFeed(
  books: List<LibriVoxBook>,
  nextUrl: String?,
): OpdsFeed {
  return OpdsFeed(
    title = null,
    navigation = emptyList(),
    publications = books.distinctBy { it.id }.mapNotNull { it.toPublication() },
    nextUrl = nextUrl,
    search = libriVoxSearch,
  )
}

private val json = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
}

/** The books of an answer of the api. A book that doesn't read is left out. */
internal fun parseLibriVoxBooks(body: String): List<LibriVoxBook> {
  val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
  val books = root["books"] as? JsonArray ?: return emptyList()
  return books.mapNotNull { book ->
    try {
      json.decodeFromJsonElement(LibriVoxBook.serializer(), book)
    } catch (e: SerializationException) {
      Logger.w(e, "Could not read a LibriVox book")
      null
    }
  }
}

@Serializable
internal data class LibriVoxBook(
  val id: String,
  val title: String? = null,
  val description: String? = null,
  val language: String? = null,
  val totaltimesecs: JsonPrimitive? = null,
  val authors: List<LibriVoxAuthor> = emptyList(),
  @SerialName("url_iarchive") val archiveUrl: String? = null,
  val sections: List<LibriVoxSection> = emptyList(),
)

@Serializable
internal data class LibriVoxAuthor(
  @SerialName("first_name") val firstName: String? = null,
  @SerialName("last_name") val lastName: String? = null,
)

@Serializable
internal data class LibriVoxSection(
  @SerialName("section_number") val number: String? = null,
  val title: String? = null,
  @SerialName("listen_url") val listenUrl: String? = null,
  val readers: List<LibriVoxReader> = emptyList(),
)

@Serializable
internal data class LibriVoxReader(
  @SerialName("display_name") val name: String? = null,
)

/**
 * The book as a publication, with one MP3 per section. Null for a book that has a section without
 * its recording yet.
 */
internal fun LibriVoxBook.toPublication(): OpdsPublication? {
  val title = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  if (sections.isEmpty() || sections.any { it.listenUrl.isNullOrBlank() }) return null
  val ordered = sections.sortedBy { it.number?.trim()?.toIntOrNull() ?: Int.MAX_VALUE }
  val sectionTitles = ordered.map { it.title?.trim().orEmpty() }
  val acquisitions = ordered.mapIndexed { index, section ->
    val sectionTitle = sectionTitles[index]
    // the files of a book need titles of their own, or they count as one file
    val unique = sectionTitle.isNotEmpty() && sectionTitles.count { it == sectionTitle } == 1
    OpdsAcquisition(
      url = section.listenUrl.orEmpty().trim(),
      type = "audio/mpeg",
      title = if (unique) sectionTitle else "${index + 1} $sectionTitle".trim(),
    )
  }
  val cover = archiveIdentifier(archiveUrl)?.let { "https://archive.org/services/img/$it" }
  return OpdsPublication(
    id = id,
    title = title,
    authors =
      authors.mapNotNull { author ->
        listOfNotNull(author.firstName?.trim(), author.lastName?.trim())
          .filter { it.isNotEmpty() }
          .joinToString(" ")
          .ifEmpty { null }
      },
    summary = description?.let(::htmlToText)?.ifEmpty { null },
    coverUrl = cover,
    thumbnailUrl = cover,
    acquisitions = acquisitions,
    isAudiobook = true,
    duration = totaltimesecs?.longOrNull?.takeIf { it > 0 }?.seconds,
    narrators =
      ordered
        .flatMap { section -> section.readers.mapNotNull { it.name?.trim() } }
        .filter { it.isNotEmpty() }
        .distinct(),
    languages = languageCodes(listOfNotNull(language)),
  )
}

// the api has no covers, but the Internet Archive keeps one for each of its items, like
// https://archive.org/details/<identifier>
private fun archiveIdentifier(url: String?): String? {
  val segments = url?.toHttpUrlOrNull()?.pathSegments ?: return null
  val details = segments.indexOf("details")
  if (details < 0) return null
  return segments.getOrNull(details + 1)?.takeIf { it.isNotBlank() }
}
