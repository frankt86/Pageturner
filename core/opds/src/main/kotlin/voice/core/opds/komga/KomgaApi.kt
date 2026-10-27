package voice.core.opds.komga

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import voice.core.audiobookshelf.http.ServerHttpClient
import voice.core.opds.OpdsCatalog
import java.io.IOException

/** The parts of Komga's own api that keep the reading progress. It takes the Basic auth of the catalog on every call. */
@Inject
public class KomgaApi(private val http: ServerHttpClient) {

  /** Null when the book wasn't read on the server. */
  internal suspend fun bookProgress(
    catalog: OpdsCatalog,
    bookId: String,
  ): KomgaBookProgress? = get(catalog, "books/$bookId")?.let(::parseBookProgress)

  internal suspend fun progressionLocator(
    catalog: OpdsCatalog,
    bookId: String,
  ): JsonObject? = get(catalog, "books/$bookId/progression")?.let(::parseProgressionLocator)

  internal suspend fun positions(
    catalog: OpdsCatalog,
    bookId: String,
  ): List<JsonObject> = get(catalog, "books/$bookId/positions")?.let(::parsePositions).orEmpty()

  /** Komga answers 409 when it has a newer place. */
  internal suspend fun putProgression(
    catalog: OpdsCatalog,
    bookId: String,
    modified: Long,
    locator: JsonObject,
  ): Int {
    val body = JsonObject(
      mapOf(
        "modified" to JsonPrimitive(formatInstant(modified)),
        "device" to JsonObject(mapOf("id" to JsonPrimitive(DEVICE), "name" to JsonPrimitive(DEVICE))),
        "locator" to locator,
      ),
    )
    return send(catalog, "books/$bookId/progression", "PUT", body.toString().toRequestBody(JSON))
  }

  internal suspend fun markPage(
    catalog: OpdsCatalog,
    bookId: String,
    page: Int,
  ): Int {
    val body = JsonObject(mapOf("page" to JsonPrimitive(page), "completed" to JsonPrimitive(false)))
    return send(catalog, "books/$bookId/read-progress", "PATCH", body.toString().toRequestBody(JSON))
  }

  internal suspend fun markRead(
    catalog: OpdsCatalog,
    bookId: String,
  ): Int {
    val body = JsonObject(mapOf("completed" to JsonPrimitive(true)))
    return send(catalog, "books/$bookId/read-progress", "PATCH", body.toString().toRequestBody(JSON))
  }

  internal suspend fun markUnread(
    catalog: OpdsCatalog,
    bookId: String,
  ): Int = send(catalog, "books/$bookId/read-progress", "DELETE", null)

  /** The body, or null when there is none. */
  private suspend fun get(
    catalog: OpdsCatalog,
    path: String,
  ): String? {
    val request = request(catalog, path).get().build()
    return withContext(Dispatchers.IO) {
      http.client.newCall(request).executeAsync().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        if (response.code == 204) null else response.body.string()
      }
    }
  }

  private suspend fun send(
    catalog: OpdsCatalog,
    path: String,
    method: String,
    body: RequestBody?,
  ): Int {
    val request = request(catalog, path).method(method, body).build()
    return withContext(Dispatchers.IO) {
      http.client.newCall(request).executeAsync().use { it.code }
    }
  }

  private fun request(
    catalog: OpdsCatalog,
    path: String,
  ): Request.Builder {
    val url = catalog.url.trimEnd('/') + "/api/v1/" + path
    return Request.Builder()
      .url(url.toHttpUrlOrNull() ?: throw IOException("Not a url"))
      // the progression and the positions come as Readium types
      .header("Accept", "application/json, */*;q=0.8")
      .apply { catalog.authorizationFor(url)?.let { header("Authorization", it) } }
  }
}

private const val DEVICE = "Pageturner"

private val JSON = "application/json".toMediaType()
