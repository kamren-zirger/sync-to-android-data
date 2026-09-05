package com.kamrenzirger.synctoandroiddata.util
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.kamrenzirger.synctoandroiddata.ISyncService
import com.kamrenzirger.synctoandroiddata.service.UserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
object ShizukuHelper {
    private var userService: ISyncService? = null
    val SHIZUKU_PACKAGES = listOf("moe.shizuku.privileged.api", "moe.shizuku.service")

    init {
        try {
            Shizuku.addBinderReceivedListener {
            }
        } catch (e: Exception) {
            Log.e("ShizukuHelper", "Failed to add binder listener", e)
        }
    }
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }
    fun checkPermission(requestCode: Int): Boolean {
        try {
            if (!isShizukuAvailable()) return false
            if (Shizuku.isPreV11()) return false
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e("ShizukuHelper", "Error checking permission", e)
            return false
        }
    }
    private suspend fun getUserService(): ISyncService? {
        if (!isShizukuAvailable()) return null
        if (userService != null && userService?.asBinder()?.isBinderAlive == true) {
            return userService
        }
        return withContext(Dispatchers.IO) {
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    userService = ISyncService.Stub.asInterface(binder)
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    userService = null
                }
            }
            val args = UserServiceArgs(ComponentName("com.kamrenzirger.synctoandroiddata", UserService::class.java.name))
                .daemon(false)
                .debuggable(true)
                .processNameSuffix("sync_service")
            try {
                Shizuku.bindUserService(args, connection)
                var retry = 0
                while (userService == null && retry < 10) {
                    delay(200)
                    retry++
                }
            } catch (e: Exception) {
                Log.e("ShizukuHelper", "Failed to bind UserService", e)
            }
            userService
        }
    }
    suspend fun pathExists(path: String): Boolean {
        val service = getUserService() ?: return false
        return try {
            service.runCommand("[ -d \"$path\" ]") == 0
        } catch (e: Exception) {
            false
        }
    }
    suspend fun listFiles(path: String): List<String> {
        val service = getUserService() ?: return emptyList()
        return try {
            service.runCommandWithOutput("ls -F \"$path\"")
        } catch (e: Exception) {
            emptyList()
        }
    }
    suspend fun executeCp(src: String, dst: String): Result<Int> {
        val service = getUserService() ?: return Result.failure(Exception("UserService not available"))
        return try {
            val command = "mkdir -p \"$dst\" && cp -ua \"$src/.\" \"$dst/\""
            val exitCode = service.runCommand(command)
            if (exitCode == 0) Result.success(exitCode) else Result.failure(Exception("cp failed with exit code $exitCode"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun mirrorDeletions(src: String, dst: String): Result<Unit> {
        val service = getUserService() ?: return Result.failure(Exception("UserService not available"))
        return try {
            val srcFiles = service.runCommandWithOutput("cd \"$src\" && find .").toSet()
            val dstFiles = service.runCommandWithOutput("cd \"$dst\" && find .")
            val toDelete = dstFiles
                .filter { it !in srcFiles && it != "." && it != ".." }
                .sortedByDescending { it.length }
            if (toDelete.isNotEmpty()) {
                val deleteScript = toDelete.joinToString("\n") { 
                    "rm -rf \"$dst/${it.removePrefix("./")}\"" 
                }
                service.runCommand(deleteScript)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun runShellCommand(command: String): String {
        val service = getUserService() ?: return ""
        return try {
            service.runCommandWithOutput(command).joinToString("\n")
        } catch (e: Exception) {
            Log.e("ShizukuHelper", "Shell command failed: $command", e)
            ""
        }
    }
    suspend fun grantAccessibility(context: Context, serviceClass: Class<*>): Boolean {
        val componentName = android.content.ComponentName(context, serviceClass).flattenToString()
        val currentServices = runShellCommand("settings get secure enabled_accessibility_services").trim()
        if (currentServices.contains(componentName)) {
            runShellCommand("settings put secure accessibility_enabled 1")
            return true
        }
        val newServices = if (currentServices.isEmpty() || currentServices == "null") {
            componentName
        } else {
            "$currentServices:$componentName"
        }
        runShellCommand("settings put secure enabled_accessibility_services $newServices")
        runShellCommand("settings put secure accessibility_enabled 1")
        return true
    }
}
