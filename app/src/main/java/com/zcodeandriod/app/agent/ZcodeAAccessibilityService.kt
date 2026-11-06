package com.zcodeandriod.app.agent

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject

/**
 * 设备控制无障碍服务（骨架学 dsh-go PhoneControlService）：
 * - 是否开启以 Settings.Secure 为准，不信任内存单例（系统随时可能重绑）
 * - 事件不驱动，工具调用时按需拉取树
 * - 动作前校验快照，避免点错陈旧目标
 */
class ZcodeAAccessibilityService : AccessibilityService() {

    @Volatile
    var latestScreen: PhoneScreen? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        epoch++
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        epoch++
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // ---------- 观察 ----------

    /** 在主线程收集当前窗口节点树（BFS ≤300 节点），返回给模型看的紧凑 JSON */
    suspend fun collectJson(): String = withContext(Dispatchers.Main.immediate) {
        val root = rootInActiveWindow ?: run {
            latestScreen = null
            return@withContext JSONObject().apply {
                put("error", "no_accessible_window")
            }.toString()
        }
        val pkg = root.packageName?.toString()
        PhonePolicy.observeRefusal(pkg)?.let { refusal ->
            latestScreen = null
            return@withContext JSONObject().apply { put("error", refusal) }.toString()
        }

        val metrics = resources.displayMetrics
        val nodes = mutableListOf<PhoneNode>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && nodes.size < MAX_NODES) {
            val n = queue.removeFirst()
            val label = n.text?.toString()?.take(60)
                ?: n.contentDescription?.toString()?.take(60)
                ?: n.viewIdResourceName?.substringAfterLast('/')?.take(40)
                ?: ""
            val editable = n.isEditable
            val clickable = n.isClickable
            if (label.isNotEmpty() || clickable || editable || n.isScrollable) {
                val b = android.graphics.Rect().also { n.getBoundsInScreen(it) }
                nodes.add(
                    PhoneNode(
                        id = nodes.size,
                        label = PhonePolicy.redact(label),
                        className = n.className?.toString()?.substringAfterLast('.'),
                        boundsInScreen = intArrayOf(b.left, b.top, b.right, b.bottom),
                        clickable = clickable,
                        editable = editable,
                        scrollable = n.isScrollable,
                        nodeRef = n,
                    ),
                )
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { if (queue.size < MAX_NODES) queue.add(it) }
            }
        }
        val screen = PhoneScreen(pkg, metrics.widthPixels, metrics.heightPixels, nodes.toList())
        latestScreen = screen
        screen.toJson().toString()
    }

    // ---------- 动作 ----------

    suspend fun performAction(
        action: String,
        nodeId: Int?,
        matchText: String?,
        value: String?,
    ): String = withContext(Dispatchers.Main.immediate) {
        // 全局动作无需目标
        when (action) {
            "back" -> return@withContext boolResult(performGlobalAction(GLOBAL_ACTION_BACK), "back")
            "home" -> return@withContext boolResult(performGlobalAction(GLOBAL_ACTION_HOME), "home")
        }

        val screen = latestScreen
            ?: return@withContext "error: 没有可用快照，先 phone_observe"
        val node: PhoneNode = when {
            nodeId != null -> screen.nodes.getOrNull(nodeId)
                ?: return@withContext "error: id $nodeId 不在快照内，重新 observe"
            matchText != null -> screen.nodes.firstOrNull { it.label == matchText }
                ?: return@withContext "error: 快照内没有文本 \"${matchText.take(20)}\"，重新 observe"
            else -> return@withContext "error: 缺少目标（id 或 text）"
        }
        if (PhonePolicy.isProtectedPackage(screen.packageName)) {
            return@withContext "error: 受保护窗口 ${screen.packageName} 拒绝操作"
        }
        val labelForPolicy = node.label
        if (action in setOf("click", "long_press") && PhonePolicy.isPaymentContext(screen.packageName, labelForPolicy)) {
            return@withContext "error: 支付/银行上下文拒绝自动点击，请用户手动完成"
        }
        val target = node.nodeRef
        // 陈旧节点刷新一次再动作
        target.refresh()
        val ok = when (action) {
            "click" -> target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            "long_press" -> target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            "scroll_forward" -> target.isScrollable && target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            "scroll_backward" -> target.isScrollable && target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "set_text" -> {
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value.orEmpty())
                }
                target.isEditable && target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            "focus" -> target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            else -> return@withContext "error: 未知动作 $action（可用：click/long_press/set_text/scroll_forward/scroll_backward/focus/back/home）"
        }
        if (!ok) {
            return@withContext "error: 动作被 Android 拒绝或目标不支持（$action），重新 observe 后换路径"
        }
        "ok: $action 已执行（观察最新状态请再 phone_observe）"
    }

    private fun boolResult(ok: Boolean, what: String): String =
        if (ok) "ok: $what 已执行" else "error: $what 被系统拒绝"

    /** 坐标手势（tap/swipe）：dispatchGesture 结果包成可取消协程，超时=时长+2s（学 dsh-go） */
    suspend fun gestureSwipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long,
    ): String = withContext(Dispatchers.Main.immediate) {
        val screen = latestScreen
            ?: return@withContext "error: 没有可用快照，先 phone_observe"
        if (PhonePolicy.isProtectedPackage(screen.packageName) ||
            PhonePolicy.isPaymentContext(screen.packageName, "")
        ) {
            return@withContext "error: 当前窗口受保护，拒绝手势"
        }
        val m = resources.displayMetrics
        fun inside(x: Float, y: Float) = x in 0f..m.widthPixels.toFloat() && y in 0f..m.heightPixels.toFloat()
        if (!inside(x1, y1) || !inside(x2, y2)) {
            return@withContext "error: 坐标越界（屏幕 ${m.widthPixels}x${m.heightPixels}）"
        }
        val path = android.graphics.Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(
            path,
            0,
            durationMs.coerceIn(120, 5000),
        )
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        val dispatched = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val okNow = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(g: android.accessibilityservice.GestureDescription?) {
                        cont.resume(true)
                    }

                    override fun onCancelled(g: android.accessibilityservice.GestureDescription?) {
                        cont.resume(false)
                    }
                },
                null,
            )
            if (!okNow) cont.resume(false)
        }
        if (dispatched) "ok: 手势已派发（务必 phone_observe 确认效果）"
        else "error: 手势被系统拒绝"
    }

    private fun PhoneScreen.toJson(): JSONObject = JSONObject().apply {
        put("package", packageName ?: "")
        put("screen", JSONArray().put(width).put(height))
        put("nodes", JSONArray().also { arr ->
            nodes.forEach { n ->
                arr.put(JSONObject().apply {
                    put("i", n.id)
                    put("t", n.label)
                    n.className?.let { put("cls", it) }
                    put("b", JSONArray().put(n.boundsInScreen[0]).put(n.boundsInScreen[1]).put(n.boundsInScreen[2]).put(n.boundsInScreen[3]))
                    val flags = buildList {
                        if (n.clickable) add("clickable")
                        if (n.editable) add("editable")
                        if (n.scrollable) add("scrollable")
                    }
                    if (flags.isNotEmpty()) put("f", flags.joinToString(","))
                })
            }
        })
    }

    companion object {
        const val MAX_NODES = 300

        @Volatile
        var instance: ZcodeAAccessibilityService? = null
            private set

        /** 连接代际：重绑后自增 */
        @Volatile
        var epoch: Int = 0
            private set

        /** 以系统设置为唯一事实源（服务可能被系统重绑而内存态过期） */
        fun isEnabled(context: android.content.Context): Boolean {
            val self = ComponentName(context, ZcodeAAccessibilityService::class.java)
            val setting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = android.text.TextUtils.SimpleStringSplitter(':')
            splitter.setString(setting)
            while (splitter.hasNext()) {
                if (ComponentName.unflattenFromString(splitter.next()) == self) return true
            }
            return false
        }
    }
}
