package voice.core.opds.download

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import voice.core.common.rootGraphAs

private const val NOTIFICATION_ID = 4245
private const val ACTION_STOP = "voice.opds.download.STOP"

/** Keeps audiobooks from catalogs downloading while Pageturner is in the background, and shows their progress. */
public class OpdsDownloadService : Service() {

  private val downloads by lazy { rootGraphAs<OpdsDownloadGraph>().opdsAudiobookDownloads }
  private val scope = MainScope()
  private var observing = false
  private var lastStartId = 0

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    lastStartId = startId
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      downloads.currentNotification(),
      if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
    )
    if (intent?.action == ACTION_STOP) downloads.cancelAll()
    if (!observing) {
      observing = true
      scope.launch {
        downloads.notification.collect { notification ->
          if (notification == null) {
            ServiceCompat.stopForeground(this@OpdsDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // a download that started in between keeps the service
            stopSelf(lastStartId)
          } else {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
          }
        }
      }
    }
    return START_NOT_STICKY
  }

  // Android stops downloads in the background after a few hours
  override fun onTimeout(
    startId: Int,
    fgsType: Int,
  ) {
    downloads.cancelAll()
    stopSelf()
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  internal companion object {

    fun startIntent(context: Context): Intent = Intent(context, OpdsDownloadService::class.java)

    fun stopIntent(context: Context): PendingIntent = PendingIntent.getService(
      context,
      0,
      startIntent(context).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}

@ContributesTo(AppScope::class)
public interface OpdsDownloadGraph {
  public val opdsAudiobookDownloads: OpdsAudiobookDownloads
}
