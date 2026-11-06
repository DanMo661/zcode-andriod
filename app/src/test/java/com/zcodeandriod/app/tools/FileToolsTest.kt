package com.zcodeandriod.app.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileToolsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json

    private fun obj(vararg pairs: Pair<String, String>): JsonObject =
        buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }

    @Test
    fun `write then read roundtrip`() = runTest {
        val tools = FileTools(tmp.newFolder())
        val w = tools.write.execute(obj("path" to "src/main.kt", "content" to "fun a() = 1"))
        assertTrue(w.startsWith("ok"))
        val r = tools.read.execute(obj("path" to "src/main.kt"))
        assertEquals("fun a() = 1", r)
    }

    @Test
    fun `path escape rejected`() = runTest {
        val tools = FileTools(tmp.newFolder())
        try {
            tools.read.execute(obj("path" to "../../etc/passwd"))
            fail("expected SecurityException")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("escapes"))
        }
        try {
            tools.write.execute(obj("path" to "../evil.txt", "content" to "x"))
            fail("expected SecurityException")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("escapes"))
        }
    }

    @Test
    fun `list files marks directories`() = runTest {
        val tools = FileTools(tmp.newFolder())
        tools.write.execute(obj("path" to "d/inner.txt", "content" to "x"))
        val listing = tools.list.execute(obj())
        assertTrue("listing=[$listing]", listing.contains("[目录] d"))
        // 列子目录应看到文件（不带 [目录] 前缀）
        val sub = tools.list.execute(obj("path" to "d"))
        assertTrue("sub=[$sub]", sub.contains("inner.txt"))
        assertTrue(!sub.contains("[目录]"))
    }

    @Test
    fun `read missing file errors gracefully`() = runTest {
        val tools = FileTools(tmp.newFolder())
        val r = tools.read.execute(obj("path" to "nope.txt"))
        assertTrue(r.startsWith("error"))
    }
}
