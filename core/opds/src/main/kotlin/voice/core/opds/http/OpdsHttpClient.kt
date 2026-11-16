package voice.core.opds.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** The client for the catalogs, which often run on servers that listeners run themselves. */
@SingleIn(AppScope::class)
@Inject
public class OpdsHttpClient(okHttpClient: OkHttpClient) {

  public val client: OkHttpClient =
    okHttpClient
      .newBuilder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build()
}
