package com.kamrenzirger.synctoandroiddata.util

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * A thread-safe logger that maintains an in-memory buffer of the last 100 log lines.
 * Logs are only stored if [SettingsManager.enableLogging] is true.
 */
object AppLogger {
    private const val MAX_LOGS = 200
    private const val TAG = "AppLogger"
    private val logBuffer = ArrayDeque<String>(MAX_LOGS)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * When true, all logs are captured. When false, "noisy" logs like window state changes are ignored.
     */
    var isSessionActive: Boolean = false

    private fun addLog(level: String, tag: String, message: String, context: Context, force: Boolean = false) {
        val settings = SettingsManager(context)
        if (!settings.enableLogging) return

        // Ignore noisy window changes and focus checks if a target session isn't active, unless forced
        if (!isSessionActive && !force) {
            if (message.contains("TYPE_WINDOW_STATE_CHANGED") || 
                message.contains("isIgnoredPackage") || 
                message.contains("handlePackageChange")) {
                return
            }
        }

        synchronized(logBuffer) {
            if (logBuffer.size >= MAX_LOGS) {
                logBuffer.removeFirst()
            }
            val timestamp = dateFormat.format(Date())
            val logLine = "[$timestamp] $level/$tag: $message"
            logBuffer.addLast(logLine)
        }
    }

    fun d(tag: String, message: String, context: Context, force: Boolean = false) {
        Log.d(tag, message)
        addLog("D", tag, message, context, force)
    }

    fun e(tag: String, message: String, context: Context, throwable: Throwable? = null, force: Boolean = false) {
        Log.e(tag, message, throwable)
        val fullMsg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        addLog("E", tag, fullMsg, context, force)
    }

    fun i(tag: String, message: String, context: Context, force: Boolean = false) {
        Log.i(tag, message)
        addLog("I", tag, message, context, force)
    }

    fun w(tag: String, message: String, context: Context, force: Boolean = false) {
        Log.w(tag, message)
        addLog("W", tag, message, context, force)
    }

    /**
     * Clears the in-memory log buffer.
     */
    fun clear() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
    }

    /**
     * Returns all logs in the buffer as a single string, oldest to newest.
     */
    fun getFormattedLogs(): String {
        synchronized(logBuffer) {
            return logBuffer.joinToString("\n")
        }
    }
}
