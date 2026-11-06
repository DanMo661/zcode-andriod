package com.zcodeandriod.app.tools

import kotlinx.serialization.json.JsonObject

/**
 * 工具协议：schema（供给）与执行（放行）共用同一份工具列表，
 * 闭式 allowlist——模型幻觉调用未供给工具时返回 error 文本而非崩溃（学 dsh-go 双重校验）。
 */
interface LocalTool {
    val name: String
    val description: String
    /** JSON Schema 对象字符串 */
    val parametersJson: String

    /** @return 给模型看的执行结果文本 */
    suspend fun execute(args: JsonObject): String
}

/** 结果统一截断：超长工具输出保护上下文预算 */
fun String.truncateForTool(max: Int = 20_000): String =
    if (length <= max) this else take(max) + "\n…[已截断，共 $length 字符]"
