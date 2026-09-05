package com.kamrenzirger.synctoandroiddata.util
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kamrenzirger.synctoandroiddata.R
object NotificationHelper {
    private const val CHANNEL_SYNC_STATUS = "sync_status_channel"
    private const val CHANNEL_SERVICE_ALERTS = "service_alerts_channel"
    private const val CHANNEL_UPDATES = "updates_channel"
    private const val NAME_SYNC_STATUS = "Sync Status"
    private const val NAME_SERVICE_ALERTS = "Service Alerts (Permissions)"
    private const val NAME_UPDATES = "App Updates"
    const val ID_SHIZUKU_ALERT = 1001
    const val ID_ACCESSIBILITY_ALERT = 1002
    private const val ID_SYNC_NOTIFICATION = 1003
    private const val ID_UPDATE_NOTIFICATION = 1004

    fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SYNC_STATUS, 
                NAME_SYNC_STATUS, 
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows notifications when files are being copied or mirrored."
            }
            notificationManager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_SYNC_STATUS)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        notificationManager.notify(ID_SYNC_NOTIFICATION, builder.build())
    }

    fun showUpdateNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_UPDATES, 
                NAME_UPDATES, 
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies you when a new version of the app is available."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val githubIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/kamren-zirger/sync-to-android-data/releases/latest"))
        val pendingIntent = PendingIntent.getActivity(
            context, ID_UPDATE_NOTIFICATION, githubIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.btn_download), pendingIntent)

        notificationManager.notify(ID_UPDATE_NOTIFICATION, builder.build())
    }
    fun showPermissionAlert(context: Context, id: Int, title: String, message: String, actionText: String, actionIntent: Intent, showLoading: Boolean = false, isBroadcast: Boolean = false) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SERVICE_ALERTS, 
                NAME_SERVICE_ALERTS, 
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts regarding Shizuku or Accessibility permissions."
            }
            notificationManager.createNotificationChannel(channel)
        }
        val pendingIntent = if (isBroadcast) {
            PendingIntent.getBroadcast(
                context, id, actionIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                context, id, actionIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, actionText, pendingIntent)
            .setContentIntent(pendingIntent)
            .setProgress(0, 0, showLoading)
        notificationManager.notify(id, builder.build())
    }
    fun dismissNotification(context: Context, id: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
    }
}
