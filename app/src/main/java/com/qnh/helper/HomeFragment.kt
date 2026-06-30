package com.qnh.helper

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    private lateinit var statusCard: TextView
    private lateinit var enableSwitch: Switch
    private lateinit var floatingSwitch: Switch
    private lateinit var backSwitch: Switch
    private lateinit var permsContainer: LinearLayout
    private var permsExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scroll = ScrollView(requireContext())
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }

        root.addView(TextView(requireContext()).apply {
            text = "牵牛花跳转助手"
            textSize = 24f
            setTextColor(0xFF0F172A.toInt())
            setPadding(0, dp(8), 0, dp(4))
        })

        root.addView(TextView(requireContext()).apply {
            text = "自动跳转到拣货任务待领取页面"
            textSize = 14f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 0, 0, dp(16))
        })

        statusCard = TextView(requireContext()).apply {
            textSize = 13f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFFF8FAFC.toInt())
            setBackgroundResource(R.drawable.card_bg)
        }
        root.addView(statusCard, lp())
        (statusCard.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)

        root.addView(buildSectionTitle("快捷权限"))
        permsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val permBtn1 = buildPrimaryBtn("授予通知使用权") { openNotificationListenerSettings() }
        val permBtn2 = buildPrimaryBtn("授予无障碍权限") { openAccessibilitySettings() }
        val permBtn3 = buildPrimaryBtn("授予悬浮窗权限") { openOverlaySettings() }
        val permBtn4 = buildPrimaryBtn("加入电池白名单（推荐）") { requestBatteryExemption() }
        permsContainer.addView(permBtn1)
        permsContainer.addView(permBtn2)
        permsContainer.addView(permBtn3)
        permsContainer.addView(permBtn4)
        root.addView(permsContainer, lp())
        root.addView(spacer(dp(16)))

        root.addView(buildSectionTitle("功能开关"))

        enableSwitch = buildSwitch("自动进入拣货任务",
            requireActivity().getSharedPreferences("qnh_helper", 0).getBoolean("enabled", true)
        ) { _, isChecked ->
            requireActivity().getSharedPreferences("qnh_helper", 0).edit()
                .putBoolean("enabled", isChecked).apply()
            refreshStatus()
        }
        root.addView(enableSwitch, lp())

        floatingSwitch = buildSwitch("悬浮窗按钮", FloatingWindowService.isEnabled(requireContext()))
        { buttonView, isChecked ->
            if (isChecked && !isOverlayEnabled()) {
                Toast.makeText(requireContext(), "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                buttonView.isChecked = false
                return@buildSwitch
            }
            FloatingWindowService.setEnabled(requireContext(), isChecked)
            refreshStatus()
        }
        root.addView(floatingSwitch, lp())

        backSwitch = buildSwitch("页面回退功能", QnhLauncher.isBackEnabled(requireContext()))
        { _, isChecked ->
            QnhLauncher.setBackEnabled(requireContext(), isChecked)
            refreshStatus()
        }
        root.addView(backSwitch, lp())

        root.addView(spacer(dp(16)))

        val testBtn = buildPrimaryBtn("测试：进入待领取") { testOpenPickingTask() }
        root.addView(testBtn, lp())

        root.addView(spacer(dp(16)))

        root.addView(buildTipCard("💡 建议开启电池白名单，防止系统杀后台导致通知监听失效"))

        scroll.addView(root)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildSectionTitle(text: String): View {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setTextColor(0xFF334155.toInt())
            setPadding(0, dp(4), 0, dp(10))
        }
    }

    private fun buildSwitch(text: String, checked: Boolean, onChecked: (buttonView: Switch, isChecked: Boolean) -> Unit): Switch {
        return Switch(requireContext()).apply {
            this.text = text
            this.isChecked = checked
            setTextColor(0xFF0F172A.toInt())
            setOnCheckedChangeListener { buttonView, isChecked ->
                onChecked(buttonView as Switch, isChecked)
            }
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
    }

    private fun buildPrimaryBtn(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF3B82F6.toInt())
            setBackgroundResource(R.drawable.btn_primary)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { onClick() }
            stateListAnimator = android.animation.StateListAnimator().apply {
                addState(intArrayOf(android.R.attr.state_pressed), android.animation.ObjectAnimator.ofFloat(this, "scaleX", 0.97f, 0.97f).apply { duration = 0 })
            }
        }
    }

    private fun buildTipCard(text: String): View {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xFFFEF3C7.toInt())
            setBackgroundResource(R.drawable.tip_bg)
        }
    }

    private fun refreshStatus() {
        val notificationGranted = isNotificationListenerEnabled()
        val accessibilityGranted = isAccessibilityEnabled()
        val overlayGranted = isOverlayEnabled()
        val batteryExempt = isBatteryExempt()
        val installed = isQnhInstalled()
        val prefs = requireActivity().getSharedPreferences("qnh_helper", 0)
        val enabled = prefs.getBoolean("enabled", true)
        val floatingEnabled = FloatingWindowService.isEnabled(requireContext())
        val backEnabled = QnhLauncher.isBackEnabled(requireContext())

        val sb = StringBuilder()
        sb.append("通知使用权：").append(if (notificationGranted) "已授权 ✅" else "未授权 ❌").append('\n')
        sb.append("无障碍权限：").append(if (accessibilityGranted) "已授权 ✅" else "未授权 ❌").append('\n')
        sb.append("悬浮窗权限：").append(if (overlayGranted) "已授权 ✅" else "未授权 ❌").append('\n')
        sb.append("电池白名单：").append(if (batteryExempt) "已加入 ✅" else "未加入 ⚠️").append('\n')
        sb.append("自动拣货：").append(if (enabled) "已启用 ✅" else "已关闭 ❌").append('\n')
        sb.append("悬浮窗按钮：").append(if (floatingEnabled) "已启用 ✅" else "已关闭 ❌").append('\n')
        sb.append("页面回退：").append(if (backEnabled) "已启用 ✅" else "已关闭 ❌").append('\n')
        sb.append("牵牛花：").append(if (installed) "已安装 ✅" else "未安装 ❌")
        statusCard.text = sb.toString()

        if (::enableSwitch.isInitialized) enableSwitch.isChecked = enabled
        if (::floatingSwitch.isInitialized) floatingSwitch.isChecked = floatingEnabled
        if (::backSwitch.isInitialized) backSwitch.isChecked = backEnabled
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners") ?: return false
        if (TextUtils.isEmpty(flat)) return false
        val target = ComponentName(requireContext(), QnhNotificationListenerService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == target }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        if (TextUtils.isEmpty(flat)) return false
        val target = ComponentName(requireContext(), QnhAccessibilityService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == target }
    }

    private fun isQnhInstalled(): Boolean = try {
        requireContext().packageManager.getPackageInfo(QnhLauncher.QNH_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun isOverlayEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(requireContext())
        } else {
            true
        }
    }

    private fun isBatteryExempt(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = requireContext().getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
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
            Toast.makeText(requireContext(), "无法打开设置：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开无障碍设置：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "无法打开悬浮窗设置：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestBatteryExemption() {
        FloatingWindowService.requestBatteryExemption(requireContext())
        Toast.makeText(requireContext(), "请在电池设置中找到「牵牛花跳转助手」，设为「不允许」或「无限制」", Toast.LENGTH_LONG).show()
    }

    private fun testOpenPickingTask() {
        if (!isQnhInstalled()) {
            Toast.makeText(requireContext(), "未检测到牵牛花已安装", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(requireContext(), "请先开启无障碍权限", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }
        if (!QnhLauncher.openPickingTaskEntry(requireContext())) {
            Toast.makeText(requireContext(), "打开牵牛花失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }
    private fun spacer(h: Int): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            h
        )
    }
}
