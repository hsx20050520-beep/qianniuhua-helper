package com.qnh.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class AlarmItem(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean
)

object AlarmScheduler {
    private const val PREFS = "qnh_helper"
    private const val KEY_ALARMS = "alarm_list"
    private const val KEY_NEXT_ID = "alarm_next_id"
    private const val REQUEST_CODE_BASE = 10000
    const val EXTRA_ALARM_ID = "alarm_id"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAllAlarms(context: Context): List<AlarmItem> {
        val json = getPrefs(context).getString(KEY_ALARMS, null) ?: return emptyList()
        val list = mutableListOf<AlarmItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AlarmItem(
                        id = obj.getInt("id"),
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        enabled = obj.getBoolean("enabled")
                    )
                )
            }
        } catch (_: Exception) {}
        return list.sortedBy { it.hour * 60 + it.minute }
    }

    private fun saveAlarms(context: Context, alarms: List<AlarmItem>) {
        val arr = JSONArray()
        for (alarm in alarms) {
            val obj = JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("enabled", alarm.enabled)
            }
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_ALARMS, arr.toString()).apply()
    }

    private fun getNextId(context: Context): Int {
        val prefs = getPrefs(context)
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    fun addAlarm(context: Context, hour: Int, minute: Int): AlarmItem {
        val id = getNextId(context)
        val alarm = AlarmItem(id = id, hour = hour, minute = minute, enabled = true)
        val list = getAllAlarms(context).toMutableList()
        list.add(alarm)
        saveAlarms(context, list)
        scheduleAlarm(context, alarm)
        return alarm
    }

    fun updateAlarmEnabled(context: Context, id: Int, enabled: Boolean) {
        val list = getAllAlarms(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = list[idx]
            val updated = old.copy(enabled = enabled)
            list[idx] = updated
            saveAlarms(context, list)
            if (enabled) {
                scheduleAlarm(context, updated)
            } else {
                cancelAlarmById(context, id)
            }
        }
    }

    fun updateAlarmTime(context: Context, id: Int, hour: Int, minute: Int) {
        val list = getAllAlarms(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = list[idx]
            val updated = old.copy(hour = hour, minute = minute)
            list[idx] = updated
            saveAlarms(context, list)
            if (updated.enabled) {
                cancelAlarmById(context, id)
                scheduleAlarm(context, updated)
            }
        }
    }

    fun deleteAlarm(context: Context, id: Int) {
        val list = getAllAlarms(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list.removeAt(idx)
            saveAlarms(context, list)
            cancelAlarmById(context, id)
        }
    }

    fun getAlarmById(context: Context, id: Int): AlarmItem? {
        return getAllAlarms(context).find { it.id == id }
    }

    fun hasEnabledAlarms(context: Context): Boolean {
        return getAllAlarms(context).any { it.enabled }
    }

    private fun requestCodeFor(id: Int) = REQUEST_CODE_BASE + id

    private fun scheduleAlarm(context: Context, alarm: AlarmItem) {
        if (!alarm.enabled) return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(alarm.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun scheduleNextAlarm(context: Context, alarmId: Int) {
        val alarm = getAlarmById(context, alarmId) ?: return
        scheduleAlarm(context, alarm)
    }

    private fun cancelAlarmById(context: Context, id: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(id),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    fun rescheduleAllEnabled(context: Context) {
        val alarms = getAllAlarms(context)
        for (alarm in alarms) {
            if (alarm.enabled) {
                scheduleAlarm(context, alarm)
            }
        }
    }

    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }
}
