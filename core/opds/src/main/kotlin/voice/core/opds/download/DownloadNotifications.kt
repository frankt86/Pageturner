package voice.core.opds.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.text.format.Formatter
import android.util.LruCache
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.text.NumberFormat
import kotlin.math.roundToInt
import voice.core.playback.notification.MainActivityIntentProvider
import voice.core.strings.R as StringsR

// the id of the channel listeners already have, so their settings for it stay
private const val DOWNLOAD_CHANNEL_ID = "audiobookshelfDownloads"
private const val RESULT_NOTIFICATION_ID = 4243
private const val LIBRARY_REQUEST_CODE = 4244
private const val COVER_SIZE_PX = 256

/** A book that downloads, as its notification shows it. */
internal data class DownloadProgress(
  val name: String,
  val cover: File?,
  val progress: Float,
  val downloadedBytes: Long,
  /** The size of the whole book, or 0 when the catalog didn't tell. */
  val totalBytes: Long,
)

/**
 * The notifications of the downloads: one with the progress while books download, and one per book
 * once it's ready or failed, for listeners who left Pageturner in the meantime.
 */
@SingleIn(AppScope::class)
@Inject
internal class DownloadNotifications(
  private val context: Context,
  private val mainActivityIntentProvider: MainActivityIntentProvider,
) {

  private val notificationManager = NotificationManagerCompat.from(context)
  private val covers = LruCache<String, Bitmap>(8)
  private val percentFormat = NumberFormat.getPercentInstance()

  @Volatile var appVisible: Boolean = false

  fun createChannel() {
    notificationManager.createNotificationChannel(
      NotificationChannelCompat.Builder(
          DOWNLOAD_CHANNEL_ID,
          NotificationManagerCompat.IMPORTANCE_LOW,
        )
        .setName(context.getString(StringsR.string.downloads_channel))
        .build(),
    )
  }

  /** The notification while books download. [stop] is the intent of its button that stops them. */
  fun progress(
    downloads: List<DownloadProgress>,
    stop: PendingIntent,
  ): Notification {
    val builder =
      NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setSilent(true)
        .setContentIntent(openLibrary())
    if (downloads.isEmpty()) {
      return builder
        .setContentTitle(context.getString(StringsR.string.download_notification_title))
        .setProgress(0, 0, true)
        .build()
    }
    val single = downloads.singleOrNull()
    if (single != null) {
      builder.setContentTitle(single.name).setLargeIcon(single.cover?.let(::cover))
    } else {
      builder.setContentTitle(
        context.resources.getQuantityString(
          StringsR.plurals.download_notification_books,
          downloads.size,
          downloads.size,
        ),
      )
    }
    val progress = downloads.map { it.progress }.average().toFloat()
    // a catalog that doesn't tell the size gets a moving bar instead of one stuck at the start
    val sizeUnknown = downloads.all { it.totalBytes <= 0 && it.progress <= 0F }
    val text = if (single != null) progressText(single) else downloads.joinToString { it.name }
    return builder
      .setContentText(text)
      .setProgress(100, (progress * 100).roundToInt(), sizeUnknown)
      .addAction(0, context.getString(StringsR.string.download_notification_stop), stop)
      .build()
  }

  private fun progressText(download: DownloadProgress): String {
    return if (download.totalBytes > 0) {
      context.getString(
        StringsR.string.book_download_progress,
        Formatter.formatShortFileSize(context, download.downloadedBytes),
        Formatter.formatShortFileSize(context, download.totalBytes),
      )
    } else if (download.progress <= 0F && download.downloadedBytes > 0) {
      Formatter.formatShortFileSize(context, download.downloadedBytes)
    } else {
      percentFormat.format(download.progress)
    }
  }

  /**
   * Tells that a book is ready, unless Pageturner is open and shows it itself. [tag] tells the
   * books apart.
   */
  fun downloaded(
    tag: String,
    name: String,
    cover: File?,
  ) {
    if (appVisible) return
    notifyResult(
      tag,
      NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(name)
        .setContentText(context.getString(StringsR.string.download_notification_done))
        .setLargeIcon(cover?.let(::cover))
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setContentIntent(openLibrary())
        .setAutoCancel(true)
        .setSilent(true)
        .build(),
    )
  }

  /** Tells that a download failed, unless Pageturner is open and shows it itself. */
  fun failed(
    tag: String,
    name: String,
  ) {
    if (appVisible) return
    notifyResult(
      tag,
      NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle(name)
        .setContentText(context.getString(StringsR.string.book_download_failed))
        .setCategory(NotificationCompat.CATEGORY_ERROR)
        .setContentIntent(openLibrary())
        .setAutoCancel(true)
        .setSilent(true)
        .build(),
    )
  }

  fun cancelResult(tag: String) {
    notificationManager.cancel(tag, RESULT_NOTIFICATION_ID)
  }

  @SuppressLint("MissingPermission")
  private fun notifyResult(
    tag: String,
    notification: Notification,
  ) {
    val allowed =
      Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    if (!allowed) return
    createChannel()
    notificationManager.notify(tag, RESULT_NOTIFICATION_ID, notification)
  }

  private fun openLibrary(): PendingIntent =
    PendingIntent.getActivity(
      context,
      LIBRARY_REQUEST_CODE,
      mainActivityIntentProvider.libraryIntent(),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun cover(file: File): Bitmap? {
    if (!file.exists()) return null
    val key = "${file.path}:${file.lastModified()}"
    covers.get(key)?.let {
      return it
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sampleSize = 1
    while (
      bounds.outWidth / (sampleSize * 2) >= COVER_SIZE_PX &&
        bounds.outHeight / (sampleSize * 2) >= COVER_SIZE_PX
    ) {
      sampleSize *= 2
    }
    val bitmap =
      BitmapFactory.decodeFile(
        file.path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
      ) ?: return null
    covers.put(key, bitmap)
    return bitmap
  }
}
