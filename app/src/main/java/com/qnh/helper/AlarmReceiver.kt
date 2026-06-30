package com.qnh.helper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (alarmId < 0) {
            Log.w("AlarmReceiver", "无效的闹钟ID")
            return
        }

        val alarm = AlarmScheduler.getAlarmById(context, alarmId)
        if (alarm == null) {
            Log.w("AlarmReceiver", "闹钟不存在，id=$alarmId")
            return
        }

        if (!alarm.enabled) {
            Log.d("AlarmReceiver", "闹钟已关闭，跳过，id=$alarmId")
            return
        }

        val timeStr = AlarmScheduler.formatTime(alarm.hour, alarm.minute)
        Log.d("AlarmReceiver", "定时打卡闹钟触发了: $timeStr (id=$alarmId)")
        LogRecorder.i(context, "AlarmReceiver", "定时打卡时间到（$timeStr），正在打开猴儿家排班考勤")

        showNotification(context, timeStr)

        if (!QnhLauncher.isHouerjiaInstalled(context)) {
            LogRecorder.w(context, "AlarmReceiver", "未检测到猴儿家V2，跳转失败")
            return
        }
        if (!QnhAccessibilityService.isServiceRunning()) {
            LogRecorder.w(context, "AlarmReceiver", "无障碍服务未开启，无法自动点击")
            return
        }
        QnhLauncher.openHouerjiaAttendance(context)

        AlarmScheduler.scheduleNextAlarm(context, alarmId)
        LogRecorder.i(context, "AlarmReceiver", "已重新设置明天的闹钟：$timeStr")
    }

    private fun showNotification(context: Context, timeStr: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "alarm_channel",
                "定时打卡提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定时打卡提醒，提示您打开猴儿家打卡"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val notification = android.app.Notification.Builder(context, "alarm_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("定时打卡提醒 ($timeStr)")
            .setContentText("该打卡了！正在打开猴儿家排班考勤页面")
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
