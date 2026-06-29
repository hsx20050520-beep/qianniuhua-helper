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
        Log.d("AlarmReceiver", "定时打卡闹钟触发了")
        LogRecorder.i(context, "AlarmReceiver", "定时打卡时间到，正在打开猴儿家排班考勤")

        // 发送通知提醒用户
        showNotification(context)

        // 触发猴儿家跳转
        if (!QnhLauncher.isHouerjiaInstalled(context)) {
            LogRecorder.w(context, "AlarmReceiver", "未检测到猴儿家V2，跳转失败")
            return
        }
        if (!QnhAccessibilityService.isServiceRunning()) {
            LogRecorder.w(context, "AlarmReceiver", "无障碍服务未开启，无法自动点击")
            return
        }
        QnhLauncher.openHouerjiaAttendance(context)

        // 重新设置明天的闹钟
        val hour = AlarmScheduler.getHour(context)
        val minute = AlarmScheduler.getMinute(context)
        if (hour >= 0 && minute >= 0) {
            AlarmScheduler.scheduleNextAlarm(context, hour, minute)
            LogRecorder.i(context, "AlarmReceiver", "已重新设置明天的闹钟：${AlarmScheduler.formatTime(hour, minute)}")
        }
    }

    private fun showNotification(context: Context) {
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
            .setContentTitle("定时打卡提醒")
            .setContentText("该打卡了！正在打开猴儿家排班考勤页面")
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(1, notification)
    }
}
