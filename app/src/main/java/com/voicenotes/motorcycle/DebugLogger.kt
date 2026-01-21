package com.voicenotes.motorcycle

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Centralized debug logging utility for tracking API calls and responses
 */
object DebugLogger {
    
    private const val TAG = "DebugLogger"
    private const val LOG_FILE = "debug_log.txt"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    /**
     * Log an API request
     */
    fun logApiRequest(service: String, method: String, url: String, headers: Map<String, String> = emptyMap()) {
        val timestamp = dateFormat.format(Date())
        val message = buildString {
            appendLine("[$timestamp] API REQUEST")
            appendLine("Service: $service")
            appendLine("Method: $method")
            appendLine("URL: $url")
            if (headers.isNotEmpty()) {
                appendLine("Headers:")
                headers.forEach { (key, value) ->
                    // Mask sensitive headers
                    val displayValue = if (key.equals("Authorization", ignoreCase = true)) {
                        "Bearer ***"
                    } else {
                        value
                    }
                    appendLine("  $key: $displayValue")
                }
            }
            appendLine()
        }
        
        Log.d(TAG, message)
        appendToLogFile(message)
    }
    
    /**
     * Log an API response
     */
    fun logApiResponse(service: String, statusCode: Int, responseBody: String? = null, error: String? = null) {
        val timestamp = dateFormat.format(Date())
        val message = buildString {
            appendLine("[$timestamp] API RESPONSE")
            appendLine("Service: $service")
            appendLine("Status Code: $statusCode")
            if (error != null) {
                appendLine("Error: $error")
            }
            if (responseBody != null && responseBody.length < 1000) {
                appendLine("Response Body: $responseBody")
            } else if (responseBody != null) {
                appendLine("Response Body: ${responseBody.take(1000)}... (truncated)")
            }
            appendLine()
        }
        
        Log.d(TAG, message)
        appendToLogFile(message)
    }
    
    /**
     * Log an error
     */
    fun logError(service: String, error: String, exception: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val message = buildString {
            appendLine("[$timestamp] ERROR")
            appendLine("Service: $service")
            appendLine("Error: $error")
            if (exception != null) {
                appendLine("Exception: ${exception.javaClass.simpleName}")
                appendLine("Message: ${exception.message}")
                appendLine("Stack trace:")
                exception.stackTrace.take(10).forEach {
                    appendLine("  at $it")
                }
            }
            appendLine()
        }
        
        Log.e(TAG, message)
        appendToLogFile(message)
    }
    
    /**
     * Log general information
     */
    fun logInfo(service: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = buildString {
            appendLine("[$timestamp] INFO")
            appendLine("Service: $service")
            appendLine("Message: $message")
            appendLine()
        }
        
        Log.i(TAG, logMessage)
        appendToLogFile(logMessage)
    }
    
    /**
     * Append message to log file
     */
    private fun appendToLogFile(message: String) {
        try {
            val context = AppContextHolder.context ?: return
            val logFile = File(context.filesDir, LOG_FILE)
            
            // Check file size and truncate if needed
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                // Keep only the last 50%
                val content = logFile.readText()
                val keepLength = (MAX_LOG_SIZE / 2).toInt()
                val truncated = content.takeLast(keepLength)
                logFile.writeText(truncated)
            }
            
            logFile.appendText(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }
    
    /**
     * Get the full log content
     */
    fun getLogContent(context: Context): String {
        return try {
            val logFile = File(context.filesDir, LOG_FILE)
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "No log entries yet."
            }
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }
    
    /**
     * Clear the log file
     */
    fun clearLog(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILE)
            if (logFile.exists()) {
                logFile.delete()
            }
            Log.i(TAG, "Log cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log", e)
        }
    }
}

/**
 * Holder for application context to allow DebugLogger to access it
 */
object AppContextHolder {
    var context: Context? = null
}
