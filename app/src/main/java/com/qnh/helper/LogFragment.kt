package com.qnh.helper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class LogFragment : Fragment() {

    private lateinit var logTextView: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }

        root.addView(TextView(requireContext()).apply {
            text = "操作日志"
            textSize = 24f
            setTextColor(0xFF0F172A.toInt())
            setPadding(0, dp(8), 0, dp(4))
        })

        root.addView(TextView(requireContext()).apply {
            text = "最近的操作记录，方便排查问题"
            textSize = 14f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 0, 0, dp(16))
        })

        swipeRefresh = SwipeRefreshLayout(requireContext()).apply {
            setOnRefreshListener {
                refreshLogs()
                isRefreshing = false
                Toast.makeText(requireContext(), "已刷新", Toast.LENGTH_SHORT).show()
            }
            setColorSchemeColors(0xFF3B82F6.toInt())
        }

        val logScroll = android.widget.ScrollView(requireContext()).apply {
            setBackgroundResource(R.drawable.log_bg)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        logTextView = TextView(requireContext()).apply {
            textSize = 11f
            setTextColor(0xFFE2E8F0.toInt())
            setLineSpacing(0f, 1.2f)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        logScroll.addView(logTextView)
        swipeRefresh.addView(logScroll)

        root.addView(swipeRefresh, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        root.addView(spacer(dp(12)))

        val btnRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val copyBtn = buildBtn("复制日志") { copyLogs() }
        val refreshBtn = buildBtn("刷新") { refreshLogs() }
        val clearBtn = buildBtn("清空") { clearLogs() }
        btnRow.addView(copyBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        btnRow.addView(refreshBtn, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
        btnRow.addView(clearBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(btnRow, lp())

        refreshLogs()
        return root
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    private fun buildBtn(text: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            this.text = text
            setTextColor(0xFF3B82F6.toInt())
            setBackgroundResource(R.drawable.btn_outline)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun copyLogs() {
        val logs = LogRecorder.getLogsAsString(requireContext())
        if (logs.isBlank()) {
            Toast.makeText(requireContext(), "暂无日志", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("牵牛花助手日志", logs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun refreshLogs() {
        val logs = LogRecorder.getAllLogs(requireContext())
        logTextView.text = if (logs.isEmpty()) "暂无日志" else logs.joinToString("\n")
    }

    private fun clearLogs() {
        LogRecorder.clearLogs(requireContext())
        logTextView.text = "暂无日志"
        Toast.makeText(requireContext(), "日志已清空", Toast.LENGTH_SHORT).show()
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
