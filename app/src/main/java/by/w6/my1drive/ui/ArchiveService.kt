package by.w6.my1drive.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import by.w6.my1drive.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ArchiveService : Service() {

    private val CHANNEL_ID = "ArchiveServiceChannel"
    private val NOTIFICATION_ID = 1001

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var progressJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(getString(by.w6.my1drive.R.string.service_sync_preparing))
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ requires foreground service type
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // API 34+
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                }
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        observeProgress()

        return START_NOT_STICKY
    }

    private fun observeProgress() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            val syncHelper = ArchiveSyncHelper.getInstance(application)
            
            // Collect archive state
            launch {
                syncHelper.archiveState.collect { state ->
                    if (!state.isArchiving && !syncHelper.isSilentSyncing) {
                        stopSelf() // Stop service when work is done
                    } else if (state.error != null) {
                        updateNotification(getString(by.w6.my1drive.R.string.service_error, state.error ?: ""))
                    }
                }
            }

            // Collect sync progress
            launch {
                syncHelper.syncProgressState.collect { progress ->
                    if (progress.totalFiles > 0) {
                        val text = getString(by.w6.my1drive.R.string.service_copying_progress, progress.currentFileIndex.toString(), progress.totalFiles.toString())
                        updateNotification(text)
                    }
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(by.w6.my1drive.R.string.service_archiving_title))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher) // Update to a proper icon if available
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(by.w6.my1drive.R.string.service_sync_archive_title),
                NotificationManager.IMPORTANCE_LOW // Low priority prevents sound/vibration
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
