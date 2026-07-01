package com.kamrenzirger.synctoandroiddata.service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.kamrenzirger.synctoandroiddata.R
import com.kamrenzirger.synctoandroiddata.service.SyncAccessibilityService
import com.kamrenzirger.synctoandroiddata.util.NotificationHelper
import com.kamrenzirger.synctoandroiddata.util.SettingsManager
import com.kamrenzirger.synctoandroiddata.util.ShizukuHelper
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val settings = SettingsManager(context)
            if (settings.setupCompleted && settings.startOnBoot) {
                checkPermissions(context)
            }
        }
    }
    private fun checkPermissions(context: Context) {
        val isShizukuInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: Exception) {
            false
        }
        val isShizukuAvailable = ShizukuHelper.isShizukuAvailable()
        val isShizukuAuthorized = ShizukuHelper.checkPermission(0)
        if (!isShizukuInstalled || !isShizukuAvailable || !isShizukuAuthorized) {
            val shizukuIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") 
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/"))
            val (title, message, action) = when {
                !isShizukuInstalled -> Triple(
                    context.getString(R.string.shizuku_not_installed_title),
                    context.getString(R.string.shizuku_not_installed_description),
                    context.getString(R.string.btn_get_shizuku)
                )
                !isShizukuAvailable -> Triple(
                    context.getString(R.string.shizuku_not_running_title),
                    context.getString(R.string.shizuku_not_running_description),
                    context.getString(R.string.btn_open_shizuku)
                )
                else -> Triple(
                    context.getString(R.string.shizuku_not_authorized_title),
                    context.getString(R.string.shizuku_not_authorized_description),
                    context.getString(R.string.btn_authorize_now)
                )
            }
            NotificationHelper.showPermissionAlert(
                context,
                NotificationHelper.ID_SHIZUKU_ALERT,
                title,
                message,
                action,
                shizukuIntent
            )
        }
        if (!isAccessibilityServiceEnabled(context, SyncAccessibilityService::class.java)) {
            val title = context.getString(R.string.accessibility_disabled_title)
            val message = context.getString(R.string.accessibility_disabled_description)
            val actionText = context.getString(R.string.btn_grant_permission)
            val accessIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val broadcastIntent = Intent(context, PermissionActionReceiver::class.java).apply {
                action = PermissionActionReceiver.ACTION_GRANT_ACCESSIBILITY
                putExtra("notificationId", NotificationHelper.ID_ACCESSIBILITY_ALERT)
                putExtra("title", title)
                putExtra("message", message)
                putExtra("actionText", actionText)
                putExtra("actionIntent", accessIntent)
            }
            NotificationHelper.showPermissionAlert(
                context,
                NotificationHelper.ID_ACCESSIBILITY_ALERT,
                title,
                message,
                actionText,
                broadcastIntent,
                isBroadcast = true
            )
        }
    }
    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = android.content.ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) return true
        }
        return false
    }
}
