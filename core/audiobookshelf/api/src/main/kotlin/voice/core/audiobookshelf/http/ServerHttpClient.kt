package voice.core.audiobookshelf.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * The client for the servers listeners run themselves, shared by Audiobookshelf and the OPDS
 * catalogs.
 */
@SingleIn(AppScope::class)
@Inject
public class ServerHttpClient(okHttpClient: OkHttpClient) {

  public val client: OkHttpClient =
    okHttpClient
      .newBuilder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()
}
