package com.qnh.helper

import android.app.Notification
import android.content.SharedPreferences
import android.os.Handler
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 监听系统通知栏，当收到"牵牛花"应用的拣货通知时，
 * 自动唤起牵牛花，并由无障碍服务进入"拣货任务"入口。
 *
 * 只匹配"新拣货任务"通知，避免其他消息误触发跳转。
 */
class QnhNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "QnhListener"

        // 只匹配"新拣货任务"通知关键词，避免其他消息误触发。
        private val PICKING_KEYWORDS = listOf(
            "新拣货任务", "新分拣任务", "拣货任务", "分拣任务", "新任务"
        )

        // 防抖时间（毫秒），避免同一通知重复触发
        private const val DEBOUNCE_MS = 500L
        // 首次重试延迟
        private const val RETRY_DELAY_MS = 1000L
        // 二次重试延迟
        private const val RETRY_DELAY_2_MS = 3000L
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("qnh_helper", MODE_PRIVATE)
    }

    @Volatile
    private var lastFireKey: String? = null
    @Volatile
    private var lastFireTs: Long = 0L

    private var retryRunnable1: Runnable? = null
    private var retryRunnable2: Runnable? = null
    private val handler: Handler by lazy { Handler(mainLooper) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!isEnabled()) return
        if (sbn.packageName != QnhLauncher.QNH_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // 提取所有可能的通知文本字段
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()

        // 组合所有文本字段
        val combined = listOf(title, text, bigText, subText, summaryText)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val isPicking = isPickingNotification(combined)
        if (!isPicking) {
            val msg = "通知不匹配关键词，忽略：$combined"
            Log.d(TAG, msg)
            LogRecorder.d(this, TAG, msg)
            return
        }

        val key = sbn.key + "|" + combined.hashCode()
        val now = System.currentTimeMillis()
        if (key == lastFireKey && now - lastFireTs < DEBOUNCE_MS) return
        lastFireKey = key
        lastFireTs = now

        val hitMsg = "命中新拣货任务通知，准备跳转：$combined"
        Log.i(TAG, hitMsg)
        LogRecorder.i(this, TAG, hitMsg)

        // 标记待处理任务
        QnhLauncher.markPickingTaskPending(this)

        // 取消之前的重试
        cancelAllRetries()

        // 只把牵牛花带到前台，不改变当前页面
        // 让无障碍服务从当前页面开始判断，确保待拣货页面守卫生效
        QnhLauncher.bringToFront(this)

        // 安排重试
        scheduleRetries()
    }

    private fun scheduleRetries() {
        retryRunnable1 = Runnable {
            if (QnhLauncher.isPickingTaskPending(this)) {
                val msg = "第1次重试跳转"
                Log.d(TAG, msg)
                LogRecorder.d(this@QnhNotificationListenerService, TAG, msg)
                QnhLauncher.bringToFront(this)
            }
        }

        retryRunnable2 = Runnable {
            if (QnhLauncher.isPickingTaskPending(this)) {
                val msg = "第2次重试跳转"
                Log.d(TAG, msg)
                LogRecorder.d(this@QnhNotificationListenerService, TAG, msg)
                QnhLauncher.bringToFront(this)
            }
        }

        handler.postDelayed(retryRunnable1!!, RETRY_DELAY_MS)
        handler.postDelayed(retryRunnable2!!, RETRY_DELAY_MS + RETRY_DELAY_2_MS)
    }

    private fun cancelAllRetries() {
        retryRunnable1?.let { handler.removeCallbacks(it) }
        retryRunnable2?.let { handler.removeCallbacks(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 不处理
    }

    private fun isPickingNotification(text: String): Boolean {
        if (text.isBlank()) return false
        val lowerText = text.lowercase()
        return PICKING_KEYWORDS.any { lowerText.contains(it.lowercase()) }
    }

    private fun isEnabled(): Boolean = prefs.getBoolean("enabled", true)
}