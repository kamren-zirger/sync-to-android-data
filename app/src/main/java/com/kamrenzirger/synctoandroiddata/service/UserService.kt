package com.kamrenzirger.synctoandroiddata.service
import com.kamrenzirger.synctoandroiddata.ISyncService
import java.io.BufferedReader
import java.io.InputStreamReader
class UserService : ISyncService.Stub() {
    override fun runCommand(command: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            process.waitFor()
        } catch (e: Exception) {
            -1
        }
    }
    override fun runCommandWithOutput(command: String): List<String> {
        val output = mutableListOf<String>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { output.add(it) }
            }
            process.waitFor()
        } catch (e: Exception) {
        }
        return output
    }
    override fun destroy() {
        System.exit(0)
    }
}
