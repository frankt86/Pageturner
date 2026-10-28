package voice.core.opds.download

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import voice.core.opds.OpdsCatalog
import java.io.File
import java.io.IOException

private const val UNKNOWN_SIZE_REPORT_BYTES = 512 * 1024

/**
 * Downloads [request] into [target]. [onProgress] gets the bytes read and the size of the file, which is 0 while the
 * server doesn't tell it. [onCall] gets the call, as a read that waits for the server only stops once it's cancelled.
 */
internal suspend fun OkHttpClient.fetch(
  request: Request,
  target: File,
  onCall: (Call) -> Unit,
  onProgress: (read: Long, total: Long) -> Unit,
) {
  val call = newCall(request)
  onCall(call)
  call.execute().use { response ->
    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
    val total = response.body.contentLength().coerceAtLeast(0)
    var read = 0L
    var lastReported = -1L
    response.body.byteStream().use { input ->
      target.outputStream().use { output ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
          currentCoroutineContext().ensureActive()
          val count = input.read(buffer)
          if (count < 0) break
          output.write(buffer, 0, count)
          read += count
          // once per percent, or now and then while the size isn't known
          val step = if (total > 0) read * 100 / total else read / UNKNOWN_SIZE_REPORT_BYTES
          if (step != lastReported) {
            lastReported = step
            onProgress(read, total)
          }
        }
      }
    }
  }
}

internal fun downloadRequest(
  catalog: OpdsCatalog,
  url: String,
): Request {
  return Request.Builder()
    .url(url.toHttpUrlOrNull() ?: throw IOException("Not a url"))
    .apply { catalog.authorizationFor(url)?.let { header("Authorization", it) } }
    .build()
}
