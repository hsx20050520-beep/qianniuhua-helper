package com.qnh.helper

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    private lateinit var statusTitle: TextView
    private lateinit var statusDesc: TextView
    private lateinit var statusCard: View
    private lateinit var statusDot: View
    private lateinit var arrowIcon: ImageView
    private lateinit var permsTitle: TextView
    private lateinit var permsCount: TextView
    private lateinit var permsContent: LinearLayout
    private lateinit var permsActionContainer: LinearLayout
    private lateinit var enableSwitch: Switch
    private lateinit var floatingSwitch: Switch
    private lateinit var backSwitch: Switch
    private var permsExpanded = false

    private val WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT
    private val MP = LinearLayout.LayoutParams.MATCH_PARENT

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(c(R.color.bg_page))
        }
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        // ── Header ──
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(requireContext()).apply {
            text = "牵牛花助手"
            textSize = 26f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        header.addView(buildVersionBadge())
        root.addView(header)

        root.addView(TextView(requireContext()).apply {
            text = "自动跳转到拣货任务待领取页面"
            textSize = 14f
            setTextColor(c(R.color.text_muted))
            setPadding(0, dp(2), 0, dp(24))
        })

        // ── Status Card ──
        val (sCard, sDot, sTitle, sDesc) = buildStatusCard()
        statusCard = sCard; statusDot = sDot; statusTitle = sTitle; statusDesc = sDesc
        root.addView(sCard, lp(bottom = 16))

        // ── Permissions Card ──
        val (pCard, aIcon, pTitle, pCount, pContent, pActions) = buildPermsCard()
        arrowIcon = aIcon; permsTitle = pTitle; permsCount = pCount
        permsContent = pContent; permsActionContainer = pActions
        root.addView(pCard, lp(bottom = 16))

        // ── Feature Switches ──
        val fCard = buildFeatureCard()
        root.addView(fCard, lp(bottom = 20))

        // ── Test Button ──
        root.addView(buildPrimaryBtn("测试：进入待领取") { testOpenPickingTask() })
        root.addView(spacer(dp(16)))

        // ── Tip ──
        root.addView(buildTipCard("建议开启电池白名单，防止系统杀后台导致通知监听失效"))

        scroll.addView(root)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatus()
    }

    // ═══════════ Status Card ═══════════

    private fun buildStatusCard(): StatusCardParts {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundResource(R.drawable.gradient_card_bg)
        }

        val dot = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(14) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c(R.color.status_granted))
            }
        }
        card.addView(dot)

        val textCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val title = TextView(requireContext()).apply {
            text = "运行正常"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.text_on_gradient))
        }
        textCol.addView(title)

        val desc = TextView(requireContext()).apply {
            text = "所有权限已授权，随时待命"
            textSize = 13f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, dp(2), 0, 0)
        }
        textCol.addView(desc)
        card.addView(textCol)

        return StatusCardParts(card, dot, title, desc)
    }

    // ═══════════ Permissions Card ═══════════

    private fun buildPermsCard(): PermsCardParts {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.card_bg)
        }

        // ── Header row ──
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pTitle = TextView(requireContext()).apply {
            text = "权限状态"
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        header.addView(pTitle)

        val pCount = TextView(requireContext()).apply {
            text = "5/5"
            textSize = 13f
            setTextColor(c(R.color.status_granted))
            setPadding(0, 0, dp(8), 0)
        }
        header.addView(pCount)

        val arrow = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            setColorFilter(c(R.color.text_muted))
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
        }
        header.addView(arrow)
        header.setOnClickListener { togglePerms(arrow, pCount) }
        card.addView(header)

        // ── Collapsible content ──
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(MP, dp(1)).apply {
                topMargin = dp(12); bottomMargin = dp(12)
            }
            setBackgroundColor(c(R.color.border))
        }
        content.addView(divider)

        // Permission rows with colored dots
        data class PermItem(val label: String, val check: () -> Boolean)
        val items = listOf(
            PermItem("通知使用权", ::isNotificationListenerEnabled),
            PermItem("无障碍权限", ::isAccessibilityEnabled),
            PermItem("悬浮窗权限", ::isOverlayEnabled),
            PermItem("电池白名单", ::isBatteryExempt),
            PermItem("牵牛花已安装", ::isQnhInstalled)
        )
        for ((label, check) in items) {
            content.addView(buildPermRow(label, check()))
        }

        val actionContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(12), 0, 0)
        }

        card.addView(content)
        card.addView(actionContainer)

        return PermsCardParts(card, arrow, pTitle, pCount, content, actionContainer)
    }

    private fun buildPermRow(label: String, granted: Boolean): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        // Status dot
        row.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(12) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (granted) c(R.color.status_granted) else c(R.color.status_denied))
            }
        })
        row.addView(TextView(requireContext()).apply {
            text = label
            textSize = 14f
            setTextColor(c(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = if (granted) "已授权" else "未授权"
            textSize = 13f
            setTextColor(if (granted) c(R.color.status_granted) else c(R.color.status_denied))
        })
        return row
    }

    // ═══════════ Feature Card ═══════════

    private fun buildFeatureCard(): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(6))
            setBackgroundResource(R.drawable.card_bg)
        }

        card.addView(TextView(requireContext()).apply {
            text = "功能开关"
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.text_primary))
            setPadding(0, 0, 0, dp(4))
        })

        // Divider
        card.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(MP, dp(1)).apply {
                topMargin = dp(8); bottomMargin = dp(4)
            }
            setBackgroundColor(c(R.color.border))
        })

        // Auto-enter switch
        val prefs = requireActivity().getSharedPreferences("qnh_helper", 0)
        val (row1, sw1) = buildFeatureSwitchRow(
            "自动进入拣货任务",
            "进入牵牛花后自动跳转到待领取页面",
            prefs.getBoolean("enabled", true)
        ) { _, isChecked ->
            requireActivity().getSharedPreferences("qnh_helper", 0).edit()
                .putBoolean("enabled", isChecked).apply()
            refreshAllStatus()
        }
        enableSwitch = sw1
        card.addView(row1)

        // Floating button switch
        val (row2, sw2) = buildFeatureSwitchRow(
            "悬浮窗按钮",
            "在屏幕边缘显示快捷操作按钮",
            FloatingWindowService.isEnabled(requireContext())
        ) { buttonView, isChecked ->
            if (isChecked && !isOverlayEnabled()) {
                Toast.makeText(requireContext(), "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                buttonView.isChecked = false
                return@buildFeatureSwitchRow
            }
            FloatingWindowService.setEnabled(requireContext(), isChecked)
            refreshAllStatus()
        }
        floatingSwitch = sw2
        card.addView(row2)

        // Back function switch
        val (row3, sw3) = buildFeatureSwitchRow(
            "页面回退功能",
            "到达目标页面后自动返回牵牛花主页",
            QnhLauncher.isBackEnabled(requireContext())
        ) { _, isChecked ->
            QnhLauncher.setBackEnabled(requireContext(), isChecked)
            refreshAllStatus()
        }
        backSwitch = sw3
        card.addView(row3)

        return card
    }

    private fun buildFeatureSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onChecked: (buttonView: Switch, isChecked: Boolean) -> Unit
    ): Pair<View, Switch> {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        val textCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(requireContext()).apply {
            text = title; textSize = 15f; setTextColor(c(R.color.text_primary))
        })
        textCol.addView(TextView(requireContext()).apply {
            text = subtitle; textSize = 12f; setTextColor(c(R.color.text_muted))
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(textCol)
        val sw = Switch(requireContext()).apply {
            isChecked = checked
            setOnCheckedChangeListener { bv, isC -> onChecked(bv as Switch, isC) }
        }
        row.addView(sw)
        return Pair(row, sw)
    }

    // ═══════════ Actions ═══════════

    private fun togglePerms(arrow: ImageView, countText: TextView) {
        permsExpanded = !permsExpanded
        val anim = if (permsExpanded) {
            RotateAnimation(0f, 180f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        } else {
            RotateAnimation(180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        }
        anim.duration = 250; anim.fillAfter = true
        arrow.startAnimation(anim)

        if (permsExpanded) {
            permsContent.visibility = View.VISIBLE
            permsActionContainer.visibility = if (hasUnauthorizedPerms()) View.VISIBLE else View.GONE
        } else {
            permsContent.visibility = View.GONE
            permsActionContainer.visibility = View.GONE
        }
    }

    private fun refreshAllStatus() {
        val granted = isNotificationListenerEnabled()
        val acc = isAccessibilityEnabled()
        val overlay = isOverlayEnabled()
        val battery = isBatteryExempt()
        val installed = isQnhInstalled()
        val prefs = requireActivity().getSharedPreferences("qnh_helper", 0)
        val enabled = prefs.getBoolean("enabled", true)

        val corePerms = listOf(granted, acc, installed)
        val coreGranted = corePerms.count { it }
        val allCoreOk = corePerms.all { it } && enabled

        val permItems = listOf(granted, acc, overlay, battery, installed)
        val totalGranted = permItems.count { it }

        // Status card
        if (allCoreOk) {
            statusTitle.text = "运行正常"
            statusDesc.text = "核心权限已就绪，自动拣货已启用"
            statusCard.setBackgroundResource(R.drawable.gradient_card_bg)
            (statusDot.background as? GradientDrawable)?.setColor(c(R.color.status_granted))
        } else {
            statusTitle.text = "需要设置"
            statusDesc.text = "有未授权的权限或功能未开启"
            statusCard.setBackgroundResource(R.drawable.gradient_warn_bg)
            (statusDot.background as? GradientDrawable)?.setColor(c(R.color.status_warning))
        }

        permsCount.text = "$totalGranted/${permItems.size}"
        permsCount.setTextColor(if (totalGranted == permItems.size) c(R.color.status_granted) else c(R.color.status_warning))

        if (::enableSwitch.isInitialized) enableSwitch.isChecked = enabled
        if (::floatingSwitch.isInitialized) floatingSwitch.isChecked = FloatingWindowService.isEnabled(requireContext())
        if (::backSwitch.isInitialized) backSwitch.isChecked = QnhLauncher.isBackEnabled(requireContext())

        refreshPermsActions()
    }

    private fun hasUnauthorizedPerms(): Boolean =
        !isNotificationListenerEnabled() || !isAccessibilityEnabled() ||
        !isOverlayEnabled() || !isBatteryExempt()

    private fun refreshPermsActions() {
        permsActionContainer.removeAllViews()
        if (!isNotificationListenerEnabled())
            permsActionContainer.addView(buildPermBtn("授予通知使用权") { openNotificationListenerSettings() })
        if (!isAccessibilityEnabled())
            permsActionContainer.addView(buildPermBtn("授予无障碍权限") { openAccessibilitySettings() })
        if (!isOverlayEnabled())
            permsActionContainer.addView(buildPermBtn("授予悬浮窗权限") { openOverlaySettings() })
        if (!isBatteryExempt())
            permsActionContainer.addView(buildPermBtn("加入电池白名单") { requestBatteryExemption() })
    }

    // ═══════════ Reusable Components ═══════════

    private fun buildVersionBadge(): View {
        return TextView(requireContext()).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            textSize = 11f
            setTextColor(c(R.color.qnh_primary))
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(0x1A16A34A.toInt())
            }
        }
    }

    private fun buildPermBtn(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(c(R.color.qnh_primary))
            setBackgroundResource(R.drawable.btn_outline)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            textSize = 13f
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(MP, WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
    }

    private fun buildPrimaryBtn(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(c(R.color.text_on_gradient))
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

    // ═══════════ Permission Checks ═══════════

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
    } catch (e: Exception) { false }

    private fun isOverlayEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(requireContext())
        else true

    private fun isBatteryExempt(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = requireContext().getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        } else true
    }

    // ═══════════ Settings Intents ═══════════

    private fun openNotificationListenerSettings() {
        val action = if (Build.VERSION.SDK_INT >= 22)
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        else
            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开设置：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings() {
        try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        catch (e: Exception) { Toast.makeText(requireContext(), "无法打开设置：${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "无法打开设置：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestBatteryExemption() {
        FloatingWindowService.requestBatteryExemption(requireContext())
        Toast.makeText(requireContext(), "请在电池设置中设为「无限制」", Toast.LENGTH_LONG).show()
    }

    private fun testOpenPickingTask() {
        if (!isQnhInstalled()) {
            Toast.makeText(requireContext(), "未检测到牵牛花已安装", Toast.LENGTH_SHORT).show(); return
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(requireContext(), "请先开启无障碍权限", Toast.LENGTH_LONG).show()
            openAccessibilitySettings(); return
        }
        if (!QnhLauncher.openPickingTaskEntry(requireContext()))
            Toast.makeText(requireContext(), "打开牵牛花失败", Toast.LENGTH_SHORT).show()
    }

    // ═══════════ Helpers ═══════════

    private fun c(id: Int): Int = ContextCompat.getColor(requireContext(), id)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp(bottom: Int = 0) = LinearLayout.LayoutParams(MP, WRAP_CONTENT).apply {
        this.bottomMargin = dp(bottom)
    }

    private fun spacer(h: Int): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(MP, h)
    }

    // ═══════════ Data Classes ═══════════

    private data class StatusCardParts(val card: View, val dot: View, val title: TextView, val desc: TextView)
    private data class PermsCardParts(val card: View, val arrow: ImageView, val title: TextView,
                                      val count: TextView, val content: LinearLayout, val actions: LinearLayout)
}
