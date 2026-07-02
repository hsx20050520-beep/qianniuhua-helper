package com.qnh.helper

import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
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
        root.addView(handHeader())
        root.addView(spacer(dp(6)))
        root.addView(handSubtitle())
        root.addView(spacer(dp(24)))

        // ── Sticky Note Status ──
        val sb = buildStickyStatus()
        statusBar = sb.first; statusDot = sb.second
        statusLabel = sb.third; statusSub = sb.fourth
        root.addView(sb.first)
        root.addView(spacer(dp(24)))

        // ── Permissions ──
        root.addView(handSectionLabel("✎ 权限"))
        root.addView(spacer(dp(10)))
        val p = buildPermSection()
        arrowIcon = p.first; permsContent = p.second; permsActions = p.third
        root.addView(p.fourth)
        root.addView(spacer(dp(24)))

        // ── Features ──
        root.addView(handSectionLabel("✎ 功能"))
        root.addView(spacer(dp(10)))
        val f = buildFeatureSection()
        root.addView(f.fourth)
        root.addView(spacer(dp(24)))

        // ── Button ──
        root.addView(handTestBtn())
        root.addView(spacer(dp(16)))

        // ── Tip ──
        root.addView(handTip())

        scroll.addView(root)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        refreshAllStatus()
    }

    // ═══════ Hand-drawn Header ═══════

    private fun handHeader(): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(requireContext()).apply {
            text = "牵牛花助手"
            textSize = 26f
            setTextColor(c(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = "v${BuildConfig.VERSION_NAME}"
            textSize = 11f
            setTextColor(c(R.color.text_muted))
            setPadding(0, dp(4), 0, 0)
        })
        return row
    }

    private fun handSubtitle(): View {
        return TextView(requireContext()).apply {
            text = "✎ 手绘版  ·  自动跳转到拣货任务"
            textSize = 13f
            setTextColor(c(R.color.text_muted))
        }
    }

    private fun handSectionLabel(text: String): View {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            setTextColor(c(R.color.text_muted))
        }
    }

    // ═══════ Sticky Note Status ═══════

    private fun buildStickyStatus(): Quad<View, View, TextView, TextView> {
        val sticky = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_sticky)
        }

        val dot = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(12) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c(R.color.status_granted))
            }
        }
        sticky.addView(dot)

        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val label = TextView(requireContext()).apply {
            text = "运行正常 ✓"
            textSize = 16f
            setTextColor(c(R.color.text_on_sticky))
        }
        col.addView(label)

        val sub = TextView(requireContext()).apply {
            text = "权限完备，随时待命"
            textSize = 12f
            setTextColor(c(R.color.text_muted))
            setPadding(0, dp(2), 0, 0)
        }
        col.addView(sub)
        sticky.addView(col)

        return Quad(sticky, dot, label, sub)
    }

    // ═══════ Permissions ═══════

    private fun buildPermSection(): Quad<ImageView, LinearLayout, LinearLayout, View> {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_paper)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(requireContext()).apply {
            text = "权限状态"
            textSize = 14f
            setTextColor(c(R.color.text_primary))
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

        val items = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        container.addView(spacer(dp(4)))
        container.addView(thinDiv())
        container.addView(spacer(dp(4)))

        listOf(
            "通知使用权" to ::isNotifEnabled,
            "无障碍权限" to ::isAccessEnabled,
            "悬浮窗权限" to ::isOverlayOn,
            "电池白名单" to ::isBatterySafe,
            "牵牛花已安装" to ::isQnhThere
        ).forEach { (label, check) ->
            items.addView(handPermRow(label, check()))
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

    private fun handPermRow(label: String, ok: Boolean): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        row.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(10) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (ok) c(R.color.status_granted) else c(R.color.status_denied))
            }
        })
        row.addView(TextView(requireContext()).apply {
            text = label
            textSize = 14f
            setTextColor(c(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = if (ok) "✓ 已授权" else "✗ 未授权"
            textSize = 13f
            setTextColor(if (ok) c(R.color.status_granted) else c(R.color.status_denied))
        })
        return row
    }

    // ═══════ Features ═══════

    private fun buildFeatureSection(): Quad<Switch, Switch, Switch, View> {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_paper)
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }

        val prefs = requireActivity().getSharedPreferences("qnh_helper", 0)

        val r1 = handSwitch("自动进入拣货任务", "跳转到待领取页面",
            prefs.getBoolean("enabled", true)) { _, c ->
            prefs.edit().putBoolean("enabled", c).apply(); refreshAllStatus()
        }
        enableSwitch = r1.second; container.addView(r1.first)
        container.addView(thinDiv())

        val r2 = handSwitch("悬浮窗按钮", "屏幕边缘快捷操作",
            FloatingWindowService.isEnabled(requireContext())) { v, c ->
            if (c && !isOverlayOn()) {
                Toast.makeText(requireContext(), "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                (v as Switch).isChecked = false; return@handSwitch
            }
            FloatingWindowService.setEnabled(requireContext(), c); refreshAllStatus()
        }
        floatingSwitch = r2.second; container.addView(r2.first)
        container.addView(thinDiv())

        val r3 = handSwitch("页面回退功能", "到达后自动返回主页",
            QnhLauncher.isBackEnabled(requireContext())) { _, c ->
            QnhLauncher.setBackEnabled(requireContext(), c); refreshAllStatus()
        }
        backSwitch = r3.second; container.addView(r3.first)

        return Quad(enableSwitch, floatingSwitch, backSwitch, container)
    }

    private fun handSwitch(
        title: String, sub: String, checked: Boolean,
        onChange: (View, Boolean) -> Unit
    ): Pair<View, Switch> {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val col = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        col.addView(TextView(requireContext()).apply {
            text = title; textSize = 15f; setTextColor(c(R.color.text_primary))
        })
        col.addView(TextView(requireContext()).apply {
            text = sub; textSize = 12f; setTextColor(c(R.color.text_muted))
            setPadding(0, dp(2), 0, 0)
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

    private fun handTestBtn(): Button {
        return Button(requireContext()).apply {
            text = "☺ 测试：进入待领取"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.btn_hand)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            textSize = 14f
            setOnClickListener { testOpen() }
        }
    }

    private fun handTip(): View {
        return TextView(requireContext()).apply {
            text = "✎ 提示：建议开启电池白名单，防止杀后台"
            textSize = 12f
            setTextColor(c(R.color.text_muted))
            gravity = Gravity.CENTER
        }
    }

    private fun thinDiv(): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(MP, dp(1))
        setBackgroundResource(R.drawable.divider_hand)
    }

    // ═══════ Actions ═══════

    private fun togglePerms(arrow: ImageView, count: TextView) {
        permsExpanded = !permsExpanded
        val anim = if (permsExpanded)
            RotateAnimation(0f, 180f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        else
            RotateAnimation(180f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        anim.duration = 200; anim.fillAfter = true
        arrow.startAnimation(anim)
        if (permsExpanded) {
            permsContent.visibility = View.VISIBLE
            permsActions.visibility = if (hasMissing()) View.VISIBLE else View.GONE
        } else {
            permsContent.visibility = View.GONE; permsActions.visibility = View.GONE
        }
    }

    private fun refreshAllStatus() {
        val nl = isNotifEnabled(); val acc = isAccessEnabled()
        val ov = isOverlayOn(); val bat = isBatterySafe(); val inst = isQnhThere()
        val prefs = requireActivity().getSharedPreferences("qnh_helper", 0)
        val enabled = prefs.getBoolean("enabled", true)

        val coreOk = listOf(nl, acc, inst).all { it } && enabled
        val totalOk = listOf(nl, acc, ov, bat, inst).count { it }

        if (coreOk) {
            statusLabel.text = "运行正常 ✓"
            statusSub.text = "权限完备，随时待命"
            statusBar.setBackgroundResource(R.drawable.bg_sticky)
            (statusDot.background as? GradientDrawable)?.setColor(c(R.color.status_granted))
        } else {
            statusLabel.text = "需要配置 ✗"
            statusSub.text = "有权限未授权或功能已停用"
            statusBar.setBackgroundResource(R.drawable.bg_sticky_warn)
            (statusDot.background as? GradientDrawable)?.setColor(c(R.color.status_warning))
        }

        if (::enableSwitch.isInitialized) enableSwitch.isChecked = enabled
        if (::floatingSwitch.isInitialized) floatingSwitch.isChecked = FloatingWindowService.isEnabled(requireContext())
        if (::backSwitch.isInitialized) backSwitch.isChecked = QnhLauncher.isBackEnabled(requireContext())
        refreshActions()
    }

    private fun hasMissing(): Boolean =
        !isNotifEnabled() || !isAccessEnabled() || !isOverlayOn() || !isBatterySafe()

    private fun refreshActions() {
        permsActions.removeAllViews()
        if (!isNotifEnabled()) permsActions.addView(handPermBtn("授予通知使用权") { openNotif() })
        if (!isAccessEnabled()) permsActions.addView(handPermBtn("授予无障碍权限") { openAcc() })
        if (!isOverlayOn()) permsActions.addView(handPermBtn("授予悬浮窗权限") { openOverlay() })
        if (!isBatterySafe()) permsActions.addView(handPermBtn("加入电池白名单") { reqBattery() })
    }

    private fun handPermBtn(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(c(R.color.qnh_primary))
            setBackgroundResource(R.drawable.btn_outline_hand)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            textSize = 13f
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(MP, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }
    }

    // ═══════ Permission Checks ═══════

    private fun isNotifEnabled(): Boolean {
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners") ?: return false
        if (TextUtils.isEmpty(flat)) return false
        val t = ComponentName(requireContext(), QnhNotificationListenerService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == t }
    }

    private fun isAccessEnabled(): Boolean {
        val flat = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        if (TextUtils.isEmpty(flat)) return false
        val t = ComponentName(requireContext(), QnhAccessibilityService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == t }
    }

    private fun isQnhThere(): Boolean = try {
        requireContext().packageManager.getPackageInfo(QnhLauncher.QNH_PACKAGE, 0); true
    } catch (_: Exception) { false }

    private fun isOverlayOn(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(requireContext())
        else true

    private fun isBatterySafe(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = requireContext().getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(requireContext().packageName)
        } else true
    }

    // ═══════ Intents ═══════

    private fun openNotif() {
        val a = if (Build.VERSION.SDK_INT >= 22) Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        else "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        try { startActivity(Intent(a)) }
        catch (e: Exception) { Toast.makeText(requireContext(), "无法打开设置", Toast.LENGTH_LONG).show() }
    }

    private fun openAcc() {
        try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        catch (e: Exception) { Toast.makeText(requireContext(), "无法打开设置", Toast.LENGTH_LONG).show() }
    }

    private fun openOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")))
            } catch (e: Exception) { Toast.makeText(requireContext(), "无法打开设置", Toast.LENGTH_LONG).show() }
        }
    }

    private fun reqBattery() {
        FloatingWindowService.requestBatteryExemption(requireContext())
        Toast.makeText(requireContext(), "请在电池设置中设为「无限制」", Toast.LENGTH_LONG).show()
    }

    private fun testOpen() {
        if (!isQnhThere()) {
            Toast.makeText(requireContext(), "未检测到牵牛花已安装", Toast.LENGTH_SHORT).show(); return
        }
        if (!isAccessEnabled()) {
            Toast.makeText(requireContext(), "请先开启无障碍权限", Toast.LENGTH_LONG).show()
            openAcc(); return
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
