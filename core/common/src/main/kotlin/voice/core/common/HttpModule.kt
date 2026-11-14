package voice.core.common

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.OkHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
object HttpModule {

  // Clients that need their own settings derive from this one with newBuilder(), so all of them
  // share one connection pool and its threads
  @Provides
  @SingleIn(AppScope::class)
  fun okHttpClient(appInfoProvider: AppInfoProvider): OkHttpClient {
    // Wikimedia blocks the default user agent of OkHttp and asks for a version and a contact
    val userAgent =
      "Pageturner/${appInfoProvider.versionName} (https://github.com/frankt86/Pageturner)"
    return OkHttpClient.Builder()
      .addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
      }
      .build()
  }
}
