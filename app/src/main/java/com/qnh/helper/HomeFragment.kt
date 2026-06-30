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
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    private lateinit var statusTitle: TextView
    private lateinit var statusDesc: TextView
    private lateinit var statusCard: View
    private lateinit var arrowIcon: ImageView
    private lateinit var permsTitle: TextView
    private lateinit var permsContent: LinearLayout
    private lateinit var permsActionContainer: LinearLayout
    private lateinit var enableSwitch: Switch
    private lateinit var floatingSwitch: Switch
    private lateinit var backSwitch: Switch
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
            paint.isFakeBoldText = true
        })

        root.addView(TextView(requireContext()).apply {
            text = "自动跳转到拣货任务待领取页面"
            textSize = 14f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 0, 0, dp(20))
        })

        val (sCard, sTitle, sDesc) = buildStatusCard()
        statusCard = sCard
        statusTitle = sTitle
        statusDesc = sDesc
        root.addView(sCard, lp())
        (sCard.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)

        val (pCard, aIcon, pTitle, pContent, pActions) = buildCollapsiblePermsCard()
        arrowIcon = aIcon
        permsTitle = pTitle
        permsContent = pContent
        permsActionContainer = pActions
        root.addView(pCard, lp())
        (pCard.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)

        val fCard = buildFeatureCard()
        root.addView(fCard, lp())
        (fCard.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(20)

        val testBtn = buildPrimaryBtn("测试：进入待领取") { testOpenPickingTask() }
        root.addView(testBtn, lp())
        root.addView(spacer(dp(16)))

        root.addView(buildTipCard("💡 建议开启电池白名单，防止系统杀后台导致通知监听失效"))

        scroll.addView(root)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatus()
    }

    private fun buildStatusCard(): Triple<View, TextView, TextView> {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundResource(R.drawable.gradient_card_bg)
        }

        val title = TextView(requireContext()).apply {
            text = "运行正常"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            paint.isFakeBoldText = true
        }
        card.addView(title)

        val desc = TextView(requireContext()).apply {
            text = "所有权限已授权，随时待命"
            textSize = 13f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, dp(4), 0, 0)
        }
        card.addView(desc)

        return Triple(card, title, desc)
    }

    private fun buildCollapsiblePermsCard(): ViewGroup.Quadruple<View, ImageView, TextView, LinearLayout, LinearLayout> {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.card_bg)
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val title = TextView(requireContext()).apply {
            text = "权限状态"
            textSize = 15f
            setTextColor(0xFF0F172A.toInt())
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val countText = TextView(requireContext()).apply {
            text = "5/5"
            textSize = 13f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 0, dp(8), 0)
        }
        header.addView(countText)

        val arrow = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            setColorFilter(0xFF94A3B8.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
        }
        header.addView(arrow)

        header.setOnClickListener {
            togglePerms(arrow, countText)
        }

        card.addView(header)

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            setBackgroundColor(0xFFE2E8F0.toInt())
        }
        content.addView(divider)

        val permLabels = listOf(
            "通知使用权" to ::isNotificationListenerEnabled,
            "无障碍权限" to ::isAccessibilityEnabled,
            "悬浮窗权限" to ::isOverlayEnabled,
            "电池白名单" to ::isBatteryExempt,
            "牵牛花已安装" to ::isQnhInstalled
        )
        for ((label, check) in permLabels) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                    marginEnd = dp(10)
                }
                setBackgroundColor(0xFFCBD5E1.toInt())
                setBackgroundResource(android.R.drawable.presence_online)
            }
            row.addView(TextView(requireContext()).apply {
                text = label
                textSize = 14f
                setTextColor(0xFF334155.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(requireContext()).apply {
                text = if (check()) "✅" else "❌"
                textSize = 14f
            })
            content.addView(row)
        }

        val actionContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(12), 0, 0)
        }

        card.addView(content)
        card.addView(actionContainer)

        return ViewGroup.Quadruple(card, arrow, title, content, actionContainer)
    }

    private fun buildFeatureCard(): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(6))
            setBackgroundResource(R.drawable.card_bg)
        }

        card.addView(TextView(requireContext()).apply {
            text = "功能开关"
            textSize = 15f
            setTextColor(0xFF0F172A.toInt())
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(4))
        })

        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(4)
            }
            setBackgroundColor(0xFFE2E8F0.toInt())
        }
        card.addView(divider)

        enableSwitch = buildSwitch("自动进入拣货任务",
            requireActivity().getSharedPreferences("qnh_helper", 0).getBoolean("enabled", true)
        ) { _, isChecked ->
            requireActivity().getSharedPreferences("qnh_helper", 0).edit()
                .putBoolean("enabled", isChecked).apply()
            refreshAllStatus()
        }
        card.addView(enableSwitch, lp())

        floatingSwitch = buildSwitch("悬浮窗按钮", FloatingWindowService.isEnabled(requireContext()))
        { buttonView, isChecked ->
            if (isChecked && !isOverlayEnabled()) {
                Toast.makeText(requireContext(), "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                buttonView.isChecked = false
                return@buildSwitch
            }
            FloatingWindowService.setEnabled(requireContext(), isChecked)
            refreshAllStatus()
        }
        card.addView(floatingSwitch, lp())

        backSwitch = buildSwitch("页面回退功能", QnhLauncher.isBackEnabled(requireContext()))
        { _, isChecked ->
            QnhLauncher.setBackEnabled(requireContext(), isChecked)
            refreshAllStatus()
        }
        card.addView(backSwitch, lp())

        return card
    }

    private fun togglePerms(arrow: ImageView, countText: TextView) {
        permsExpanded = !permsExpanded
        val anim = if (permsExpanded) {
            RotateAnimation(0f, 180f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        } else {
            RotateAnimation(180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        }
        anim.duration = 250
        anim.fillAfter = true
        arrow.startAnimation(anim)

        if (permsExpanded) {
            permsContent.visibility = View.VISIBLE
            permsActionContainer.visibility = if (hasUnauthorizedPerms()) View.VISIBLE else View.GONE
        } else {
            permsContent.visibility = View.GONE
            permsActionContainer.visibility = View.GONE
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
            setBackgroundResource(R.drawable.btn_primary)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            textSize = 15f
            paint.isFakeBoldText = true
            setOnClickListener { onClick() }
        }
    }

    private fun buildTipCard(text: String): View {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF92400E.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(R.drawable.tip_bg)
        }
    }

    private fun refreshAllStatus() {
        val notificationGranted = isNotificationListenerEnabled()
        val accessibilityGranted = isAccessibilityEnabled()
        val overlayGranted = isOverlayEnabled()
        val batteryExempt = isBatteryExempt()
        val installed = isQnhInstalled()
        val prefs = requireActivity().getSharedPreferences("qnh_helper", 0)
        val enabled = prefs.getBoolean("enabled", true)
        val floatingEnabled = FloatingWindowService.isEnabled(requireContext())
        val backEnabled = QnhLauncher.isBackEnabled(requireContext())

        val allPerms = listOf(notificationGranted, accessibilityGranted, installed)
        val grantedCount = allPerms.count { it }
        val allGranted = allPerms.all { it } && enabled

        if (allGranted) {
            statusTitle.text = "运行正常"
            statusDesc.text = "核心权限已就绪，自动拣货已启用"
            statusCard.setBackgroundResource(R.drawable.gradient_card_bg)
        } else {
            statusTitle.text = "需要设置"
            statusDesc.text = "有未授权的权限或功能未开启"
            statusCard.setBackgroundResource(R.drawable.gradient_warn_bg)
        }

        if (::enableSwitch.isInitialized) enableSwitch.isChecked = enabled
        if (::floatingSwitch.isInitialized) floatingSwitch.isChecked = floatingEnabled
        if (::backSwitch.isInitialized) backSwitch.isChecked = backEnabled

        refreshPermsActions()
    }

    private fun hasUnauthorizedPerms(): Boolean {
        return !isNotificationListenerEnabled() || !isAccessibilityEnabled() ||
            !isOverlayEnabled() || !isBatteryExempt()
    }

    private fun refreshPermsActions() {
        permsActionContainer.removeAllViews()
        if (!isNotificationListenerEnabled()) {
            permsActionContainer.addView(buildPermBtn("授予通知使用权") { openNotificationListenerSettings() })
        }
        if (!isAccessibilityEnabled()) {
            permsActionContainer.addView(buildPermBtn("授予无障碍权限") { openAccessibilitySettings() })
        }
        if (!isOverlayEnabled()) {
            permsActionContainer.addView(buildPermBtn("授予悬浮窗权限") { openOverlaySettings() })
        }
        if (!isBatteryExempt()) {
            permsActionContainer.addView(buildPermBtn("加入电池白名单") { requestBatteryExemption() })
        }
    }

    private fun buildPermBtn(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(0xFF3B82F6.toInt())
            setBackgroundResource(R.drawable.btn_outline)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            textSize = 13f
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
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
    )
    private fun spacer(h: Int): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            h
        )
    }
}

fun <A, B, C, D, E> ViewGroup.Quadruple(a: A, b: B, c: C, d: D, e: E) = object {
    val first = a
    val second = b
    val third = c
    val fourth = d
    val fifth = e
    operator fun component1() = a
    operator fun component2() = b
    operator fun component3() = c
    operator fun component4() = d
    operator fun component5() = e
}
