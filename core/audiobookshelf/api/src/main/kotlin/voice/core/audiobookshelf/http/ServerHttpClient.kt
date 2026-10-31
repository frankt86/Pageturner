package voice.core.audiobookshelf.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import okhttp3.OkHttpClient
import voice.core.common.AppInfoProvider
import java.util.concurrent.TimeUnit

/**
 * The client for the servers listeners run themselves, shared by Audiobookshelf and the OPDS catalogs.
 */
@SingleIn(AppScope::class)
@Inject
public class ServerHttpClient(appInfoProvider: AppInfoProvider) {

  private val userAgent = "Pageturner/${appInfoProvider.versionName}"

  public val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .addInterceptor { chain ->
      chain.proceed(
        chain.request().newBuilder()
          .header("User-Agent", userAgent)
          .build(),
      )
    }
    .build()
}
