package com.qnh.helper

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class HouerjiaFragment : Fragment() {

    private lateinit var statusCard: TextView
    private lateinit var alarmsContainer: LinearLayout

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
            text = "猴儿家打卡助手"
            textSize = 24f
            setTextColor(0xFF0F172A.toInt())
            setPadding(0, dp(8), 0, dp(4))
        })

        root.addView(TextView(requireContext()).apply {
            text = "定时提醒 + 自动跳转排班考勤"
            textSize = 14f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 0, 0, dp(16))
        })

        statusCard = TextView(requireContext()).apply {
            textSize = 13f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.card_bg)
        }
        root.addView(statusCard, lp())
        (statusCard.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(16)

        val openBtn = buildPrimaryBtn("立即打开排班考勤") { testOpenHouerjia() }
        root.addView(openBtn, lp())
        root.addView(spacer(dp(16)))

        root.addView(buildSectionTitle("定时打卡"))

        alarmsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(alarmsContainer, lp())
        root.addView(spacer(dp(8)))

        val addBtn = buildAddBtn()
        root.addView(addBtn, lp())

        root.addView(spacer(dp(16)))
        root.addView(buildTipCard("💡 点击时间可修改，长按可删除定时"))

        scroll.addView(root)
        return scroll
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshAlarms()
    }

    private fun buildSectionTitle(text: String): View {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setTextColor(0xFF334155.toInt())
            setPadding(0, dp(4), 0, dp(10))
        }
    }

    private fun buildPrimaryBtn(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.btn_primary)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { onClick() }
        }
    }

    private fun buildAddBtn(): Button {
        return Button(requireContext()).apply {
            text = "+ 添加定时打卡"
            setTextColor(0xFF3B82F6.toInt())
            setBackgroundColor(0xFFEFF6FF.toInt())
            setBackgroundResource(R.drawable.btn_outline)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener {
                showTimePickerDialog { hour, minute ->
                    val alarm = AlarmScheduler.addAlarm(requireContext(), hour, minute)
                    Toast.makeText(requireContext(), "已添加定时：${AlarmScheduler.formatTime(hour, minute)}", Toast.LENGTH_SHORT).show()
                    refreshAlarms()
                    refreshStatus()
                }
            }
        }
    }

    private fun buildTipCard(text: String): View {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(R.drawable.tip_bg)
        }
    }

    private fun buildAlarmRow(alarm: AlarmItem): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(10), dp(4), dp(10))
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.alarm_row_bg)
        }

        val timeText = TextView(requireContext()).apply {
            text = AlarmScheduler.formatTime(alarm.hour, alarm.minute)
            textSize = 22f
            setTextColor(if (alarm.enabled) 0xFF0F172A.toInt() else 0xFF94A3B8.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                showTimePickerDialog(alarm.hour, alarm.minute) { h, m ->
                    AlarmScheduler.updateAlarmTime(requireContext(), alarm.id, h, m)
                    Toast.makeText(requireContext(), "已修改为 ${AlarmScheduler.formatTime(h, m)}", Toast.LENGTH_SHORT).show()
                    refreshAlarms()
                    refreshStatus()
                }
            }
            setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("删除定时")
                    .setMessage("确定要删除 ${AlarmScheduler.formatTime(alarm.hour, alarm.minute)} 的定时吗？")
                    .setPositiveButton("删除") { _, _ ->
                        AlarmScheduler.deleteAlarm(requireContext(), alarm.id)
                        Toast.makeText(requireContext(), "已删除定时", Toast.LENGTH_SHORT).show()
                        refreshAlarms()
                        refreshStatus()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        }
        row.addView(timeText)

        val switch = Switch(requireContext()).apply {
            isChecked = alarm.enabled
            setOnCheckedChangeListener { _, isChecked ->
                AlarmScheduler.updateAlarmEnabled(requireContext(), alarm.id, isChecked)
                timeText.setTextColor(if (isChecked) 0xFF0F172A.toInt() else 0xFF94A3B8.toInt())
                refreshStatus()
            }
        }
        row.addView(switch)

        return row
    }

    private fun refreshStatus() {
        val installed = QnhLauncher.isHouerjiaInstalled(requireContext())
        val accessibilityGranted = isAccessibilityEnabled()
        val alarms = AlarmScheduler.getAllAlarms(requireContext())
        val enabledCount = alarms.count { it.enabled }

        val sb = StringBuilder()
        sb.append("猴儿家V2：").append(if (installed) "已安装 ✅" else "未安装 ❌").append('\n')
        sb.append("无障碍权限：").append(if (accessibilityGranted) "已授权 ✅" else "未授权 ❌").append('\n')
        sb.append("定时任务：").append(if (alarms.isEmpty()) "未设置" else "$enabledCount 个已启用")
        statusCard.text = sb.toString()
    }

    private fun refreshAlarms() {
        alarmsContainer.removeAllViews()
        val alarms = AlarmScheduler.getAllAlarms(requireContext())
        if (alarms.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "暂无定时，点击下方按钮添加"
                textSize = 13f
                setTextColor(0xFF94A3B8.toInt())
                setPadding(dp(4), dp(12), dp(4), dp(12))
            }
            alarmsContainer.addView(empty)
            return
        }
        for (alarm in alarms) {
            val row = buildAlarmRow(alarm)
            alarmsContainer.addView(row, lp())
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        if (TextUtils.isEmpty(flat)) return false
        val target = ComponentName(requireContext(), QnhAccessibilityService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == target }
    }

    private fun testOpenHouerjia() {
        if (!QnhLauncher.isHouerjiaInstalled(requireContext())) {
            Toast.makeText(requireContext(), "未检测到猴儿家V2已安装", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityEnabled()) {
            Toast.makeText(requireContext(), "请先开启无障碍权限", Toast.LENGTH_LONG).show()
            try {
                startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {}
            return
        }
        if (QnhLauncher.openHouerjiaAttendance(requireContext())) {
            Toast.makeText(requireContext(), "正在打开猴儿家排班考勤", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "打开猴儿家失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTimePickerDialog(defaultHour: Int = -1, defaultMinute: Int = -1, onTimeSet: (hour: Int, minute: Int) -> Unit) {
        val cal = java.util.Calendar.getInstance()
        val h = if (defaultHour < 0) cal.get(java.util.Calendar.HOUR_OF_DAY) else defaultHour
        val m = if (defaultMinute < 0) cal.get(java.util.Calendar.MINUTE) else defaultMinute
        TimePickerBottomSheet(
            requireContext(),
            h,
            m,
            "选择时间"
        ) { hour, minute ->
            onTimeSet(hour, minute)
        }.show()
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
