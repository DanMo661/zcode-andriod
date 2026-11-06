package com.zcodeandriod.app.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

/**
 * 工作区文件工具：沙箱根 = 应用私有目录 workspace/。
 * canonical path 前缀比对防 `..` 与 symlink 逃逸（学 dsh-go）。
 */
class FileTools(private val root: File) {

    private val rootCanonical: File = root.canonicalFile
    private val rootPathPrefix: String = rootCanonical.path + File.separator

    private fun resolve(path: String): File {
        val clean = path.trim().ifEmpty { "." }
        val f = File(root, clean).canonicalFile
        if (f != rootCanonical && !f.path.startsWith(rootPathPrefix)) {
            throw SecurityException("path escapes workspace: $path")
        }
        return f
    }

    /** 展示用相对路径：优先规范化前缀截取（避开 Windows 8.3 短路径下 relativeTo 的 .. 链） */
    private fun relOf(e: File): String =
        if (e.path.startsWith(rootPathPrefix)) e.path.substring(rootPathPrefix.length) else e.name

    private fun str(args: JsonObject, key: String): String =
        (args[key] as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("missing $key")

    val read = object : LocalTool {
        override val name = "read_file"
        override val description = "读取工作区内文本文件内容"
        override val parametersJson = """{"type":"object","properties":{"path":{"type":"string","description":"相对工作区的路径"}},"required":["path"]}"""
        override suspend fun execute(args: JsonObject): String {
            val f = resolve(str(args, "path"))
            if (!f.exists()) return "error: 文件不存在 ${relOf(f)}"
            if (f.isDirectory) return "error: ${relOf(f)} 是目录，用 list_files"
            val text = f.readText()
            return text.truncateForTool()
        }
    }

    val write = object : LocalTool {
        override val name = "write_file"
        override val description = "在工作区内写入/覆盖文本文件（自动建父目录）"
        override val parametersJson = """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}"""
        override suspend fun execute(args: JsonObject): String {
            val f = resolve(str(args, "path"))
            val content = str(args, "content")
            f.parentFile?.mkdirs()
            f.writeText(content)
            return "ok: 已写入 ${relOf(f)}（${content.toByteArray().size} 字节）"
        }
    }

    val list = object : LocalTool {
        override val name = "list_files"
        override val description = "列出工作区目录内容（默认根目录，最多 200 项）"
        override val parametersJson = """{"type":"object","properties":{"path":{"type":"string","description":"可选，相对路径"}}}"""
        override suspend fun execute(args: JsonObject): String {
            val dir = resolve(args["path"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ".")
            if (!dir.exists()) return "error: 目录不存在"
            if (!dir.isDirectory) return "error: 不是目录"
            val entries = dir.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
                ?: return "error: 无法读取目录"
            if (entries.isEmpty()) return "（空目录）"
            return entries.take(200).joinToString("\n") { e ->
                (if (e.isDirectory) "[目录] " else "") + relOf(e)
            } + if (entries.size > 200) "\n…共 ${entries.size} 项" else ""
        }
    }

    fun all(): List<LocalTool> = listOf(read, write, list)
}
