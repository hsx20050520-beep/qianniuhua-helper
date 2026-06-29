package com.qnh.helper

import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("qnh_helper", MODE_PRIVATE)
    }

    private lateinit var statusLine: TextView
    private lateinit var enableSwitch: Switch
    private lateinit var floatingSwitch: Switch
    private lateinit var backSwitch: Switch
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshLogs()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        // 标题
        root.addView(TextView(this).apply {
            text = "牵牛花跳转助手"
            textSize = 22f
            setPadding(0, 0, 0, dp(8))
        })

        // 副标题
        root.addView(TextView(this).apply {
            text = "收到牵牛花拣货通知时，自动跳转到待领取页面。支持 OriginOS、MIUI、EMUI 等各定制安卓系统。"
            textSize = 14f
            setPadding(0, 0, 0, dp(16))
        })

        // 状态栏
        statusLine = TextView(this).apply {
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFF1F5F9.toInt())
        }
        root.addView(statusLine, lp())

        root.addView(spacer(12))

        // 权限按钮组
        val grant = Button(this).apply {
            text = "1. 授予通知使用权"
            setOnClickListener { openNotificationListenerSettings() }
        }
        root.addView(grant, lp())

        val grantAccessibility = Button(this).apply {
            text = "2. 授予无障碍权限"
            setOnClickListener { openAccessibilitySettings() }
        }
        root.addView(grantAccessibility, lp())

        val grantOverlay = Button(this).apply {
            text = "3. 授予悬浮窗权限"
            setOnClickListener { openOverlaySettings() }
        }
        root.addView(grantOverlay, lp())

        val grantBattery = Button(this).apply {
            text = "4. 加入电池白名单（推荐）"
            setOnClickListener { requestBatteryExemption() }
        }
        root.addView(grantBattery, lp())

        // 功能开关
        root.addView(spacer(8))

        enableSwitch = Switch(this).apply {
            text = "5. 启用自动进入拣货任务"
            isChecked = prefs.getBoolean("enabled", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("enabled", isChecked).apply()
                refreshStatus()
            }
            setPadding(dp(4), dp(16), dp(4), dp(8))
        }
        root.addView(enableSwitch, lp())

        floatingSwitch = Switch(this).apply {
            text = "6. 启用悬浮窗按钮"
            isChecked = FloatingWindowService.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked && !isOverlayEnabled()) {
                    Toast.makeText(this@MainActivity, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                    buttonView.isChecked = false
                    return@setOnCheckedChangeListener
                }
                FloatingWindowService.setEnabled(this@MainActivity, isChecked)
                refreshStatus()
            }
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(floatingSwitch, lp())

        backSwitch = Switch(this).apply {
            text = "7. 启用页面回退功能"
            isChecked = QnhLauncher.isBackEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                QnhLauncher.setBackEnabled(this@MainActivity, isChecked)
                refreshStatus()
            }
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(backSwitch, lp())

        // 测试按钮
        val testPickingTask = Button(this).apply {
            text = "测试：进入待领取"
            setOnClickListener { testOpenPickingTask() }
        }
        root.addView(testPickingTask, lp())

        // 测试：判断牵牛花是否在后台运行
        val testRunning = Button(this).apply {
            text = "测试：判断牵牛花是否运行"
            setOnClickListener { testQnhRunning() }
        }
        root.addView(testRunning, lp())

        // 测试：打开猴儿家
        val testHouerjia = Button(this).apply {
            text = "测试：打开猴儿家排班考勤"
            setOnClickListener { testOpenHouerjia() }
        }
        root.addView(testHouerjia, lp())

        // 定时打卡提醒
        root.addView(spacer(16))
        root.addView(TextView(this).apply {
            text = "定时打卡提醒"
            textSize = 16f
            setPadding(0, 0, 0, dp(8))
        })

        val alarmSwitch = Switch(this).apply {
            text = "启用定时打卡提醒"
            isChecked = AlarmScheduler.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    showTimePickerDialog { hour, minute ->
                        AlarmScheduler.setAlarm(this@MainActivity, hour, minute)
                        alarmSwitch.text = "启用定时打卡提醒  ${AlarmScheduler.formatTime(hour, minute)}"
                        refreshStatus()
                    }
                } else {
                    AlarmScheduler.cancelAlarm(this@MainActivity)
                    alarmSwitch.text = "启用定时打卡提醒"
                    refreshStatus()
                }
            }
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(alarmSwitch, lp())

        // 如果已设置过时间，显示当前设置
        if (AlarmScheduler.isEnabled(this@MainActivity)) {
            val h = AlarmScheduler.getHour(this)
            val m = AlarmScheduler.getMinute(this)
            if (h >= 0 && m >= 0) {
                alarmSwitch.text = "启用定时打卡提醒  ${AlarmScheduler.formatTime(h, m)}"
            }
        }

        val changeTimeBtn = Button(this).apply {
            text = "修改定时打卡时间"
            setOnClickListener {
                val h = AlarmScheduler.getHour(this@MainActivity)
                val m = AlarmScheduler.getMinute(this@MainActivity)
                showTimePickerDialog(h.coerceAtLeast(0), m.coerceAtLeast(0)) { hour, minute ->
                    AlarmScheduler.setAlarm(this@MainActivity, hour, minute)
                    alarmSwitch.text = "启用定时打卡提醒  ${AlarmScheduler.formatTime(hour, minute)}"
                    Toast.makeText(this@MainActivity, "打卡时间已设置为 ${AlarmScheduler.formatTime(hour, minute)}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(changeTimeBtn, lp())

        root.addView(spacer(16))

        // 日志区域标题
        root.addView(TextView(this).apply {
            text = "操作日志"
            textSize = 16f
            setPadding(0, 0, 0, dp(8))
        })

        // 日志显示区域
        val logScroll = ScrollView(this).apply {
            setBackgroundColor(0xFF0F172A.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        logTextView = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFE2E8F0.toInt())
            setLineSpacing(0f, 1.2f)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        logScroll.addView(logTextView)
        root.addView(logScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(200)
        ))

        root.addView(spacer(8))

        // 日志操作按钮
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val copyBtn = Button(this).apply {
            text = "复制日志"
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            setOnClickListener { copyLogs() }
        }
        val refreshBtn = Button(this).apply {
            text = "刷新"
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            setOnClickListener { refreshLogs() }
        }
        val clearBtn = Button(this).apply {
            text = "清空"
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            setOnClickListener { clearLogs() }
        }
        btnRow.addView(copyBtn)
        btnRow.addView(refreshBtn)
        btnRow.addView(clearBtn)
        root.addView(btnRow, lp())

        root.addView(spacer(16))

        // 使用说明
        root.addView(TextView(this).apply {
            text = buildGuideText()
            textSize = 13f
            setTextColor(0xFF475569.toInt())
        })

        // 系统适配提示
        root.addView(spacer(16))
        root.addView(TextView(this).apply {
            text = buildSystemTipsText()
            textSize = 12f
            setTextColor(0xFF94A3B8.toInt())
            setPadding(0, 0, 0, dp(8))
        })

        scroll.addView(root)
        return scroll
    }

    private fun buildGuideText(): String {
        return "使用说明：\n" +
            "• 依次点击上方 1-4 按钮，授予对应权限。\n" +
            "• 建议开启「加入电池白名单」，防止系统杀后台导致通知监听失效。\n" +
            "• 开启悬浮窗按钮后，屏幕上会出现可拖动的「拣货」按钮。\n" +
            "• 收到牵牛花拣货通知时，会自动跳转到「待领取」页面。\n" +
            "• 如果你在「待拣货」界面，不会跳转到「待领取」。\n" +
            "• 本应用仅做页面跳转，不执行任何业务操作。"
    }

    private fun buildSystemTipsText(): String {
        val romName = detectRom()
        return "检测到你的系统：$romName\n" +
            "部分定制系统（如 MIUI/EMUI/OriginOS）默认限制后台应用，" +
            "建议在「电池设置」中手动允许本应用后台运行，以获得最佳体验。"
    }

    // 检测当前 ROM 类型
    private fun detectRom(): String {
        return when {
            Build.MANUFACTURER.equals("vivo", ignoreCase = true) -> "OriginOS (Vivo)"
            Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) -> "MIUI (小米)"
            Build.MANUFACTURER.equals("huawei", ignoreCase = true) ||
                Build.MANUFACTURER.equals("honor", ignoreCase = true) -> "EMUI/HarmonyOS (华为/荣耀)"
            Build.MANUFACTURER.equals("oppo", ignoreCase = true) -> "ColorOS (OPPO)"
            Build.MANUFACTURER.equals("samsung", ignoreCase = true) -> "OneUI (三星)"
            Build.MANUFACTURER.equals("google", ignoreCase = true) -> "原生 Android"
            else -> "${Build.MANUFACTURER} ${Build.MODEL}"
        }
    }

    private fun refreshStatus() {
        val notificationGranted = isNotificationListenerEnabled()
        val accessibilityGranted = isAccessibilityEnabled()
        val overlayGranted = isOverlayEnabled()
        val batteryExempt = isBatteryExempt()
        val installed = isQnhInstalled()
        val enabled = prefs.getBoolean("enabled", true)
        val floatingEnabled = FloatingWindowService.isEnabled(this)
        val backEnabled = QnhLauncher.isBackEnabled(this)
        val alarmEnabled = AlarmScheduler.isEnabled(this)
        val alarmHour = AlarmScheduler.getHour(this)
        val alarmMinute = AlarmScheduler.getMinute(this)
        val alarmTimeStr = if (alarmEnabled && alarmHour >= 0 && alarmMinute >= 0) {
            " ${AlarmScheduler.formatTime(alarmHour, alarmMinute)} ✅"
        } else {
            " ❌"
        }

        val sb = StringBuilder()
        sb.append("通知使用权：").append(if (notificationGranted) "已授权 ✅" else "未授权 ❌").append('\n')
        sb.append("无障碍权限：").append(if (accessibilityGranted) "已授权 ✅" else "未授权 ❌").append('\n')
        sb.append("悬浮窗权限：").append(if (overlayGranted) "已授权 ✅" else "未授权 ❌").append('\n')
        sb.append("电池白名单：").append(if (batteryExempt) "已加入 ✅" else "未加入 ⚠️").append('\n')
        sb.append("自动进入拣货任务：").append(if (enabled) "已启用 ✅" else "已关闭 ❌").append('\n')
        sb.append("悬浮窗按钮：").append(if (floatingEnabled) "已启用 ✅" else "已关闭 ❌").append('\n')
        sb.append("页面回退功能：").append(if (backEnabled) "已启用 ✅" else "已关闭 ❌").append('\n')
        sb.append("定时打卡提醒：").append(alarmTimeStr).append('\n')
        sb.append("牵牛花是否安装：").append(if (installed) "已安装 ✅" else "未安装 ❌")
        statusLine.text = sb.toString()

        if (::enableSwitch.isInitialized) enableSwitch.isChecked = enabled
        if (::floatingSwitch.isInitialized) floatingSwitch.isChecked = floatingEnabled
        if (::backSwitch.isInitialized) backSwitch.isChecked = backEnabled
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        if (TextUtils.isEmpty(flat)) return false
        val target = ComponentName(this, QnhNotificationListenerService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == target }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        if (TextUtils.isEmpty(flat)) return false
        val target = ComponentName(this, QnhAccessibilityService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == target }
    }

    private fun isQnhInstalled(): Boolean = try {
        packageManager.getPackageInfo(QnhLauncher.QNH_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun isOverlayEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun isBatteryExempt(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun openNotificationListenerSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 22) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        } else {
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开无障碍设置：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开悬浮窗设置：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestBatteryExemption() {
        FloatingWindowService.requestBatteryExemption(this)
        Toast.makeText(this, "请在电池设置中找到「牵牛花跳转助手」，设为「不允许」或「无限制」", Toast.LENGTH_LONG).show()
    }

    private fun testOpenPickingTask() {
        if (!isQnhInstalled()) {
            Toast.makeText(this, "未检测到牵牛花已安装", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍权限，否则无法自动点击拣货任务入口", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        if (!QnhLauncher.openPickingTaskEntry(this)) {
            Toast.makeText(this, "打开牵牛花失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testQnhRunning() {
        val state = QnhLauncher.getQnhState(this)
        val msg = when (state) {
            QnhLauncher.QNH_STATE_FOREGROUND -> "牵牛花正在前台运行"
            QnhLauncher.QNH_STATE_BACKGROUND -> "牵牛花正在后台运行"
            else -> "牵牛花没有运行"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun testOpenHouerjia() {
        if (!QnhLauncher.isHouerjiaInstalled(this)) {
            Toast.makeText(this, "未检测到猴儿家V2已安装", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请先开启无障碍权限，否则无法自动点击排班考勤", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        if (QnhLauncher.openHouerjiaAttendance(this)) {
            Toast.makeText(this, "正在打开猴儿家排班考勤", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "打开猴儿家失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTimePickerDialog(defaultHour: Int = 9, defaultMinute: Int = 0, onTimeSet: (hour: Int, minute: Int) -> Unit) {
        val hour = if (defaultHour < 0) 9 else defaultHour
        val minute = if (defaultMinute < 0) 0 else defaultMinute
        TimePickerDialog(
            this,
            { _, h, m -> onTimeSet(h, m) },
            hour,
            minute,
            true
        ).show()
    }

    private fun copyLogs() {
        val logs = LogRecorder.getLogsAsString(this)
        if (logs.isBlank()) {
            Toast.makeText(this, "暂无日志", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("牵牛花助手日志", logs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun refreshLogs() {
        val logs = LogRecorder.getAllLogs(this)
        logTextView.text = if (logs.isEmpty()) "暂无日志" else logs.joinToString("\n")
    }

    private fun clearLogs() {
        LogRecorder.clearLogs(this)
        logTextView.text = "暂无日志"
        Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
    }

    // ---- helpers ----
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    private fun spacer(h: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(h)
        )
    }
}