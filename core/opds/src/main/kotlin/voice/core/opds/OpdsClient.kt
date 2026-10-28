package voice.core.opds

import dev.zacsweers.metro.Inject
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
import voice.core.opds.feed.parseFeed
import voice.core.opds.feed.parseOpenSearchTemplate
import java.io.IOException

public sealed interface FeedResult {
  public data class Success(val feed: OpdsFeed) : FeedResult
  public data object WrongCredentials : FeedResult
  public data object Unreachable : FeedResult
  public data object NotACatalog : FeedResult
}

@Inject
public class OpdsClient(private val http: ServerHttpClient) {

  public suspend fun feed(
    catalog: OpdsCatalog,
    url: String = catalog.feedUrl,
  ): FeedResult = withContext(Dispatchers.IO) {
    try {
      http.client.newCall(request(catalog, url) ?: return@withContext FeedResult.NotACatalog).execute().use { response ->
        when {
          response.code == 401 || response.code == 403 -> FeedResult.WrongCredentials
          response.code >= 500 -> FeedResult.Unreachable
          !response.isSuccessful -> FeedResult.NotACatalog
          else -> FeedResult.Success(
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

  /** The url of the results for [query], or null when the catalog didn't say how it searches. */
  public suspend fun searchUrl(
    catalog: OpdsCatalog,
    search: OpdsSearch,
    query: String,
  ): String? {
    val template = when (search) {
      is OpdsSearch.Template -> search.template
      is OpdsSearch.Description -> openSearchTemplate(catalog, search.url)
    }
    return template?.let { expandSearchTemplate(it, query) }
  }

  private suspend fun openSearchTemplate(
    catalog: OpdsCatalog,
    url: String,
  ): String? = withContext(Dispatchers.IO) {
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

internal fun request(
  catalog: OpdsCatalog,
  url: String,
): Request? {
  val httpUrl = url.toHttpUrlOrNull() ?: return null
  return Request.Builder()
    .url(httpUrl)
    .header("Accept", "application/opds+json, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
    .apply { catalog.authorizationFor(url)?.let { header("Authorization", it) } }
    .build()
}
