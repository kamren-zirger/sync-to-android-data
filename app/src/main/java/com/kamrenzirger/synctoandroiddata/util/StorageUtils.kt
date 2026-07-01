package com.kamrenzirger.synctoandroiddata.util
import android.os.Environment
import android.os.StatFs
import java.io.File
object StorageUtils {
    /**
     * Checks if the given path is currently accessible and writable.
     * Only works for non-restricted (external) directories.
     */
    fun isExternalPathReady(path: String): Boolean {
        val file = File(path)
        return try {
            if (!file.exists()) {
                file.mkdirs()
            }
            file.exists() && file.canWrite()
        } catch (e: Exception) {
            false
        }
    }
    /**
     * Checks if there is sufficient space at the given path for the estimated transfer.
     * @param path The target directory path
     * @param minBytesRequired The minimum bytes that must be free (e.g. 50MB buffer)
     */
    fun hasSufficientSpace(path: String, minBytesRequired: Long = 50 * 1024 * 1024L): Boolean {
        return try {
            val file = File(path)
            val target = if (file.exists()) file else file.parentFile ?: return false
            val stat = StatFs(target.absolutePath)
            stat.availableBytes > minBytesRequired
        } catch (e: Exception) {
            true 
        }
    }
    /**
     * Checks if the storage containing the path is currently mounted.
     */
    fun isStorageMounted(path: String): Boolean {
        if (path.startsWith("/storage/emulated/0")) return true
        return try {
            Environment.getExternalStorageState(File(path)) == Environment.MEDIA_MOUNTED
        } catch (e: Exception) {
            true 
        }
    }
}
