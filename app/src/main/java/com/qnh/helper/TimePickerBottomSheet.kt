package com.qnh.helper

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

class TimePickerBottomSheet(
    context: Context,
    private val initialHour: Int = 9,
    private val initialMinute: Int = 0,
    private val title: String = "选择时间",
    private val onConfirm: (hour: Int, minute: Int) -> Unit
) {

    private val dialog: BottomSheetDialog
    private var hour = initialHour
    private var minute = initialMinute

    init {
        dialog = BottomSheetDialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.setContentView(buildView(context))
        dialog.behavior.isDraggable = true
        dialog.behavior.peekHeight = (context.resources.displayMetrics.heightPixels * 0.45).toInt()
    }

    private fun buildView(context: Context): View {
        val dp = { v: Int -> (v * context.resources.displayMetrics.density).toInt() }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(20), dp(12), dp(20), dp(24))
            background = context.getDrawable(R.drawable.bottom_sheet_bg)
        }

        val handle = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12)
            }
            setBackgroundColor(Color.parseColor("#E2E8F0"))
            setBackgroundResource(R.drawable.handle_bg)
        }
        root.addView(handle)

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val cancelBtn = TextView(context).apply {
            text = "取消"
            textSize = 16f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { dismiss() }
        }
        titleBar.addView(cancelBtn)

        val titleText = TextView(context).apply {
            text = title
            textSize = 17f
            setTextColor(Color.parseColor("#0F172A"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER
            }
            gravity = Gravity.CENTER
        }
        titleBar.addView(titleText)

        val confirmBtn = TextView(context).apply {
            text = "确定"
            textSize = 16f
            setTextColor(Color.parseColor("#3B82F6"))
            paint.isFakeBoldText = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                onConfirm(hour, minute)
                dismiss()
            }
        }
        titleBar.addView(confirmBtn)

        root.addView(titleBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(16)
        })

        val pickerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val hourPicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 23
            value = initialHour
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            setOnValueChangedListener { _, _, newVal -> hour = newVal }
            layoutParams = LinearLayout.LayoutParams(0, dp(180), 1f)
        }
        pickerRow.addView(hourPicker)

        val colon = TextView(context).apply {
            text = ":"
            textSize = 32f
            setTextColor(Color.parseColor("#0F172A"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            gravity = Gravity.CENTER
        }
        pickerRow.addView(colon)

        val minutePicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 59
            value = initialMinute
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            setFormatter { String.format("%02d", it) }
            setOnValueChangedListener { _, _, newVal -> minute = newVal }
            layoutParams = LinearLayout.LayoutParams(0, dp(180), 1f)
        }
        pickerRow.addView(minutePicker)

        root.addView(pickerRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        labels.addView(TextView(context).apply {
            text = "小时"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        })
        labels.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(1))
        })
        labels.addView(TextView(context).apply {
            text = "分钟"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        })
        root.addView(labels, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8)
        })

        return root
    }

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }
}
