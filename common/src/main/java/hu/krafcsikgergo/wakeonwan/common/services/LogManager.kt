package hu.krafcsikgergo.wakeonwan.common.services

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LogEntry(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String
)

interface LogManager {
    val logs: StateFlow<List<LogEntry>>

    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)

    fun getLogs(): List<LogEntry>
    fun clearLogs()
}

class LogManagerImpl : LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val maxLogs = 1000

    override fun v(tag: String, message: String) {
        Log.v(tag, message)
        addLog("VERBOSE", tag, message)
    }

    override fun d(tag: String, message: String) {
        Log.d(tag, message)
        addLog("DEBUG", tag, message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
        addLog("INFO", tag, message)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
        addLog("WARN", tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
        addLog("ERROR", tag, message)
    }

    override fun getLogs(): List<LogEntry> {
        return _logs.value
    }

    override fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun addLog(level: String, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )

        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(entry)

        // Keep only the last maxLogs entries
        if (currentLogs.size > maxLogs) {
            currentLogs.removeAt(0)
        }

        _logs.value = currentLogs
    }
}
