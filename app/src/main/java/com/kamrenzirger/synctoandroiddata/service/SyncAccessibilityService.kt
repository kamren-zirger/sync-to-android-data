package com.kamrenzirger.synctoandroiddata.service
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.kamrenzirger.synctoandroiddata.R
import com.kamrenzirger.synctoandroiddata.data.AppDatabase
import com.kamrenzirger.synctoandroiddata.data.SyncEntryWithPairs
import com.kamrenzirger.synctoandroiddata.util.NotificationHelper
import com.kamrenzirger.synctoandroiddata.util.SettingsManager
import com.kamrenzirger.synctoandroiddata.util.ShizukuHelper
import com.kamrenzirger.synctoandroiddata.util.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class SyncAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var lastPackageName: CharSequence? = null
    private fun isIgnoredPackage(packageName: String): Boolean {
        val ignoredList = listOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.incallui",
            "com.google.android.googlequicksearchbox",
            "com.sec.android.inputmethod",
            "com.microsoft.emmx", 
            applicationContext.packageName
        )
        return ignoredList.contains(packageName)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            if (isIgnoredPackage(packageName)) {
                return
            }
            if (packageName != lastPackageName?.toString()) {
                handlePackageChange(lastPackageName?.toString(), packageName)
                lastPackageName = packageName
            }
        }
    }
    private fun handlePackageChange(oldPackage: String?, newPackage: String?) {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val myPackage = applicationContext.packageName
            if (newPackage != null && newPackage != myPackage && newPackage != oldPackage) {
                val entries = db.syncEntryDao().getSyncEntriesWithPairsForPackage(newPackage)
                if (entries.isNotEmpty()) {
                    for (entryWithPairs in entries) {
                        if (entryWithPairs.entry.isEnabled) {
                            performSync(entryWithPairs, isOpening = true)
                        }
                    }
                }
            }
            if (oldPackage != null && oldPackage != newPackage && oldPackage != myPackage) {
                val entries = db.syncEntryDao().getSyncEntriesWithPairsForPackage(oldPackage)
                if (entries.isNotEmpty()) {
                    for (entryWithPairs in entries) {
                        if (entryWithPairs.entry.isEnabled) {
                            performSync(entryWithPairs, isOpening = false)
                        }
                    }
                }
            }
        }
    }
    private suspend fun performSync(entryWithPairs: SyncEntryWithPairs, isOpening: Boolean) {
        val entry = entryWithPairs.entry
        val pairs = entryWithPairs.pairs
        if (pairs.isEmpty()) {
            return
        }
        val settings = SettingsManager(applicationContext)
        if (!ShizukuHelper.isShizukuAvailable() || !ShizukuHelper.checkPermission(101)) {
            if (settings.showToasts) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.sync_failed_shizuku, entry.appName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            val shizukuIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") 
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
            NotificationHelper.showPermissionAlert(
                applicationContext,
                NotificationHelper.ID_SHIZUKU_ALERT,
                getString(R.string.shizuku_not_authorized_title),
                getString(R.string.shizuku_not_authorized_description),
                getString(R.string.btn_authorize_now),
                shizukuIntent
            )
            return
        }
        var allSuccess = true
        for (pair in pairs) {
            val rawSrc = if (isOpening) pair.externalPath else pair.internalPath
            val rawDst = if (isOpening) pair.internalPath else pair.externalPath
            val src = resolvePath(rawSrc)
            val dst = resolvePath(rawDst)
            if (!dst.startsWith("/storage/emulated/0/Android/data")) {
                if (!StorageUtils.isStorageMounted(dst)) {
                    showErrorToast(getString(R.string.toast_storage_unmounted, dst), settings)
                    allSuccess = false
                    continue
                }
                if (!StorageUtils.isExternalPathReady(dst)) {
                    showErrorToast(getString(R.string.toast_path_inaccessible, dst), settings)
                    allSuccess = false
                    continue
                }
                if (!StorageUtils.hasSufficientSpace(dst)) {
                    showErrorToast(getString(R.string.toast_storage_low_space, dst), settings)
                    allSuccess = false
                    continue
                }
            }
            if (entry.mirrorDeletions) {
                val mirrorResult = ShizukuHelper.mirrorDeletions(src, dst)
                if (mirrorResult.isFailure) {
                    allSuccess = false
                }
            }
            val result = ShizukuHelper.executeCp(src, dst)
            if (!result.isSuccess) {
                allSuccess = false
            }
        }
        val statusStr = if (allSuccess) getString(R.string.sync_status_success) else getString(R.string.sync_status_failed)
        val emoji = if (allSuccess) "✅" else "❌"
        val directionLabel = if (isOpening) getString(R.string.sync_dir_ext_to_int) else getString(R.string.sync_dir_int_to_ext)
        val message = if (allSuccess) {
            getString(R.string.notif_success_msg, if (isOpening) "External -> Internal" else "Internal -> External", entry.appName)
        } else {
            getString(R.string.notif_error_msg, entry.appName)
        }
        if (settings.showToasts) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.sync_status_toast, emoji, statusStr, directionLabel, entry.appName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        NotificationHelper.showNotification(
            applicationContext, 
            getString(R.string.notif_sync_status, statusStr), 
            message
        )
    }
    private suspend fun showErrorToast(message: String, settings: SettingsManager) {
        if (settings.showToasts) {
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun resolvePath(path: String): String {
        if (path.startsWith("/")) return path
        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
        return "$root/$path".replace("//", "/")
    }
    override fun onInterrupt() {
    }
}
