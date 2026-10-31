package voice.core.audiobookshelf.download

import android.app.Notification
import android.app.PendingIntent
import java.io.File

/** A book that downloads, as its notification shows it. */
public data class DownloadProgress(
  val name: String,
  val cover: File?,
  val state: BookDownloadState.Downloading,
)

/**
 * The notifications of the server downloads, for other downloads that show with them, like the books from the
 * catalogs. [tag] tells their books apart.
 */
public interface ServerDownloadNotifications {

  public fun createChannel()

  /** The notification while books download. [stop] is the intent of its button that stops them. */
  public fun progress(
    downloads: List<DownloadProgress>,
    stop: PendingIntent,
  ): Notification

  /** Tells that a book is ready, unless Pageturner is open and shows it itself. */
  public fun downloaded(
    tag: String,
    name: String,
    cover: File?,
  )

  /** Tells that a download failed, unless Pageturner is open and shows it itself. */
  public fun failed(
    tag: String,
    name: String,
    cover: File?,
    retry: PendingIntent?,
  )

  public fun cancelResult(tag: String)
}
