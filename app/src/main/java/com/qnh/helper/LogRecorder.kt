package com.qnh.helper

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRecorder {

    private const val PREFS_NAME = "qnh_logs"
    private const val KEY_NEXT_INDEX = "next_index"
    private const val KEY_LOG_PREFIX = "log_"
    private const val MAX_LOGS = 200

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun i(context: Context, tag: String, message: String) {
        addLog(context, "I", tag, message)
    }

    @Synchronized
    fun d(context: Context, tag: String, message: String) {
        addLog(context, "D", tag, message)
    }

    @Synchronized
    fun w(context: Context, tag: String, message: String) {
        addLog(context, "W", tag, message)
    }

    @Synchronized
    fun e(context: Context, tag: String, message: String) {
        addLog(context, "E", tag, message)
    }

    private fun addLog(context: Context, level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp][$level][$tag] $message"

        val prefs = getPrefs(context)
        val editor = prefs.edit()

        var nextIndex = prefs.getInt(KEY_NEXT_INDEX, 0)
        editor.putString(KEY_LOG_PREFIX + nextIndex, entry)

        nextIndex = (nextIndex + 1) % MAX_LOGS
        editor.putInt(KEY_NEXT_INDEX, nextIndex)

        editor.apply()
    }

    @Synchronized
    fun getAllLogs(context: Context): List<String> {
        val prefs = getPrefs(context)
        val nextIndex = prefs.getInt(KEY_NEXT_INDEX, 0)
        val logs = mutableListOf<String>()

        for (i in 0 until MAX_LOGS) {
            val idx = (nextIndex - 1 - i + MAX_LOGS) % MAX_LOGS
            val entry = prefs.getString(KEY_LOG_PREFIX + idx, null)
            if (entry != null) {
                logs.add(entry)
            }
        }

        return logs
    }

    @Synchronized
    fun getLogsAsString(context: Context): String {
        return getAllLogs(context).joinToString("\n")
    }

    @Synchronized
    fun clearLogs(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
