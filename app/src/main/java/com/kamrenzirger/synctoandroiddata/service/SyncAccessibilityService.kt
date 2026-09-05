package com.kamrenzirger.synctoandroiddata.service
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.kamrenzirger.synctoandroiddata.R
import com.kamrenzirger.synctoandroiddata.data.AppDatabase
import com.kamrenzirger.synctoandroiddata.data.SyncEntryWithPairs
import com.kamrenzirger.synctoandroiddata.util.AppLogger
import com.kamrenzirger.synctoandroiddata.util.NotificationHelper
import com.kamrenzirger.synctoandroiddata.util.SettingsManager
import com.kamrenzirger.synctoandroiddata.util.ShizukuHelper
import com.kamrenzirger.synctoandroiddata.util.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var lastPackageName: String? = null
    private var pendingExitJob: Job? = null
    private var settlingJob: Job? = null

    companion object {
        private const val SETTLING_PERIOD_MS = 1000L // Period to wait for app/overlays to settle
        private const val EXIT_DEBOUNCE_MS = 1000L // Focus must be gone for 1s to trigger exit
        private const val TAG = "SyncAccessibilityService"
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        val ignoredList = listOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.incallui",
            "com.google.android.googlequicksearchbox",
            "com.sec.android.inputmethod",
            "com.microsoft.emmx",
            "com.rp.gameassistant",
            "com.rp.settings",
            "com.rp.mapping",
            "com.retroidpocket.gameassistant",
            "com.retroidpocket.gamelauncher",
            "com.retroidpocket.setupwizard",
            "com.draco.anyhome",
            "com.android.permissioncontroller",
            applicationContext.packageName
        )
        val isIgnored = ignoredList.contains(packageName)
        if (isIgnored) {
            AppLogger.e(TAG, "isIgnoredPackage: $packageName is ignored", applicationContext)
        }
        return isIgnored
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            if (isIgnoredPackage(packageName)) return
            
            val className = event.className?.toString() ?: "Unknown"
            val isFullScreen = event.isFullScreen
            val logMsg = "TYPE_WINDOW_STATE_CHANGED: package=$packageName, class=$className, fullScreen=$isFullScreen, lastPackageName=$lastPackageName"
            AppLogger.e(TAG, logMsg, applicationContext)

            if (packageName != lastPackageName) {
                handlePackageChange(lastPackageName, packageName)
                lastPackageName = packageName
            }
        }
    }

    private fun handlePackageChange(oldPackage: String?, newPackage: String?) {
        AppLogger.e(TAG, "handlePackageChange: oldPackage=$oldPackage, newPackage=$newPackage", applicationContext)
        
        // Cancel any pending exit sync since focus has changed
        pendingExitJob?.cancel()
        
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val myPackage = applicationContext.packageName
            
            // 1. Detect if the NEW package is a target app
            val entries = db.syncEntryDao().getSyncEntriesWithPairsForPackage(newPackage ?: "")
            if (entries.isNotEmpty()) {
                AppLogger.isSessionActive = true
                AppLogger.e(TAG, ">> Detected TARGET OPENING of $newPackage", applicationContext, force = true)
                AppLogger.e(TAG, ">> Session Started for $newPackage", applicationContext)
                
                // Trigger opening sync immediately
                for (entryWithPairs in entries) {
                    if (entryWithPairs.entry.isEnabled) {
                        performSync(entryWithPairs, isOpening = true)
                    }
                }

                // Start the settling period
                settlingJob?.cancel()
                settlingJob = serviceScope.launch {
                    AppLogger.e(TAG, "Starting ${SETTLING_PERIOD_MS}ms settling period...", applicationContext)
                    kotlinx.coroutines.delay(SETTLING_PERIOD_MS)
                    
                    // After 2 seconds, whatever is currently focused becomes the new "baseline"
                    // We update lastPackageName to the current focus to "swallow" any overlays
                    val currentFocus = lastPackageName 
                    AppLogger.e(TAG, "Settling period finished. New baseline focus: $currentFocus", applicationContext)
                }
                return@launch
            }

            // 2. Handle Closing sync for the OLD package
            if (oldPackage != null && oldPackage != newPackage && oldPackage != myPackage) {
                val oldEntries = db.syncEntryDao().getSyncEntriesWithPairsForPackage(oldPackage)
                if (oldEntries.isNotEmpty()) {
                    
                    // If we are still in the settling period, don't trigger exit yet
                    if (settlingJob?.isActive == true) {
                        AppLogger.e(TAG, "Focus changed to $newPackage during settling period. Ignoring exit from $oldPackage.", applicationContext)
                        return@launch
                    }

                    // Start a stable exit check
                    pendingExitJob = serviceScope.launch {
                        AppLogger.e(TAG, "Target $oldPackage lost focus. Waiting ${EXIT_DEBOUNCE_MS}ms to verify exit...", applicationContext)
                        kotlinx.coroutines.delay(EXIT_DEBOUNCE_MS)
                        
                        AppLogger.e(TAG, "<< Detected STABLE CLOSING of $oldPackage (now: $newPackage)", applicationContext, force = true)
                        for (entryWithPairs in oldEntries) {
                            if (entryWithPairs.entry.isEnabled) {
                                performSync(entryWithPairs, isOpening = false)
                            }
                        }
                        
                        // Session finished
                        AppLogger.e(TAG, "<< Session Ended", applicationContext, force = true)
                        AppLogger.isSessionActive = false
                    }
                }
            }
        }
    }

    private suspend fun performSync(entryWithPairs: SyncEntryWithPairs, isOpening: Boolean) {
        val entry = entryWithPairs.entry
        val pairs = entryWithPairs.pairs
        
        AppLogger.e(TAG, "performSync START: appName=${entry.appName}, isOpening=$isOpening, mirrorDeletions=${entry.mirrorDeletions}, pairCount=${pairs.size}", applicationContext, force = true)
        
        if (pairs.isEmpty()) {
            AppLogger.e(TAG, "performSync ABORTED: No directory pairs configured for ${entry.appName}", applicationContext, force = true)
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
            
            AppLogger.e(TAG, "Syncing Pair: src=$src, dst=$dst, rawExternal=${pair.externalPath}, rawInternal=${pair.internalPath}", applicationContext, force = true)

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
                    AppLogger.e(TAG, "Mirror deletions FAILED for $src -> $dst: ${mirrorResult.exceptionOrNull()?.message}", applicationContext, force = true)
                } else {
                    AppLogger.e(TAG, "Mirror deletions success for $src -> $dst", applicationContext, force = true)
                }
            }
            val result = ShizukuHelper.executeCp(src, dst)
            if (!result.isSuccess) {
                allSuccess = false
                AppLogger.e(TAG, "Copy FAILED for $src -> $dst: ${result.exceptionOrNull()?.message}", applicationContext, force = true)
            } else {
                AppLogger.e(TAG, "Copy success for $src -> $dst", applicationContext, force = true)
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
