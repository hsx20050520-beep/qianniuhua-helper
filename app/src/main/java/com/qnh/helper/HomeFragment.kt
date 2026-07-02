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

    private lateinit var statusBar: View
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var statusSub: TextView

    private lateinit var arrowIcon: ImageView
    private lateinit var permsContent: LinearLayout
    private lateinit var permsActions: LinearLayout
    private var permsExpanded = false

    private lateinit var enableSwitch: Switch
    private lateinit var floatingSwitch: Switch
    private lateinit var backSwitch: Switch

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
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        // ── Header ──
        root.addView(buildHeader())
        root.addView(spacer(dp(28)))

        // ── Status Bar ──
        val sb = buildStatusBar()
        statusBar = sb.first; statusDot = sb.second; statusLabel = sb.third; statusSub = sb.fourth
        root.addView(sb.first)

        root.addView(spacer(dp(28)))

        // ── Section: Permissions ──
        root.addView(buildSectionTitle("权限"))
        root.addView(spacer(dp(12)))
        val p = buildPermsSection()
        arrowIcon = p.first; permsContent = p.second; permsActions = p.third
        root.addView(p.fourth) // the perms container view

        root.addView(spacer(dp(28)))

        // ── Section: 功能 ──
        root.addView(buildSectionTitle("功能"))
        root.addView(spacer(dp(12)))
        val f = buildFeatureSection()
        root.addView(f.fourth)

        root.addView(spacer(dp(28)))

        // ── Test Button ──
        root.addView(buildTestBtn())

        root.addView(spacer(dp(20)))

        // ── Tip ──
        root.addView(buildTip())

        scroll.addView(root)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatus()
    }

    // ═══════ Header ═══════

    private fun buildHeader(): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(requireContext()).apply {
            text = "牵牛花助手"
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(c(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            textSize = 12f
            setTextColor(c(R.color.text_muted))
        })
        return row
    }

    // ═══════ Status Bar ═══════

    private fun buildStatusBar(): Quad<View, View, TextView, TextView> {
        val bar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_status_granted)
        }

        val dot = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(12) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFFFFFF.toInt())
            }
        }
        bar.addView(dot)

        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val label = TextView(requireContext()).apply {
            text = "运行正常"
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
        }
        col.addView(label)

        val sub = TextView(requireContext()).apply {
            text = "权限完备，随时待命"
            textSize = 12f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, dp(2), 0, 0)
        }
        col.addView(sub)
        bar.addView(col)

        return Quad(bar, dot, label, sub)
    }

    // ═══════ Permissions ═══════

    private fun buildPermsSection(): Quad<ImageView, LinearLayout, LinearLayout, View> {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Collapse header
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(6), dp(2), dp(6))
        }
        header.addView(TextView(requireContext()).apply {
            text = "权限状态"
            textSize = 14f
            setTextColor(c(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })

        val count = TextView(requireContext()).apply {
            id = View.generateViewId()
            text = "5/5"
            textSize = 13f
            setTextColor(c(R.color.status_granted))
            setPadding(0, 0, dp(6), 0)
        }
        header.addView(count)

        val arrow = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            setColorFilter(c(R.color.text_muted))
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14))
        }
        header.addView(arrow)
        header.setOnClickListener { togglePerms(arrow, count) }
        container.addView(header)

        // Collapsible items
        val items = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val permList = listOf(
            "通知使用权" to ::isNotificationListenerEnabled,
            "无障碍权限" to ::isAccessibilityEnabled,
            "悬浮窗权限" to ::isOverlayEnabled,
            "电池白名单" to ::isBatteryExempt,
            "牵牛花已安装" to ::isQnhInstalled
        )

        // thin divider before items
        items.addView(thinDivider())
        items.addView(spacer(dp(4)))

        for ((label, check) in permList) {
            items.addView(buildPermRow(label, check()))
        }

        val actions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }

        container.addView(items)
        container.addView(actions)

        return Quad(arrow, items, actions, container)
    }

    private fun buildPermRow(label: String, granted: Boolean): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(10) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (granted) c(R.color.status_granted) else c(R.color.status_denied))
            }
        })
        row.addView(TextView(requireContext()).apply {
            text = label
            textSize = 14f
            setTextColor(c(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = if (granted) "已授权" else "未授权"
            textSize = 13f
            setTextColor(if (granted) c(R.color.status_granted) else c(R.color.status_denied))
        })
        return row
    }

    // ═══════ Features ═══════

    private fun buildFeatureSection(): Quad<View, View, View, View> {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val prefs = requireActivity().getSharedPreferences("qnh_helper", 0)

        val r1 = buildSwitchRow("自动进入拣货任务", "跳转到待领取页面",
            prefs.getBoolean("enabled", true)) { _, c ->
            prefs.edit().putBoolean("enabled", c).apply()
            refreshAllStatus()
        }
        enableSwitch = r1.second
        container.addView(r1.first)

        container.addView(thinDivider())

        val r2 = buildSwitchRow("悬浮窗按钮", "屏幕边缘快捷操作",
            FloatingWindowService.isEnabled(requireContext())) { v, c ->
            if (c && !isOverlayEnabled()) {
                Toast.makeText(requireContext(), "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                (v as Switch).isChecked = false
                return@buildSwitchRow
            }
            FloatingWindowService.setEnabled(requireContext(), c)
            refreshAllStatus()
        }
        floatingSwitch = r2.second
        container.addView(r2.first)

        container.addView(thinDivider())

        val r3 = buildSwitchRow("页面回退功能", "到达后自动返回主页",
            QnhLauncher.isBackEnabled(requireContext())) { _, c ->
            QnhLauncher.setBackEnabled(requireContext(), c)
            refreshAllStatus()
        }
        backSwitch = r3.second
        container.addView(r3.first)

        return Quad(r1.first, r2.first, r3.first, container)
    }

    private fun buildSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onChange: (View, Boolean) -> Unit
    ): Pair<View, Switch> {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        col.addView(TextView(requireContext()).apply {
            text = title; textSize = 15f; setTextColor(c(R.color.text_primary))
        })
        col.addView(TextView(requireContext()).apply {
            text = subtitle; textSize = 12f; setTextColor(c(R.color.text_muted))
            setPadding(0, dp(1), 0, 0)
        })
        row.addView(col)
        val sw = Switch(requireContext()).apply {
            isChecked = checked
            setOnCheckedChangeListener { v, c -> onChange(v, c) }
        }
        row.addView(sw)
        return Pair(row, sw)
    }

    // ═══════ Components ═══════

    private fun buildSectionTitle(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 11f
            setTextColor(c(R.color.text_muted))
            setText(text)
            letterSpacing = 0.06f
        }
    }

    private fun buildTestBtn(): Button {
        return Button(requireContext()).apply {
            text = "测试：进入待领取"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.btn_green)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            textSize = 14f
            setOnClickListener { testOpenPickingTask() }
        }
    }

    private fun buildTip(): View {
        return TextView(requireContext()).apply {
            text = "建议开启电池白名单，防止系统杀后台"
            textSize = 12f
            setTextColor(c(R.color.text_muted))
            gravity = Gravity.CENTER
        }
    }

    private fun thinDivider(): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(MP, dp(1)).apply {
            leftMargin = dp(0); rightMargin = dp(0)
        }
        setBackgroundColor(c(R.color.border))
    }

    // ═══════ Actions ═══════

    private fun togglePerms(arrow: ImageView, countText: TextView) {
        permsExpanded = !permsExpanded
        val anim = if (permsExpanded)
            RotateAnimation(0f, 180f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        else
            RotateAnimation(180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        anim.duration = 200; anim.fillAfter = true
        arrow.startAnimation(anim)
        if (permsExpanded) {
            permsContent.visibility = View.VISIBLE
            permsActions.visibility = if (hasUnauthorizedPerms()) View.VISIBLE else View.GONE
        } else {
            permsContent.visibility = View.GONE
            permsActions.visibility = View.GONE
        }
    }

    private fun refreshAllStatus() {
        val nl = isNotificationListenerEnabled()
        val acc = isAccessibilityEnabled()
        val ov = isOverlayEnabled()
        val bat = isBatteryExempt()
        val inst = isQnhInstalled()
        val prefs = requireActivity().getSharedPreferences("qnh_helper", 0)
        val enabled = prefs.getBoolean("enabled", true)

        val coreOk = listOf(nl, acc, inst).all { it } && enabled
        val totalOk = listOf(nl, acc, ov, bat, inst).count { it }

        if (coreOk) {
            statusLabel.text = "运行正常"
            statusSub.text = "权限完备，随时待命"
            statusBar.setBackgroundResource(R.drawable.bg_status_granted)
            (statusDot.background as? GradientDrawable)?.setColor(0xFFFFFFFF.toInt())
        } else {
            statusLabel.text = "需要配置"
            statusSub.text = "有权限未授权或功能已停用"
            statusBar.setBackgroundResource(R.drawable.bg_status_warning)
            (statusDot.background as? GradientDrawable)?.setColor(0xFFFFFFFF.toInt())
        }

        if (::enableSwitch.isInitialized) enableSwitch.isChecked = enabled
        if (::floatingSwitch.isInitialized) floatingSwitch.isChecked = FloatingWindowService.isEnabled(requireContext())
        if (::backSwitch.isInitialized) backSwitch.isChecked = QnhLauncher.isBackEnabled(requireContext())
        refreshPermsActions()
    }

    private fun hasUnauthorizedPerms(): Boolean =
        !isNotificationListenerEnabled() || !isAccessibilityEnabled() ||
        !isOverlayEnabled() || !isBatteryExempt()

    private fun refreshPermsActions() {
        permsActions.removeAllViews()
        if (!isNotificationListenerEnabled())
            permsActions.addView(permBtn("授予通知使用权") { openNotificationListenerSettings() })
        if (!isAccessibilityEnabled())
            permsActions.addView(permBtn("授予无障碍权限") { openAccessibilitySettings() })
        if (!isOverlayEnabled())
            permsActions.addView(permBtn("授予悬浮窗权限") { openOverlaySettings() })
        if (!isBatteryExempt())
            permsActions.addView(permBtn("加入电池白名单") { requestBatteryExemption() })
    }

    private fun permBtn(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(c(R.color.qnh_primary))
            setBackgroundResource(R.drawable.btn_outline)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            textSize = 13f
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(MP, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }
    }

    // ═══════ Permission Checks ═══════

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
        requireContext().packageManager.getPackageInfo(QnhLauncher.QNH_PACKAGE, 0); true
    } catch (_: Exception) { false }

    private fun isOverlayEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(requireContext())
        else true

    private fun isBatteryExempt(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = requireContext().getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        } else true
    }

    // ═══════ Settings Intents ═══════

    private fun openNotificationListenerSettings() {
        val action = if (Build.VERSION.SDK_INT >= 22)
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        else "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        try { startActivity(Intent(action)) }
        catch (e: Exception) { Toast.makeText(requireContext(), "无法打开设置", Toast.LENGTH_LONG).show() }
    }

    private fun openAccessibilitySettings() {
        try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        catch (e: Exception) { Toast.makeText(requireContext(), "无法打开设置", Toast.LENGTH_LONG).show() }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")))
            } catch (e: Exception) { Toast.makeText(requireContext(), "无法打开设置", Toast.LENGTH_LONG).show() }
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

    // ═══════ Helpers ═══════

    private fun c(id: Int): Int = ContextCompat.getColor(requireContext(), id)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun spacer(h: Int): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(MP, h)
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        private val WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT
        private val MP = LinearLayout.LayoutParams.MATCH_PARENT
    }
}
