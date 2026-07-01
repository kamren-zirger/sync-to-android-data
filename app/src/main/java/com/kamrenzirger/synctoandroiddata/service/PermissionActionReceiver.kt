package com.kamrenzirger.synctoandroiddata.service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.kamrenzirger.synctoandroiddata.util.NotificationHelper
import com.kamrenzirger.synctoandroiddata.util.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
class PermissionActionReceiver : BroadcastReceiver() {
    private val receiverScope = CoroutineScope(Dispatchers.IO + Job())
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_GRANT_ACCESSIBILITY) {
            val id = intent.getIntExtra("notificationId", -1)
            val title = intent.getStringExtra("title") ?: ""
            val message = intent.getStringExtra("message") ?: ""
            val actionText = intent.getStringExtra("actionText") ?: ""
            val actionIntent: Intent? = intent.getParcelableExtra("actionIntent")
            if (ShizukuHelper.isShizukuAvailable() && ShizukuHelper.checkPermission(0)) {
                receiverScope.launch {
                    NotificationHelper.showPermissionAlert(
                        context, id, title, message, actionText, 
                        actionIntent ?: Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                        showLoading = true
                    )
                    val success = ShizukuHelper.grantAccessibility(context, SyncAccessibilityService::class.java)
                    if (success) {
                        NotificationHelper.dismissNotification(context, id)
                    } else {
                        NotificationHelper.showPermissionAlert(
                            context, id, title, message, actionText, 
                            actionIntent ?: Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                            showLoading = false
                        )
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                }
            } else {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }
    companion object {
        const val ACTION_GRANT_ACCESSIBILITY = "com.kamrenzirger.synctoandroiddata.ACTION_GRANT_ACCESSIBILITY"
    }
}
