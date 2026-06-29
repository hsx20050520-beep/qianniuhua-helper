package com.qnh.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (AlarmScheduler.isEnabled(context)) {
                val hour = AlarmScheduler.getHour(context)
                val minute = AlarmScheduler.getMinute(context)
                if (hour >= 0 && minute >= 0) {
                    AlarmScheduler.scheduleNextAlarm(context, hour, minute)
                    Log.d("BootReceiver", "开机完成，已恢复定时打卡闹钟：${AlarmScheduler.formatTime(hour, minute)}")
                    LogRecorder.i(context, "BootReceiver", "开机完成，已恢复定时打卡闹钟：${AlarmScheduler.formatTime(hour, minute)}")
                }
            }
        }
    }
}
