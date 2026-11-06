package com.zcodeandriod.app.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SseAssemblerTest {

    private fun assembler() = SseAssembler { events.add(it) }
    private val events = mutableListOf<StreamEvent>()

    @Test
    fun `deltas and done`() {
        val sse = assembler()
        sse.feedLine("""data: {"choices":[{"delta":{"content":"你"}}]}""")
        sse.feedLine("")
        sse.feedLine("""data: {"choices":[{"delta":{"content":"好"},"finish_reason":null}]}""")
        sse.feedLine("""data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":2}}""")
        sse.feedLine("data: [DONE]")

        assertEquals(2, events.filterIsInstance<StreamEvent.Delta>().size)
        assertEquals("你好", events.filterIsInstance<StreamEvent.Delta>().joinToString("") { it.text })
        val done = events.last() as StreamEvent.Done
        assertEquals("stop", done.finishReason)
        assertEquals(5, done.promptTokens)
        assertEquals(2, done.completionTokens)
        assertTrue(sse.isFinished)
    }

    @Test
    fun `eof with finish reason closes`() {
        val sse = assembler()
        sse.feedLine("""data: {"choices":[{"delta":{"content":"x"},"finish_reason":"length"}]}""")
        // 供应商不发 [DONE] 直接断流：有 finish_reason 应收口为 Done
        assertTrue(sse.closeAtEof())
        assertTrue(events.last() is StreamEvent.Done)
    }

    @Test
    fun `eof without finish reason is incomplete`() {
        val sse = assembler()
        sse.feedLine("""data: {"choices":[{"delta":{"content":"half"}}]}""")
        assertFalse(sse.closeAtEof())
        assertFalse(sse.isFinished)
    }

    @Test
    fun `fake streaming whole object`() {
        val sse = assembler()
        sse.emitWhole("整块内容")
        assertEquals("整块内容", (events.single() as StreamEvent.Delta).text)
    }

    @Test
    fun `tool call deltas parsed`() {
        val sse = assembler()
        sse.feedLine("""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call1","function":{"name":"read_file","arguments":""}}]}}]}""")
        sse.feedLine("""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"path\""}}]}}]}""")
        val calls = events.filterIsInstance<StreamEvent.ToolCallDelta>()
        assertEquals(2, calls.size)
        assertEquals("read_file", calls[0].name)
        assertEquals("call1", calls[0].id)
        assertEquals("""{"path"""", calls[1].argsDelta)
    }

    @Test
    fun `non data lines ignored`() {
        val sse = assembler()
        assertFalse(sse.feedLine(": keep-alive"))
        assertFalse(sse.feedLine("event: ping"))
        assertFalse(sse.isFinished)
    }
}
