package voice.core.opds

import dev.zacsweers.metro.Inject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import voice.core.audiobookshelf.http.ServerHttpClient
import voice.core.logging.api.Logger
import voice.core.opds.feed.OpdsFeed
import voice.core.opds.feed.OpdsParseException
import voice.core.opds.feed.OpdsSearch
import voice.core.opds.feed.expandSearchTemplate
import voice.core.opds.feed.inEnglish
import voice.core.opds.feed.parseFeed
import voice.core.opds.feed.parseOpenSearchTemplate
import voice.core.opds.librivox.LibriVoxClient

public sealed interface FeedResult {
  public data class Success(val feed: OpdsFeed) : FeedResult

  public data object WrongCredentials : FeedResult

  public data object Unreachable : FeedResult

  public data object NotACatalog : FeedResult
}

// a page of books in other languages only would look like the end of the catalog
private const val MAX_SKIPPED_PAGES = 4

@Inject
public class OpdsClient(
  private val http: ServerHttpClient,
  private val libriVox: LibriVoxClient,
) {

  public suspend fun feed(
    catalog: OpdsCatalog,
    url: String = catalog.feedUrl,
  ): FeedResult {
    val result = load(catalog, url)
    if (result !is FeedResult.Success) return result
    var feed = result.feed.inEnglish(keepUntagged = isEnglishOnServer(url))
    var skipped = 0
    while (
      feed.publications.isEmpty() && feed.navigation.isEmpty() && skipped < MAX_SKIPPED_PAGES
    ) {
      val next = feed.nextUrl ?: break
      val page = load(catalog, next) as? FeedResult.Success ?: break
      feed =
        feed.copy(
          publications = page.feed.inEnglish(keepUntagged = isEnglishOnServer(next)).publications,
          nextUrl = page.feed.nextUrl?.takeIf { it != next },
        )
      skipped++
    }
    return FeedResult.Success(feed)
  }

  private suspend fun load(
    catalog: OpdsCatalog,
    url: String,
  ): FeedResult {
    if (catalog.type == CatalogType.LibriVox) return libriVox.feed(url)
    return withContext(Dispatchers.IO) {
      try {
        http.client
          .newCall(request(catalog, url) ?: return@withContext FeedResult.NotACatalog)
          .execute()
          .use { response ->
            when {
              response.code == 401 || response.code == 403 -> FeedResult.WrongCredentials
              response.code >= 500 -> FeedResult.Unreachable
              !response.isSuccessful -> FeedResult.NotACatalog
              else ->
                FeedResult.Success(
                  parseFeed(
                    body = response.body.string(),
                    url = response.request.url.toString(),
                    contentType = response.header("Content-Type"),
                  ),
                )
            }
          }
      } catch (_: IOException) {
        FeedResult.Unreachable
      } catch (e: OpdsParseException) {
        Logger.w(e, "Could not read a feed")
        FeedResult.NotACatalog
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // a broken answer must show as an error, not take the app down
        Logger.w(e, "Could not load a feed")
        FeedResult.Unreachable
      }
    }
  }

  /** The url of the results for [query], or null when the catalog didn't say how it searches. */
  public suspend fun searchUrl(
    catalog: OpdsCatalog,
    search: OpdsSearch,
    query: String,
  ): String? {
    val template =
      when (search) {
        is OpdsSearch.Template -> search.template
        is OpdsSearch.Description -> openSearchTemplate(catalog, search.url)
      }
    template ?: return null
    return expandSearchTemplate(template, englishSearchQuery(template, query), language = "en")
  }

  private suspend fun openSearchTemplate(
    catalog: OpdsCatalog,
    url: String,
  ): String? =
    withContext(Dispatchers.IO) {
      try {
        val request = request(catalog, url) ?: return@withContext null
        http.client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@withContext null
          parseOpenSearchTemplate(response.body.string(), response.request.url.toString())
        }
      } catch (_: IOException) {
        null
      } catch (e: OpdsParseException) {
        Logger.w(e, "Could not read a search description")
        null
      }
    }
}

/**
 * The search terms that find books in English only, for the catalogs that take a language in the
 * terms.
 */
internal fun englishSearchQuery(
  template: String,
  query: String,
): String {
  val host = template.substringAfter("://").substringBefore('/').substringBefore('{')
  return when (host.removePrefix("www.").removePrefix("m.")) {
    "gutenberg.org" -> "l.en ${query.trim()}"
    "archive.org" -> "(${query.trim()}) $IA_ENGLISH"
    else -> query
  }
}

/**
 * Whether the catalog itself only sends books in English to [url], so a book without a language is
 * in English too.
 */
internal fun isEnglishOnServer(url: String): Boolean {
  val httpUrl = url.toHttpUrlOrNull() ?: return false
  val query = httpUrl.queryParameter("query").orEmpty()
  return when (httpUrl.host.removePrefix("www.").removePrefix("m.")) {
    "gutenberg.org" -> "l.en" in query.split(' ')
    "archive.org" -> query.endsWith(IA_ENGLISH)
    "ws-export.wmcloud.org" -> httpUrl.encodedPath.startsWith("/opds/en/")
    else -> false
  }
}

private const val IA_ENGLISH = "AND language:(eng OR English)"

internal fun request(
  catalog: OpdsCatalog,
  url: String,
): Request? {
  val httpUrl = url.toHttpUrlOrNull() ?: return null
  return Request.Builder()
    .url(httpUrl)
    .header(
      "Accept",
      "application/opds+json, application/atom+xml, application/xml;q=0.9, */*;q=0.8",
    )
    .apply { catalog.authorizationFor(url)?.let { header("Authorization", it) } }
    .build()
}
