package com.example.aiagg

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.concurrent.ConcurrentHashMap

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyA11yService"
        private const val DEBOUNCE_IDLE_MS = 1500L
        private const val DEBOUNCE_TICK_MS = 200L
        private const val MAX_REPLY_CHARS = 8000

        // 目标应用包名列表，用于过滤无障碍事件，降低噪声
        private val TARGET_PKGS = setOf(
            "com.larus.wormhole",
            "com.aliyun.tongyi",
            "com.deepseek.chat",
            "com.zhipu.qlib",
            "com.openai.chatgpt"
        )

        // 发送按钮的常见文本
        private val sendKeyTexts = listOf("发送", "Send", "提问")
        // 输入框的常见提示词
        private val inputHints = listOf("输入", "提问", "message", "input", "chat", "send")
        // 回复区的常见定位词
        private val replyHints = listOf("回答", "回复", "结果", "response", "answer")

        // 会话状态
        private data class Session(
            var active: Boolean = false,
            var phase: Phase = Phase.IDLE,
            var textToSend: String = "",
            var targetPkg: String = "",
            var lastChangeAt: Long = 0L,
            var sendClicked: Boolean = false
        )

        private enum class Phase { IDLE, WAIT_INPUT, WAIT_REPLY, DONE }

        // 全局只维护一个进行中会话（同一时间只对一个目标 App 做自动化）
        private val activeSession = Session()

        // 供 MainActivity 调用的查询接口
        @JvmStatic
        fun isServiceActive(): Boolean {
            return activeSession.active
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    private val sendReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = intent.getStringExtra(MainActivity.EXTRA_TEXT).orEmpty()
            Log.d(TAG, "收到本地广播：长度=${text.length}")
            if (text.isEmpty()) return

            resetSession(text)
            activeSession.active = true
            activeSession.phase = Phase.WAIT_INPUT
            activeSession.lastChangeAt = SystemClock.elapsedRealtime()
            activeSession.sendClicked = false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(sendReceiver, IntentFilter(MainActivity.ACTION_SEND_TO_AI))
        Log.d(TAG, "MyAccessibilityService 已连接")
    }

    override fun onInterrupt() { }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // 非目标应用直接忽略，减少无意义开销
        if (pkg !in TARGET_PKGS) return
        val s = activeSession
        if (!s.active) return

        // 第一次进入目标窗口时，绑定包名
        if (s.phase == Phase.WAIT_INPUT && s.targetPkg.isBlank()) {
            s.targetPkg = pkg
            Log.d(TAG, "绑定目标包：$pkg")
        }

        // 会话已绑定且事件来自非目标包，忽略
        if (s.targetPkg.isNotBlank() && s.targetPkg != pkg) return


        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "窗口切换：$pkg, phase=${s.phase}")
                if (s.phase == Phase.WAIT_INPUT) {
                    mainHandler.postDelayed({ tryFillAndSend(s) }, 400)
                } else if (s.phase == Phase.WAIT_REPLY) {
                    s.lastChangeAt = SystemClock.elapsedRealtime()
                    startDebounce(s)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                when (s.phase) {
                    Phase.WAIT_INPUT -> {
                        // 界面刚加载完时可能触发，直接尝试填入
                        mainHandler.postDelayed({ tryFillAndSend(s) }, 300)
                    }
                    Phase.WAIT_REPLY -> {
                        // 流式输出中，持续重置防抖计时
                        s.lastChangeAt = SystemClock.elapsedRealtime()
                        ensureDebounceRunning(s)
                    }
                    else -> { }
                }
            }
        }
    }

    /** 核心：定位输入框 → 填入 → 点击发送 */
    private fun tryFillAndSend(s: Session) {
        if (!s.active) return
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "根节点为空，稍后重试")
            mainHandler.postDelayed({ tryFillAndSend(s) }, 300)
            return
        }

        if (root.packageName.toString() != s.targetPkg) {
            // 窗口已切走，等待下一次事件
            return
        }

        val input = findInputNode(root)
        if (input == null) {
            Log.w(TAG, "未找到输入框，300ms 后重试")
            mainHandler.postDelayed({ tryFillAndSend(s) }, 300)
            return
        }

        // 先聚焦
        runCatching { input.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        // 填入文本
        runCatching {
            val bundle = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    s.textToSend
                )
            }
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        }.onFailure {
            Log.e(TAG, "ACTION_SET_TEXT 失败", it)
        }

        mainHandler.postDelayed({
            clickSend(s, s.targetPkg)
        }, 300)
    }

    /** 点击发送按钮；找不到则用回车兜底 */
    private fun clickSend(s: Session, targetPkg: String) {
        if (!s.active) return
        val root = rootInActiveWindow
        if (root == null || root.packageName.toString() != targetPkg) {
            // 窗口变了，放弃这次自动化
            s.active = false
            s.phase = Phase.DONE
            dispatchError("发送前目标应用窗口已变化")
            return
        }

        val btn = findSendButton(root)
        val clicked = if (btn != null) {
            runCatching { btn.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.isSuccess
        } else {
            // 回车兜底
            Log.d(TAG, "未找到发送按钮，尝试 IME_ENTER")
            runCatching {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)
            }.isSuccess
        }

        if (clicked) {
            Log.d(TAG, "发送动作成功")
            s.sendClicked = true
            s.phase = Phase.WAIT_REPLY
            s.lastChangeAt = SystemClock.elapsedRealtime()
            startDebounce(s)
        } else {
            s.active = false
            s.phase = Phase.DONE
            dispatchError("无法点击发送按钮")
        }
    }

    /** 防抖：每隔 200ms 检查一次，若距上次内容变化已 ≥1500ms 则认为流式输出结束 */
    private fun startDebounce(s: Session) {
        ensureDebounceRunning(s)
    }

    private fun ensureDebounceRunning(s: Session) {
        if (debounceRunnable != null) return
        val task = object : Runnable {
            override fun run() {
                if (!s.active || s.phase != Phase.WAIT_REPLY) {
                    debounceRunnable = null
                    return
                }
                val idle = SystemClock.elapsedRealtime() - s.lastChangeAt
                if (idle >= DEBOUNCE_IDLE_MS) {
                    // 输出稳定，抓取最终文本
                    val reply = captureLatestReply()
                    s.phase = Phase.DONE
                    s.active = false
                    debounceRunnable = null
                    dispatchResult(reply ?: "未抓取到回复")
                    return
                }
                mainHandler.postDelayed(this, DEBOUNCE_TICK_MS)
            }
        }
        debounceRunnable = task
        mainHandler.postDelayed(task, DEBOUNCE_TICK_MS)
    }

    /** 抓取 AI 回复：优先找带回复提示词且文本较长的节点，兜底取当前窗口最长非输入文本 */
    private fun captureLatestReply(): String? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName.toString() !in TARGET_PKGS) return null

        // 1) 按回复提示词找容器
        for (kw in replyHints) {
            val byText = root.findAccessibilityNodeInfosByText(kw)
            val hit = byText?.firstNotNullOfOrNull { node ->
                collectLongText(node)?.let { if (it.length >= 20) it else null }
            }
            if (hit != null) return hit.take(MAX_REPLY_CHARS)
        }
        // 2) 兜底：收集所有较长文本，排除用户输入本身和发送按钮文本
        val userText = activeSession.textToSend
        val all = mutableListOf<AccessibilityNodeInfo>()
        collectTextNodes(root, all)
        val best = all
            .map { it.text.toString() }
            .filter {
                it.length in 20..MAX_REPLY_CHARS &&
                    !it.equals(userText, ignoreCase = true) &&
                    sendKeyTexts.none { k -> it == k }
            }
            .maxByOrNull { it.length }
        return best

    }

    /** 定位输入框：先按提示词找 EditText，再按类名兜底 */
    private fun findInputNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (kw in inputHints) {
            val found = root.findAccessibilityNodeInfosByText(kw)
            val et = found?.firstOrNull {
                it.className.toString().endsWith("EditText") && it.isEditable
            }
            if (et != null) return et
        }
        val byId = root.findAccessibilityNodeInfosByViewId("android.widget.EditText")
        if (!byId.isNullOrEmpty()) return byId.first()

        // 最后递归找第一个可编辑的 EditText
        val all = root.findAccessibilityNodeInfosByClass("android.widget.EditText")
        return all?.firstOrNull { it.isEditable }
    }

    /** 定位发送按钮：按文本找 Button / 可点击 ImageView / 带 contentDescription 的控件 */
    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (kw in sendKeyTexts) {
            val found = root.findAccessibilityNodeInfosByText(kw)
            val btn = found?.firstOrNull {
                val cls = it.className.toString()
                it.isClickable &&
                    (cls.endsWith("Button") || cls.endsWith("ImageView") ||
                        (it.contentDescription?.contains(kw, ignoreCase = true) == true))
            }
            if (btn != null) return btn
        }

        // 按常见 ID 兜底
        for (id in listOf(
            "com.larus.wormhole:id/btn_send",
            "com.aliyun.tongyi:id/btn_send",
            "com.openai.chatgpt:id/btn_send",
            "android:id/button1"
        )) {
            val byId = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull()
            if (!byId.isNullOrEmpty()) return byId.first()
        }
        return null
    }

    /** 收集某节点下所有含文本的子节点（含自身） */
    private fun collectTextNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        runCatching {
            if (node.text.isNotBlank()) out.add(node)
            for (i in 0 until node.childCount) {
                node.getChildAt(i)?.let { collectTextNodes(it, out) }
            }
        }
    }

    /** 从一个容器节点向下找最长的一段文本 */
    private fun collectLongText(node: AccessibilityNodeInfo): String? {
        val texts = mutableListOf<AccessibilityNodeInfo>()
        collectTextNodes(node, texts)
        return texts
            .map { it.text.toString() }
            .filter { it.length >= 20 }
            .maxByOrNull { it.length }
    }

    private fun resetSession(text: String) {
        activeSession.active = false
        activeSession.phase = Phase.IDLE
        activeSession.textToSend = text
        activeSession.targetPkg = ""
        activeSession.lastChangeAt = 0L
        activeSession.sendClicked = false
        debounceRunnable?.let { mainHandler.removeCallbacks(it) }
        debounceRunnable = null
    }

    private fun dispatchResult(text: String) {
        Log.d(TAG, "回传结果，长度=${text.length}")
        val intent = Intent(MainActivity.ACTION_AI_RESULT)
            .putExtra(MainActivity.EXTRA_RESULT, text)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun dispatchError(msg: String) {
        Log.w(TAG, "错误：$msg")
        val intent = Intent(MainActivity.ACTION_AI_RESULT)
            .putExtra(MainActivity.EXTRA_ERROR, msg)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(sendReceiver)
        super.onDestroy()
    }
}
