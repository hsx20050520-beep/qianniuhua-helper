package com.qnh.helper

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * 悬浮窗服务，显示一个可拖动的开关按钮。
 */
class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatingWindow"
        private const val PREFS = "qnh_helper"
        private const val KEY_FLOATING_X = "floating_x"
        private const val KEY_FLOATING_Y = "floating_y"

        private const val NOTIFICATION_CHANNEL_ID = "floating_window_channel"
        private const val NOTIFICATION_ID = 10001

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("floating_enabled", false)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("floating_enabled", enabled)
                .apply()

            if (enabled) {
                context.startService(Intent(context, FloatingWindowService::class.java))
            } else {
                context.stopService(Intent(context, FloatingWindowService::class.java))
            }
        }

        fun startIfEnabled(context: Context) {
            if (isEnabled(context)) {
                context.startService(Intent(context, FloatingWindowService::class.java))
            }
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        fun requestBatteryExemption(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    Log.w(TAG, "无法打开电池优化设置", e2)
                }
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var container: LinearLayout
    private lateinit var switchView: Switch
    private lateinit var prefs: SharedPreferences
    private var params: WindowManager.LayoutParams? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastWindowX = 0
    private var lastWindowY = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        createFloatingView()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮窗按钮在后台运行"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("牵牛花助手运行中")
            .setContentText("点击悬浮窗可快速开关自动跳转")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(Notification.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("InflateParams")
    private fun createFloatingView() {
        val enabled = prefs.getBoolean("enabled", true)

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = 4f
            // 圆角效果通过背景实现
        }

        val label = TextView(this).apply {
            text = "自动跳转"
            textSize = 11f
            setTextColor(0xFF475569.toInt())
        }

        switchView = Switch(this).apply {
            text = if (enabled) "开" else "关"
            isChecked = enabled
            textSize = 12f
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("enabled", isChecked).apply()
                this.text = if (isChecked) "开" else "关"
                Toast.makeText(
                    this@FloatingWindowService,
                    "自动进入拣货任务：${if (isChecked) "已启用" else "已关闭"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        container.addView(label)
        container.addView(switchView)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val savedX = prefs.getInt(KEY_FLOATING_X, 100)
        val savedY = prefs.getInt(KEY_FLOATING_Y, 200)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = savedX
            y = savedY
        }

        container.setOnTouchListener(touchListener)

        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating window", e)
            stopSelf()
        }
    }

    private val touchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                lastWindowX = params?.x ?: 0
                lastWindowY = params?.y ?: 0
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastTouchX
                val dy = event.rawY - lastTouchY
                params?.apply {
                    x = (lastWindowX + dx.toInt()).coerceAtLeast(0)
                    y = (lastWindowY + dy.toInt()).coerceAtLeast(0)
                    try {
                        windowManager.updateViewLayout(container, this)
                    } catch (e: Exception) {
                        Log.w(TAG, "updateViewLayout failed", e)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                params?.apply {
                    prefs.edit()
                        .putInt(KEY_FLOATING_X, x)
                        .putInt(KEY_FLOATING_Y, y)
                        .apply()
                }
            }
        }
        false
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::container.isInitialized) {
                windowManager.removeView(container)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove floating window", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
