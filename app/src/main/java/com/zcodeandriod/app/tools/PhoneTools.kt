package com.zcodeandriod.app.tools

import com.zcodeandriod.app.agent.ZcodeAAccessibilityService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * AI 控制手机的语义工具：观察、动作、截屏（视觉兜底）与坐标手势。
 * 动作后必须重新 observe——这是模型闭环自校验的核心约定。
 * 安全边界：截屏与手势受 PhonePolicy 拦截；截屏帧只驻内存不落盘。
 */
class PhoneTools(
    private val service: () -> ZcodeAAccessibilityService?,
) {

    val observe = object : LocalTool {
        override val name = "phone_observe"
        override val description =
            "观察手机当前屏幕：返回前台应用包名与可交互元素列表（id、文本、边界、可点击/可编辑/可滚动）。" +
                "每次 phone_action 之后必须重新 observe 以确认结果。"
        override val parametersJson = """{"type":"object","properties":{}}"""

        override suspend fun execute(args: JsonObject): String {
            val svc = service()
                ?: return "error: 无障碍服务未连接，请引导用户在应用「设备」页开启"
            return svc.collectJson()
        }
    }

    val action = object : LocalTool {
        override val name = "phone_action"
        override val description =
            "对手机执行动作。action 可选 click/long_press/set_text/scroll_forward/scroll_backward/focus（需目标 id 或 text）" +
                "以及 back/home（全局，无需目标）。set_text 需 value。禁止用于支付/银行界面。"
        override val parametersJson =
            """{"type":"object","properties":{""" +
                """"action":{"type":"string","enum":["click","long_press","set_text","scroll_forward","scroll_backward","focus","back","home"]},""" +
                """"id":{"type":"integer","description":"phone_observe 返回的节点 id"},""" +
                """"text":{"type":"string","description":"按精确文本匹配节点"},""" +
                """"value":{"type":"string","description":"set_text 的内容"}},""" +
                """"required":["action"]}"""

        override suspend fun execute(args: JsonObject): String {
            val svc = service()
                ?: return "error: 无障碍服务未连接，请引导用户在应用「设备」页开启"
            val act = (args["action"] as? JsonPrimitive)?.contentOrNull
                ?: return "error: missing action"
            if (act == "set_text" && args["value"] == null) {
                return "error: set_text 需要 value"
            }
            val ok = svc.performAction(
                action = act,
                nodeId = (args["id"] as? JsonPrimitive)?.intOrNull,
                matchText = (args["text"] as? JsonPrimitive)?.contentOrNull,
                value = (args["value"] as? JsonPrimitive)?.contentOrNull,
            )
            return if (ok.startsWith("ok")) "$ok（务必 phone_observe 确认实际效果）" else ok
        }
    }

    val capture = object : LocalTool {
        override val name = "phone_capture"
        override val description =
            "截取当前手机屏幕给 AI 看（视觉兜底：无障碍树信息不足时用）。" +
                "截图只在本轮有效、不落盘。需要用户先在「设备」页完成系统截屏授权。"
        override val parametersJson = """{"type":"object","properties":{}}"""

        override suspend fun execute(args: JsonObject): String {
            if (!com.zcodeandriod.app.agent.ScreenCaptureService.ready()) {
                return "error: 截屏未授权，请引导用户到「设备」页开启屏幕截取"
            }
            val frame = com.zcodeandriod.app.agent.ScreenCaptureService.grabFrame()
                ?: return "error: 未取到帧（屏幕可能已关闭），稍后重试或改用 phone_observe"
            return "ok: 已截屏，截图将在本轮消息中一并提供"
        }
    }

    val gesture = object : LocalTool {
        override val name = "phone_gesture"
        override val description =
            "在屏幕上执行坐标手势（tap 或 swipe）。坐标一律来自 phone_observe 返回的边界/屏幕尺寸，" +
                "禁止凭空猜坐标。 swipe 需 x2,y2；durationMs 120..5000（默认 300）。"
        override val parametersJson =
            """{"type":"object","properties":{""" +
                """"kind":{"type":"string","enum":["tap","swipe"]},""" +
                """"x1":{"type":"number"},"y1":{"type":"number"},""" +
                """"x2":{"type":"number"},"y2":{"type":"number"},""" +
                """"durationMs":{"type":"integer"}},""" +
                """"required":["kind","x1","y1"]}"""

        override suspend fun execute(args: JsonObject): String {
            val svc = service()
                ?: return "error: 无障碍服务未连接"
            fun num(key: String): Float? = (args[key] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull()
            val kind = (args["kind"] as? JsonPrimitive)?.contentOrNull ?: return "error: missing kind"
            val x1 = num("x1") ?: return "error: missing x1"
            val y1 = num("y1") ?: return "error: missing y1"
            val (x2, y2, dur) = when (kind) {
                "tap" -> Triple(x1, y1, 120L)
                "swipe" -> Triple(
                    num("x2") ?: return "error: swipe 需要 x2",
                    num("y2") ?: return "error: swipe 需要 y2",
                    ((args["durationMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 300L),
                )
                else -> return "error: 未知 kind $kind"
            }
            return svc.gestureSwipe(x1, y1, x2, y2, dur)
        }
    }

    fun all(): List<LocalTool> = listOf(observe, action, capture, gesture)
}
