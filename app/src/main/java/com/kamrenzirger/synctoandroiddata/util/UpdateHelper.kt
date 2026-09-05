package com.kamrenzirger.synctoandroiddata.util

import android.content.Context
import android.util.Log
import com.kamrenzirger.synctoandroiddata.BuildConfig
import com.kamrenzirger.synctoandroiddata.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object UpdateHelper {
    private const val TAG = "UpdateHelper"
    private const val LATEST_RELEASE_URL = "https://github.com/kamren-zirger/sync-to-android-data/releases/latest"
    private const val UPDATE_COOLDOWN_MS = 4 * 60 * 60 * 1000L // 4 hours

    sealed class UpdateResult {
        object NoUpdate : UpdateResult()
        data class NewUpdate(val version: String) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    suspend fun checkForUpdates(context: Context, manual: Boolean = false): UpdateResult = withContext(Dispatchers.IO) {
        val settings = SettingsManager(context)
        val currentTime = System.currentTimeMillis()

        if (!manual && (currentTime - settings.lastUpdateCheck < UPDATE_COOLDOWN_MS)) {
            Log.d(TAG, "Skipping update check, last check was less than 4 hours ago")
            return@withContext UpdateResult.NoUpdate
        }

        try {
            val url = URL(LATEST_RELEASE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connect()

            val responseCode = connection.responseCode
            val location = connection.getHeaderField("Location")
            connection.disconnect()

            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 307 || responseCode == 308) {
                if (location != null) {
                    val tag = location.substringAfterLast("/")
                    val version = tag.removePrefix("v")
                    
                    settings.lastUpdateCheck = currentTime
                    settings.latestVersion = version
                    
                    val isNewer = isVersionNewer(BuildConfig.VERSION_NAME, version)
                    settings.isUpdateAvailable = isNewer

                    if (isNewer) {
                        if (!manual) {
                            NotificationHelper.showUpdateNotification(
                                context,
                                context.getString(R.string.notif_update_available_title),
                                context.getString(R.string.notif_update_available_msg, version)
                            )
                        }
                        return@withContext UpdateResult.NewUpdate(version)
                    } else {
                        return@withContext UpdateResult.NoUpdate
                    }
                }
            }
            
            // If it didn't redirect or location was null, maybe we are already at the latest or something changed
            settings.lastUpdateCheck = currentTime
            return@withContext UpdateResult.NoUpdate

        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext UpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        if (current == latest) return false
        
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val curr = currentParts.getOrElse(i) { 0 }
            val late = latestParts.getOrElse(i) { 0 }
            if (late > curr) return true
            if (curr > late) return false
        }
        return false
    }
}
