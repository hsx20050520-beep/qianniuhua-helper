package com.qnh.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务，负责在牵牛花应用内自动点击进入"待领取"页面。
 *
 * 跳转逻辑：
 * 1. 订单页面 → 允许跳转（不管有没有内容）
 * 2. 待拣货页面 + 暂无内容 → 允许跳转
 * 3. 待拣货页面 + 有内容 → 不跳转
 * 4. 其他页面 → 正常跳转流程
 * 5. 深层页面 → 自动返回，最多 3 次
 *
 * 跳转步骤：工作台 → 拣货任务 → 待领取
 */
class QnhAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "QnhAccessibility"

        // 目标 tab：进入"拣货任务"页面后，点击"待领取"这个标签
        private val TARGET_TAB_TEXTS = listOf("待领取", "待领")

        // 待拣货界面：检查是否在此界面
        private val PICKING_NOW_TEXTS = listOf("待拣货")

        // 订单页面：有内容也允许跳转
        private val ORDER_PAGE_TEXTS = listOf("订单", "我的订单", "订单列表")

        // 待拣货页面显示"暂无内容"时，允许跳转到待领取
        private val EMPTY_CONTENT_TEXTS = listOf("暂无内容", "暂无", "没有订单", "无订单", "暂无待拣")

        // 刷新按钮：待领取页面显示"暂无内容"时点击
        private val REFRESH_TEXTS = listOf("刷新试试", "刷新", "重新加载", "点击刷新")

        // 入口按钮：先进入"拣货任务"页面
        private val PICKING_TASK_TEXTS = listOf("拣货任务", "拣货单", "分拣任务", "分拣")

        // 工作台：如果当前页面还在首页，先点底部"工作台"
        private val WORKBENCH_TEXTS = listOf("工作台")

        private const val CLICK_DEBOUNCE_MS = 900L
        // 快速轮询：最多等 1 秒，每 200ms 检测一次，出现目标立即点击
        private const val POLL_INTERVAL_MS = 200L
        private const val POLL_TIMEOUT_MS = 1000L
        // 导航冷却期：点击入口后等待页面加载，期间不执行返回操作
        private const val NAVIGATION_COOLDOWN_MS = 2000L
        // 启动冷却期：pending 标记后的前几秒，给冷启动的应用留出加载时间，不执行返回
        private const val LAUNCH_COOLDOWN_MS = 5000L
        private const val STAGE_PICKING_TASK = "picking_task"
        private const val STAGE_TARGET_TAB = "target_tab"
        private const val STAGE_BACK = "back"

        // 兜底返回：最多返回 3 次
        private const val MAX_BACK_COUNT = 3
        // 主动轮询间隔：页面静止时也能主动检测
        private const val ACTIVE_POLL_INTERVAL_MS = 500L
        // 常驻检测轮询间隔：检测 pending 状态变化
        private const val WATCHDOG_INTERVAL_MS = 1500L
        // 下拉刷新滑动持续时间
        private const val SWIPE_DURATION_MS = 400L
        // 下拉刷新后等待时间
        private const val PULL_REFRESH_WAIT_MS = 1500L

        private const val STAGE_PULL_REFRESH = "pull_refresh"

        @Volatile
        private var running = false

        fun isServiceRunning(): Boolean = running
    }

    private var lastClickAt = 0L
    private var lastNavigationAt = 0L  // 记录上次点击入口的时间
    private val handler: Handler by lazy { Handler(mainLooper) }
    private var pollRunnable: Runnable? = null
    private var pollStage: String? = null
    private var pollUntil = 0L
    private var backCount = 0
    private var activePollRunnable: Runnable? = null
    private var watchdogRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        startWatchdog()
    }

    override fun onDestroy() {
        running = false
        stopPolling()
        stopActivePolling()
        stopWatchdog()
        super.onDestroy()
    }

    private fun startWatchdog() {
        if (watchdogRunnable != null) return
        watchdogRunnable = object : Runnable {
            override fun run() {
                val qnhPending = QnhLauncher.isPickingTaskPending(this@QnhAccessibilityService)
                val houerjiaPending = QnhLauncher.isHouerjiaPending(this@QnhAccessibilityService)
                if (qnhPending || houerjiaPending) {
                    ensureActivePolling()
                } else {
                    stopActivePolling()
                }
                handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
        handler.postDelayed(watchdogRunnable!!, WATCHDOG_INTERVAL_MS)
    }

    private fun stopWatchdog() {
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    private fun ensureActivePolling() {
        if (activePollRunnable != null) return
        // 每次主动轮询启动时重置猴儿家跳转状态，防止上次残留
        houerjiaStep = 0
        houerjiaStep1Retry = 0
        activePollRunnable = object : Runnable {
            override fun run() {
                val qnhPending = QnhLauncher.isPickingTaskPending(this@QnhAccessibilityService)
                val houerjiaPending = QnhLauncher.isHouerjiaPending(this@QnhAccessibilityService)
                if (!qnhPending && !houerjiaPending) {
                    stopActivePolling()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastClickAt >= CLICK_DEBOUNCE_MS) {
                    val root = rootInActiveWindow
                    val pkg = root?.packageName?.toString()
                    if (qnhPending && pkg == QnhLauncher.QNH_PACKAGE) {
                        navigateOnce(now)
                    } else if (houerjiaPending && pkg == QnhLauncher.getHouerjiaPackage(this@QnhAccessibilityService)) {
                        navigateHouerjiaOnce(now)
                    }
                }
                handler.postDelayed(this, ACTIVE_POLL_INTERVAL_MS)
            }
        }
        handler.postDelayed(activePollRunnable!!, ACTIVE_POLL_INTERVAL_MS)
        LogRecorder.d(this, TAG, "主动轮询已启动")
    }

    private fun stopActivePolling() {
        if (activePollRunnable != null) {
            activePollRunnable?.let { handler.removeCallbacks(it) }
            activePollRunnable = null
            LogRecorder.d(this, TAG, "主动轮询已停止")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == QnhLauncher.QNH_PACKAGE) {
            QnhLauncher.markQnhForeground(this)
        }

        // 猴儿家跳转（用主动轮询触发，避免在 onAccessibilityEvent 中清除 pending）
        val houerjiaPkg = QnhLauncher.getHouerjiaPackage(this)
        if (houerjiaPkg != null && pkg == houerjiaPkg && QnhLauncher.isHouerjiaPending(this)) {
            val now = System.currentTimeMillis()
            if (now - lastClickAt >= CLICK_DEBOUNCE_MS) {
                navigateHouerjiaOnce(now)
            }
            return
        }

        if (pkg != QnhLauncher.QNH_PACKAGE) return
        if (!QnhLauncher.isPickingTaskPending(this)) return

        val now = System.currentTimeMillis()
        if (now - lastClickAt < CLICK_DEBOUNCE_MS) return

        navigateOnce(now)
    }

    private fun navigateOnce(now: Long): Boolean {
        val root = rootInActiveWindow ?: return false

        // 一级守卫：检查是否需要停止跳转
        if (!checkPageGuard(root)) return false

        // 1) 优先点"待领取" tab，点到就结束这次自动跳转
        // 二级守卫：点击前再确认一次，确保不在待拣货有内容页面
        if (isOnPickingNowWithContent(root)) {
            val msg = "点击待领取前二次校验：当前在待拣货页面且有内容，不跳转"
            Log.d(TAG, msg)
            LogRecorder.d(this, TAG, msg)
            QnhLauncher.clearPickingTaskPending(this)
            backCount = 0
            return false
        }

        if (isOnTargetTabWithContent(root)) {
            val msg = "已在待领取页面且有任务，执行下拉刷新"
            Log.d(TAG, msg)
            LogRecorder.i(this, TAG, msg)
            performPullToRefresh()
            lastClickAt = now
            lastNavigationAt = now
            startPolling(STAGE_PULL_REFRESH)
            return true
        }

        if (clickByTexts(root, TARGET_TAB_TEXTS)) {
            lastClickAt = now
            val msg = "点击了待领取"
            Log.d(TAG, msg)
            LogRecorder.i(this, TAG, msg)
            stopPolling()
            
            // 如果同时显示"暂无内容"，点击刷新按钮（只点击一次）
            if (hasAnyText(root, EMPTY_CONTENT_TEXTS)) {
                clickByTexts(root, REFRESH_TEXTS)
                val refreshMsg = "待领取页面显示暂无内容，尝试点击刷新"
                Log.d(TAG, refreshMsg)
                LogRecorder.i(this, TAG, refreshMsg)
            }
            
            QnhLauncher.clearPickingTaskPending(this)
            backCount = 0
            return true
        }

        // 2) 否则点"拣货任务"入口。点到后不清空 pending，
        //    让下一次页面事件继续尝试点击"待领取"标签
        if (clickByTexts(root, PICKING_TASK_TEXTS)) {
            lastClickAt = now
            lastNavigationAt = now  // 记录导航开始时间
            val msg = "点击了拣货任务入口，开始快速检测待领取"
            Log.d(TAG, msg)
            LogRecorder.i(this, TAG, msg)
            backCount = 0
            startPolling(STAGE_TARGET_TAB)
            return true
        }

        // 3) 否则点底部"工作台"
        if (clickByTexts(root, WORKBENCH_TEXTS)) {
            lastClickAt = now
            lastNavigationAt = now  // 记录导航开始时间
            val msg = "点击了工作台，开始快速检测拣货任务"
            Log.d(TAG, msg)
            LogRecorder.i(this, TAG, msg)
            backCount = 0
            startPolling(STAGE_PICKING_TASK)
            return true
        }

        // 4) 都找不到，尝试返回（最多 3 次）
        // 但如果刚点击过入口（还在页面加载中），不执行返回
        if (now - lastNavigationAt < NAVIGATION_COOLDOWN_MS) {
            val msg = "刚点击入口，等待页面加载中（${NAVIGATION_COOLDOWN_MS - (now - lastNavigationAt)}ms）"
            Log.d(TAG, msg)
            LogRecorder.d(this, TAG, msg)
            return false
        }
        // 动态冷启动冷却期
        // - pendingAge < 3秒：可能是冷启动，等待5秒
        // - pendingAge >= 3秒：可能是热启动，等待3秒
        val pendingAge = QnhLauncher.getPendingAgeMs(this)
        val launchCooldown = if (pendingAge < 3000) LAUNCH_COOLDOWN_MS else 3000L
        if (pendingAge < launchCooldown) {
            val msg = "等待页面加载（${(launchCooldown - pendingAge).toInt()}ms）"
            Log.d(TAG, msg)
            LogRecorder.d(this, TAG, msg)
            return false
        }
        
        if (backCount < MAX_BACK_COUNT && QnhLauncher.isBackEnabled(this)) {
            backCount++
            val msg = "未找到目标入口，执行返回 ($backCount/$MAX_BACK_COUNT)"
            Log.d(TAG, msg)
            LogRecorder.i(this, TAG, msg)
            performGlobalAction(GLOBAL_ACTION_BACK)
            lastClickAt = now
            startPolling(STAGE_BACK)
            return true
        }

        // 返回次数用完，放弃本次跳转
        val stopMsg = "返回次数已达上限，停止跳转"
        Log.d(TAG, stopMsg)
        LogRecorder.w(this, TAG, stopMsg)
        QnhLauncher.clearPickingTaskPending(this)
        backCount = 0
        return false
    }

    override fun onInterrupt() {
        // 仅做页面跳转，无需处理中断事件
    }

    private fun startPolling(stage: String) {
        stopPolling()
        pollStage = stage
        val timeout = if (stage == STAGE_PULL_REFRESH) PULL_REFRESH_WAIT_MS else POLL_TIMEOUT_MS
        pollUntil = System.currentTimeMillis() + timeout

        pollRunnable = object : Runnable {
            override fun run() {
                if (!QnhLauncher.isPickingTaskPending(this@QnhAccessibilityService)) {
                    stopPolling()
                    return
                }

                val root = rootInActiveWindow
                if (root == null) {
                    scheduleNextPoll(this)
                    return
                }

                val now = System.currentTimeMillis()
                if (checkPageGuard(root)) {
                    when (pollStage) {
                        STAGE_PULL_REFRESH -> {
                            val msg = "下拉刷新完成，结束跳转"
                            Log.d(TAG, msg)
                            LogRecorder.i(this@QnhAccessibilityService, TAG, msg)
                            QnhLauncher.clearPickingTaskPending(this@QnhAccessibilityService)
                            stopPolling()
                            return
                        }
                        STAGE_PICKING_TASK -> {
                            if (clickByTexts(root, PICKING_TASK_TEXTS)) {
                                lastClickAt = now
                                lastNavigationAt = now
                                val msg = "快速检测到拣货任务入口，已点击"
                                Log.d(TAG, msg)
                                LogRecorder.d(this@QnhAccessibilityService, TAG, msg)
                                startPolling(STAGE_TARGET_TAB)
                                return
                            }
                        }
                        STAGE_TARGET_TAB -> {
                            // 轮询中也要二次校验：待拣货有内容就不点击
                            if (isOnPickingNowWithContent(root)) {
                                val msg = "轮询中检测到待拣货有内容，停止跳转"
                                Log.d(TAG, msg)
                                LogRecorder.d(this@QnhAccessibilityService, TAG, msg)
                                QnhLauncher.clearPickingTaskPending(this@QnhAccessibilityService)
                                stopPolling()
                                return
                            }
                            if (clickByTexts(root, TARGET_TAB_TEXTS)) {
                                lastClickAt = now
                                val msg = "快速检测到待领取，已点击"
                                Log.d(TAG, msg)
                                LogRecorder.d(this@QnhAccessibilityService, TAG, msg)
                                stopPolling()
                                QnhLauncher.clearPickingTaskPending(this@QnhAccessibilityService)
                                return
                            }
                        }
                        STAGE_BACK -> {
                            // 返回后检测前也做一次守卫
                            if (isOnPickingNowWithContent(root)) {
                                val msg = "返回后检测到待拣货有内容，停止跳转"
                                Log.d(TAG, msg)
                                LogRecorder.d(this@QnhAccessibilityService, TAG, msg)
                                QnhLauncher.clearPickingTaskPending(this@QnhAccessibilityService)
                                stopPolling()
                                return
                            }
                            // 返回后检测是否有目标入口
                            if (clickByTexts(root, TARGET_TAB_TEXTS)) {
                                lastClickAt = now
                                val msg = "返回后检测到待领取，已点击"
                                Log.d(TAG, msg)
                                LogRecorder.d(this@QnhAccessibilityService, TAG, msg)
                                stopPolling()
                                QnhLauncher.clearPickingTaskPending(this@QnhAccessibilityService)
                                return
                            }
                            if (clickByTexts(root, PICKING_TASK_TEXTS)) {
                                lastClickAt = now
                                lastNavigationAt = now
                                val msg = "返回后检测到拣货任务入口，已点击"
                                Log.d(TAG, msg)
                                LogRecorder.d(this@QnhAccessibilityService, TAG, msg)
                                startPolling(STAGE_TARGET_TAB)
                                return
                            }
                            if (clickByTexts(root, WORKBENCH_TEXTS)) {
                                lastClickAt = now
                                lastNavigationAt = now
                                val msg = "返回后检测到工作台，已点击"
                                Log.d(TAG, msg)
                                LogRecorder.d(this@QnhAccessibilityService, TAG, msg)
                                startPolling(STAGE_PICKING_TASK)
                                return
                            }
                            // 还没找到，继续等待或超时
                        }
                    }
                } else {
                    stopPolling()
                    return
                }

                scheduleNextPoll(this)
            }
        }

        handler.postDelayed(pollRunnable!!, POLL_INTERVAL_MS)
    }

    private fun scheduleNextPoll(runnable: Runnable) {
        if (System.currentTimeMillis() < pollUntil) {
            handler.postDelayed(runnable, POLL_INTERVAL_MS)
        } else {
            val msg = "快速检测超时，等待后续无障碍事件"
            Log.d(TAG, msg)
            LogRecorder.d(this, TAG, msg)
            stopPolling()
        }
    }

    private fun stopPolling() {
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = null
        pollStage = null
        pollUntil = 0L
    }

    private fun checkPageGuard(root: AccessibilityNodeInfo): Boolean {
        // 订单页面：允许继续跳转
        if (hasAnyText(root, ORDER_PAGE_TEXTS)) {
            val msg = "在订单页面，允许跳转"
            Log.d(TAG, msg)
            LogRecorder.d(this, TAG, msg)
            backCount = 0
            return true
        }

        // 待拣货页面判断
        if (hasAnyText(root, PICKING_NOW_TEXTS)) {
            if (isOnPickingNowWithContent(root)) {
                val msg = "待拣货页面有内容，不跳转"
                Log.d(TAG, msg)
                LogRecorder.i(this, TAG, msg)
                QnhLauncher.clearPickingTaskPending(this)
                backCount = 0
                return false
            }
            val msg = "待拣货页面显示暂无内容，允许跳转"
            Log.d(TAG, msg)
            LogRecorder.d(this, TAG, msg)
            backCount = 0
            return true
        }

        return true
    }

    // 判断是否在待拣货页面且有内容
    // 规则：在待拣货页面，同时找不到"暂无内容"和"刷新试试" → 说明有实际内容，不跳转
    private fun isOnPickingNowWithContent(root: AccessibilityNodeInfo): Boolean {
        if (!hasAnyText(root, PICKING_NOW_TEXTS)) return false
        val hasEmptyHint = hasAnyText(root, EMPTY_CONTENT_TEXTS)
        val hasRefreshBtn = hasAnyText(root, REFRESH_TEXTS)
        return !hasEmptyHint && !hasRefreshBtn
    }

    // 判断是否在待领取页面且有任务内容
    // 规则：有"待领取"文字 + 没有"暂无内容"和"刷新" → 认为在待领取页面且有内容
    private fun isOnTargetTabWithContent(root: AccessibilityNodeInfo): Boolean {
        if (!hasAnyText(root, TARGET_TAB_TEXTS)) return false
        if (isOnPickingNowWithContent(root)) return false
        val hasEmptyHint = hasAnyText(root, EMPTY_CONTENT_TEXTS)
        val hasRefreshBtn = hasAnyText(root, REFRESH_TEXTS)
        return !hasEmptyHint && !hasRefreshBtn
    }

    // 执行下拉刷新手势
    private fun performPullToRefresh() {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val startX = screenWidth / 2f
        val startY = screenHeight * 0.2f
        val endY = screenHeight * 0.6f

        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(startX, endY)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS))
        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "下拉刷新手势完成")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d(TAG, "下拉刷新手势取消")
            }
        }, null)
    }

    private fun hasAnyText(root: AccessibilityNodeInfo, texts: List<String>): Boolean {
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isVisibleToUser) {
                    return true
                }
            }
        }
        // 同时检查 contentDescription
        return findNodesByContentDesc(root, texts).isNotEmpty()
    }

    private fun clickByTexts(root: AccessibilityNodeInfo, texts: List<String>): Boolean {
        // 先按 text 查找
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (clickNodeOrParent(node)) return true
            }
        }
        // 再按 contentDescription 查找
        val descNodes = findNodesByContentDesc(root, texts)
        for (node in descNodes) {
            if (clickNodeOrParent(node)) return true
        }
        return false
    }

    // 递归查找匹配 contentDescription 的可见节点
    private fun findNodesByContentDesc(root: AccessibilityNodeInfo?, keywords: List<String>): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        root ?: return result
        fun walk(node: AccessibilityNodeInfo?) {
            node ?: return
            if (node.isVisibleToUser) {
                val desc = node.contentDescription?.toString()
                if (desc != null && keywords.any { desc.contains(it) }) {
                    result.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                walk(node.getChild(i))
            }
        }
        walk(root)
        return result
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isVisibleToUser && current.isEnabled && current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    // ---- 猴儿家跳转 ----

    private var houerjiaStep = 0  // 0=待点击底部导航, 1=待点击顶部打卡
    private var houerjiaStep1Retry = 0  // 第二步重试次数

    private fun navigateHouerjiaOnce(now: Long): Boolean {
        val root = rootInActiveWindow ?: return false

        when (houerjiaStep) {
            0 -> {
                // 第一步：点击底部导航第3个（排班考勤）
                if (clickBottomNavByIndex(root, 2)) {
                    lastClickAt = now
                    lastNavigationAt = now
                    houerjiaStep = 1
                    houerjiaStep1Retry = 0
                    LogRecorder.i(this, TAG, "猴儿家：已点击底部导航，等待页面切换")
                    return false
                }
                return false
            }
            1 -> {
                // 第二步：等页面切换后（500ms），点击顶部"打卡"
                if (now - lastNavigationAt < 500) {
                    return false
                }
                houerjiaStep1Retry++
                LogRecorder.d(this, TAG, "猴儿家：第二步第${houerjiaStep1Retry}次尝试点击打卡")

                if (clickByTexts(root, listOf("打卡"))) {
                    lastClickAt = now
                    val msg = "猴儿家：已点击顶部打卡，跳转完成"
                    Log.d(TAG, msg)
                    LogRecorder.i(this, TAG, msg)
                    QnhLauncher.clearHouerjiaPending(this)
                    houerjiaStep = 0
                    return true
                }
                // 第1次和第5次失败时 dump 页面文本，帮助诊断
                if (houerjiaStep1Retry == 1 || houerjiaStep1Retry == 5) {
                    LogRecorder.d(this, TAG, "猴儿家：页面可见文本：${dumpVisibleTexts(root)}")
                }
                // 超时保护：重试 20 次（约 10 秒）后放弃
                if (houerjiaStep1Retry > 20) {
                    val msg = "猴儿家：第二步超时，未能点击打卡，放弃跳转"
                    Log.w(TAG, msg)
                    LogRecorder.w(this, TAG, msg)
                    LogRecorder.w(this, TAG, "猴儿家：最终页面可见文本：${dumpVisibleTexts(root)}")
                    QnhLauncher.clearHouerjiaPending(this)
                    houerjiaStep = 0
                    return false
                }
                return false
            }
        }
        return false
    }

    private fun isOnHouerjiaAttendancePage(root: AccessibilityNodeInfo): Boolean {
        return hasAnyText(root, listOf("打卡"))
    }

    // 递归遍历节点树，收集所有可见文本
    private fun dumpVisibleTexts(root: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo?) {
            node ?: return
            if (node.isVisibleToUser) {
                node.text?.let { texts.add(it.toString()) }
                node.contentDescription?.let { texts.add("[$it]") }
            }
            for (i in 0 until node.childCount) {
                walk(node.getChild(i))
            }
        }
        walk(root)
        return texts.filter { it.isNotBlank() }.joinToString(" | ")
    }

    // 找到底部导航栏，按索引点击Tab
    private fun clickBottomNavByIndex(root: AccessibilityNodeInfo, index: Int): Boolean {
        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels

        // 先尝试通过节点找底部导航的Tab
        val totalTabs = 4
        val tabIndex = if (index < totalTabs) index else totalTabs - 1

        val allClickable = mutableListOf<AccessibilityNodeInfo>()
        collectBottomClickable(root, allClickable, displayHeight)

        if (allClickable.isNotEmpty()) {
            // 按x排序
            allClickable.sortBy { node ->
                val r = Rect(); node.getBoundsInScreen(r); r.left
            }
            // 只取底部那一行（bottom值最大的一组）
            var maxBottom = 0
            allClickable.forEach { node ->
                val r = Rect(); node.getBoundsInScreen(r)
                if (r.bottom > maxBottom) maxBottom = r.bottom
            }
            val bottomRow = allClickable.filter { node ->
                val r = Rect(); node.getBoundsInScreen(r)
                r.bottom >= maxBottom - 20
            }
            if (bottomRow.isNotEmpty() && tabIndex < bottomRow.size) {
                val target = bottomRow[tabIndex]
                val rect = Rect()
                target.getBoundsInScreen(rect)
                Log.d(TAG, "底部导航节点方式点击第${tabIndex + 1}个: [${rect.left},${rect.top},${rect.right},${rect.bottom}]")
                return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }

        // 节点找不到，用手势点击
        val x = displayWidth * (2 * (tabIndex + 1) - 1).toFloat() / (2 * totalTabs).toFloat()
        val y = displayHeight * 0.93f

        Log.d(TAG, "底部导航手势方式点击第${tabIndex + 1}个Tab: x=${x.toInt()}, y=${y.toInt()}")

        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1f, y + 1f)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 150))
        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    private fun collectBottomClickable(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>,
        displayHeight: Int
    ) {
        if (node.isVisibleToUser && node.isEnabled && node.isClickable) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.top >= displayHeight * 0.8f && rect.height() > 0) {
                result.add(node)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectBottomClickable(child, result, displayHeight)
        }
    }
}