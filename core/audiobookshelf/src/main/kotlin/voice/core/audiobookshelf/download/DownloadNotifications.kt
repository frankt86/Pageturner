package voice.core.audiobookshelf.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import voice.core.data.Book
import voice.core.data.BookId
import voice.core.playback.notification.MainActivityIntentProvider
import java.io.File
import java.text.NumberFormat
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

internal const val DOWNLOAD_NOTIFICATION_ID = 4242
internal const val DOWNLOAD_CHANNEL_ID = "audiobookshelfDownloads"
private const val RESULT_NOTIFICATION_ID = 4243
private const val LIBRARY_REQUEST_CODE = 4244
private const val COVER_SIZE_PX = 256

/** A book on its way to the device, as the notification shows it. */
internal data class ActiveDownload(
  val book: Book,
  val state: BookDownloadState.Downloading,
)

/** A book that downloads, as its notification shows it. */
public data class DownloadProgress(
  val name: String,
  val cover: File?,
  val state: BookDownloadState.Downloading,
)

/**
 * The notifications of the downloads: one with the progress while books download, and one per book once it's
 * ready or failed, for listeners who left Pageturner in the meantime. Books from catalogs download with them too.
 */
@SingleIn(AppScope::class)
@Inject
public class DownloadNotifications internal constructor(
  private val context: Context,
  private val mainActivityIntentProvider: MainActivityIntentProvider,
) {

  private val notificationManager = NotificationManagerCompat.from(context)
  private val covers = LruCache<String, Bitmap>(8)
  private val percentFormat = NumberFormat.getPercentInstance()

  @Volatile
  internal var appVisible: Boolean = false

  public fun createChannel() {
    notificationManager.createNotificationChannel(
      NotificationChannelCompat.Builder(DOWNLOAD_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
        .setName(context.getString(StringsR.string.audiobookshelf_downloads_channel))
        .build(),
    )
  }

  internal fun progress(downloads: List<ActiveDownload>): Notification {
    return progress(
      downloads = downloads.map { DownloadProgress(it.book.name, it.book.cover, it.state) },
      stop = DownloadActionReceiver.stop(context, downloads.map { it.book.id }),
    )
  }

  /** The notification while books download. [stop] is the intent of its button that stops them. */
  public fun progress(
    downloads: List<DownloadProgress>,
    stop: PendingIntent,
  ): Notification {
    val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setSilent(true)
      .setContentIntent(openLibrary())
    if (downloads.isEmpty()) {
      return builder
        .setContentTitle(context.getString(StringsR.string.audiobookshelf_download_notification_title))
        .setProgress(0, 0, true)
        .build()
    }
    val single = downloads.singleOrNull()
    if (single != null) {
      builder
        .setContentTitle(single.name)
        .setLargeIcon(single.cover?.let(::cover))
    } else {
      builder
        .setContentTitle(
          context.resources.getQuantityString(
            StringsR.plurals.audiobookshelf_download_notification_books,
            downloads.size,
            downloads.size,
          ),
        )
    }
    val waitingFor = downloads.firstNotNullOfOrNull { it.state.waitingFor }
    val progress = downloads.map { it.state.progress }.average().toFloat()
    // a server that doesn't tell the size gets a moving bar instead of one stuck at the start
    val sizeUnknown = downloads.all { it.state.totalBytes <= 0 && it.state.progress <= 0F }
    val text = when {
      waitingFor != null -> waitingText(waitingFor)
      single != null -> progressText(single.state)
      else -> downloads.joinToString { it.name }
    }
    return builder
      .setContentText(text)
      .setProgress(100, (progress * 100).roundToInt(), sizeUnknown)
      .addAction(
        0,
        context.getString(StringsR.string.audiobookshelf_download_notification_stop),
        stop,
      )
      .build()
  }

  internal fun progressText(state: BookDownloadState.Downloading): String {
    return if (state.totalBytes > 0) {
      context.getString(
        StringsR.string.book_download_progress,
        Formatter.formatShortFileSize(context, state.downloadedBytes),
        Formatter.formatShortFileSize(context, state.totalBytes),
      )
    } else if (state.progress <= 0F && state.downloadedBytes > 0) {
      Formatter.formatShortFileSize(context, state.downloadedBytes)
    } else {
      percentFormat.format(state.progress)
    }
  }

  private fun waitingText(waitingFor: WaitingFor): String = context.getString(
    when (waitingFor) {
      WaitingFor.Wifi -> StringsR.string.book_download_waiting_for_wifi
      WaitingFor.Connection -> StringsR.string.book_download_waiting_for_connection
    },
  )

  internal fun downloaded(book: Book) {
    downloaded(book.id.value, book.name, book.cover)
  }

  /** Tells that a book is ready, unless Pageturner is open and shows it itself. [tag] tells the books apart. */
  public fun downloaded(
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
        .setContentText(context.getString(StringsR.string.audiobookshelf_download_notification_done))
        .setLargeIcon(cover?.let(::cover))
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setContentIntent(openLibrary())
        .setAutoCancel(true)
        .setSilent(true)
        .build(),
    )
  }

  internal fun failed(book: Book) {
    failed(book.id.value, book.name, book.cover, DownloadActionReceiver.retry(context, book.id))
  }

  /** Tells that a download failed, unless Pageturner is open and shows it itself. */
  public fun failed(
    tag: String,
    name: String,
    cover: File?,
    retry: PendingIntent?,
  ) {
    if (appVisible) return
    val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_error)
      .setContentTitle(name)
      .setContentText(context.getString(StringsR.string.book_download_failed))
      .setLargeIcon(cover?.let(::cover))
      .setCategory(NotificationCompat.CATEGORY_ERROR)
      .setContentIntent(openLibrary())
      .setAutoCancel(true)
      .setSilent(true)
    if (retry != null) {
      builder.addAction(0, context.getString(StringsR.string.audiobookshelf_download_notification_retry), retry)
    }
    notifyResult(tag, builder.build())
  }

  internal fun cancelResult(bookId: BookId) {
    cancelResult(bookId.value)
  }

  public fun cancelResult(tag: String) {
    notificationManager.cancel(tag, RESULT_NOTIFICATION_ID)
  }

  internal fun cancelResults() {
    notificationManager.activeNotifications
      .filter { it.id == RESULT_NOTIFICATION_ID }
      .forEach { notificationManager.cancel(it.tag, it.id) }
  }

  @SuppressLint("MissingPermission")
  private fun notifyResult(
    tag: String,
    notification: Notification,
  ) {
    val allowed = Build.VERSION.SDK_INT < 33 ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    if (!allowed) return
    createChannel()
    notificationManager.notify(tag, RESULT_NOTIFICATION_ID, notification)
  }

  private fun openLibrary(): PendingIntent = PendingIntent.getActivity(
    context,
    LIBRARY_REQUEST_CODE,
    mainActivityIntentProvider.libraryIntent(),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )

  private fun cover(file: File): Bitmap? {
    if (!file.exists()) return null
    // the server can change a cover, which rewrites its file
    val key = "${file.path}:${file.lastModified()}"
    covers.get(key)?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= COVER_SIZE_PX && bounds.outHeight / (sampleSize * 2) >= COVER_SIZE_PX) {
      sampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
      ?: return null
    covers.put(key, bitmap)
    return bitmap
  }
}

internal fun Intent.withBookIds(bookIds: List<BookId>): Intent = putExtra(EXTRA_BOOK_IDS, bookIds.map { it.value }.toTypedArray())

internal fun Intent.bookIds(): Set<BookId> = getStringArrayExtra(EXTRA_BOOK_IDS).orEmpty().map(::BookId).toSet()

private const val EXTRA_BOOK_IDS = "bookIds"
