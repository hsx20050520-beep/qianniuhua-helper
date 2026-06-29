package com.qnh.helper

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object QnhLauncher {
    private const val TAG = "QnhLauncher"
    private const val PREFS = "qnh_helper"
    private const val KEY_PICKING_TASK_PENDING_UNTIL = "picking_task_pending_until"
    private const val KEY_HOUERJIA_PENDING_UNTIL = "houerjia_pending_until"
    private const val KEY_BACK_ENABLED = "back_enabled"
    private const val KEY_QNH_LAST_FOREGROUND = "qnh_last_foreground"
    private const val PICKING_TASK_TIMEOUT_MS = 120_000L
    const val QNH_PACKAGE = "com.sankuai.scsx.android.shuguopai"
    const val QNH_STATE_FOREGROUND = 0
    const val QNH_STATE_BACKGROUND = 1
    const val QNH_STATE_UNKNOWN = 2
    private const val BACKGROUND_THRESHOLD_MS = 30_000L

    fun isBackEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACK_ENABLED, true)
    }

    fun setBackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BACK_ENABLED, enabled)
            .apply()
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isQnhRunning(context: Context): Boolean {
        if (!hasUsageStatsPermission(context)) return false
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            time - 1000 * 60 * 30,
            time
        )
        var lastUsed = 0L
        for (s in stats) {
            if (s.packageName == QNH_PACKAGE && s.lastTimeUsed > lastUsed) {
                lastUsed = s.lastTimeUsed
            }
        }
        if (lastUsed == 0L) return false
        val elapsed = time - lastUsed
        return elapsed < 1000 * 60 * 5
    }

    fun markQnhForeground(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_QNH_LAST_FOREGROUND, System.currentTimeMillis())
            .apply()
    }

    fun getQnhState(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val processes = am.runningAppProcesses
        for (p in processes) {
            if (p.processName == QNH_PACKAGE) {
                return when (p.importance) {
                    android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                    android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> QNH_STATE_FOREGROUND
                    else -> QNH_STATE_BACKGROUND
                }
            }
        }
        try {
            val services = am.getRunningServices(100)
            for (s in services) {
                if (s.service?.packageName == QNH_PACKAGE) {
                    return QNH_STATE_BACKGROUND
                }
            }
        } catch (_: Exception) {}
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastForeground = prefs.getLong(KEY_QNH_LAST_FOREGROUND, 0L)
        if (lastForeground == 0L) return QNH_STATE_UNKNOWN
        val elapsed = System.currentTimeMillis() - lastForeground
        return if (elapsed < 2000) {
            QNH_STATE_FOREGROUND
        } else if (elapsed < BACKGROUND_THRESHOLD_MS) {
            QNH_STATE_BACKGROUND
        } else {
            QNH_STATE_UNKNOWN
        }
    }

    fun openPickingTaskEntry(context: Context): Boolean {
        markPickingTaskPending(context)
        val msg = "准备打开牵牛花拣货任务，pending 已设置（${System.currentTimeMillis()}）"
        Log.i(TAG, msg)
        LogRecorder.i(context, TAG, msg)
        return bringToFront(context)
    }

    fun markPickingTaskPending(context: Context) {
        val until = System.currentTimeMillis() + PICKING_TASK_TIMEOUT_MS
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PICKING_TASK_PENDING_UNTIL, until)
            .apply()
        val msg = "pending 设置，截止时间：$until（${PICKING_TASK_TIMEOUT_MS / 1000}秒后过期）"
        Log.d(TAG, msg)
        LogRecorder.d(context, TAG, msg)
    }

    fun isPickingTaskPending(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_PICKING_TASK_PENDING_UNTIL, 0L)
        val now = System.currentTimeMillis()
        val result = until > now
        if (!result) {
            val msg = "pending 已过期（until=$until, now=$now）"
            Log.d(TAG, msg)
            LogRecorder.d(context, TAG, msg)
        }
        return result
    }

    fun getPendingAgeMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_PICKING_TASK_PENDING_UNTIL, 0L)
        if (until == 0L) return Long.MAX_VALUE
        val elapsed = PICKING_TASK_TIMEOUT_MS - (until - System.currentTimeMillis())
        return if (elapsed > 0) elapsed else 0L
    }

    fun clearPickingTaskPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PICKING_TASK_PENDING_UNTIL)
            .apply()
        LogRecorder.d(context, TAG, "pending 已清除")
    }

    fun openMain(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(QNH_PACKAGE) ?: return false
        intent.addFlags(commonFlags())
        return startSafely(context, intent, "牵牛花主入口")
    }

    fun bringToFront(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(QNH_PACKAGE) ?: return false
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        return startSafely(context, intent, "牵牛花带到前台")
    }

    fun openWorkbench(context: Context): Boolean {
        // 牵牛花工作台没有公开文档化的 deep link，因此先尝试常见页面协议。
        // 若牵牛花未暴露对应协议，则回退到带“工作台/拣货”参数的主入口启动。
        val candidates = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("shuguopai://workbench")).setPackage(QNH_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("shuguopai://workbench/picking")).setPackage(QNH_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("qianniuhua://workbench")).setPackage(QNH_PACKAGE),
            Intent(Intent.ACTION_VIEW, Uri.parse("qianniuhua://workbench/picking")).setPackage(QNH_PACKAGE)
        )

        for (candidate in candidates) {
            candidate.addFlags(commonFlags())
            if (candidate.resolveActivity(context.packageManager) != null &&
                startSafely(context, candidate, "牵牛花工作台协议")
            ) {
                return true
            }
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(QNH_PACKAGE) ?: return false
        launchIntent.addFlags(commonFlags())
        launchIntent.putExtra("target_tab", "workbench")
        launchIntent.putExtra("target_page", "picking")
        launchIntent.putExtra("tab", "工作台")
        launchIntent.putExtra("page", "拣货")
        launchIntent.putExtra("from_qnh_helper", true)
        return startSafely(context, launchIntent, "牵牛花工作台回退入口")
    }

    private fun commonFlags(): Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP

    fun openHouerjia(context: Context): Boolean {
        val pm = context.packageManager
        val appName = "猴儿家V2"
        var targetPackage: String? = null

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            if (label.contains(appName)) {
                targetPackage = info.activityInfo.packageName
                break
            }
        }

        if (targetPackage == null) {
            val msg = "未找到应用「$appName」，请确认已安装"
            Log.w(TAG, msg)
            LogRecorder.w(context, TAG, msg)
            return false
        }

        val launchIntent = pm.getLaunchIntentForPackage(targetPackage) ?: return false
        launchIntent.addFlags(commonFlags())
        val success = startSafely(context, launchIntent, "猴儿家V2（$targetPackage）")
        if (success) {
            // 保存找到的包名，避免下次再遍历
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("houerjia_package", targetPackage).apply()
        }
        return success
    }

    fun openHouerjiaAttendance(context: Context): Boolean {
        markHouerjiaPending(context)
        val msg = "准备打开猴儿家排班考勤，pending 已设置"
        Log.i(TAG, msg)
        LogRecorder.i(context, TAG, msg)
        return openHouerjia(context)
    }

    private const val HOUERJIA_TIMEOUT_MS = 60_000L

    fun markHouerjiaPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("houerjia_pending", true)
            .commit()
    }

    fun isHouerjiaPending(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("houerjia_pending", false)
    }

    fun clearHouerjiaPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("houerjia_pending", false)
            .commit()
    }

    fun getHouerjiaPackage(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("houerjia_package", null)
    }

    fun isHouerjiaInstalled(context: Context): Boolean {
        // 先查缓存
        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("houerjia_package", null)
        if (cached != null) {
            return try {
                context.packageManager.getPackageInfo(cached, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
        // 遍历查找
        val pm = context.packageManager
        val appName = "猴儿家V2"
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        for (info in resolveInfos) {
            if (info.loadLabel(pm).toString().contains(appName)) {
                return true
            }
        }
        return false
    }

    private fun startSafely(context: Context, intent: Intent, label: String): Boolean {
        return try {
            context.startActivity(intent)
            val msg = "启动成功：$label"
            Log.i(TAG, msg)
            LogRecorder.i(context, TAG, msg)
            true
        } catch (e: Exception) {
            val msg = "启动失败：$label，${e.message}"
            Log.w(TAG, msg)
            LogRecorder.w(context, TAG, msg)
            false
        }
    }
}
