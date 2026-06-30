package com.qnh.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val alarms = AlarmScheduler.getAllAlarms(context).filter { it.enabled }
            if (alarms.isNotEmpty()) {
                AlarmScheduler.rescheduleAllEnabled(context)
                val times = alarms.joinToString(", ") { AlarmScheduler.formatTime(it.hour, it.minute) }
                Log.d("BootReceiver", "开机完成，已恢复 ${alarms.size} 个定时打卡闹钟：$times")
                LogRecorder.i(context, "BootReceiver", "开机完成，已恢复 ${alarms.size} 个定时打卡闹钟：$times")
            }
        }
    }
}
