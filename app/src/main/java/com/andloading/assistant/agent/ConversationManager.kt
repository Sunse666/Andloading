package com.andloading.assistant.agent

import com.andloading.assistant.data.model.Role

class ConversationManager(private val prompts: PromptBuilder) {
    private val _messages = mutableListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages

    fun reset() {
        _messages.clear()
        _messages += ChatMessage.text(Role.SYSTEM, prompts.systemPrompt())
    }

    fun add(message: ChatMessage) { _messages += message }

    fun addUserText(text: String) {
        _messages += ChatMessage.text(Role.USER, text)
    }

    fun addToolResult(toolCallId: String, name: String, result: String) {
        _messages += ChatMessage.text(
            Role.TOOL, result.take(ToolExecutionLimits.TOOL_RESULT_MAX_CHARS),
            toolCallId = toolCallId, name = name,
        )
    }

    fun addToolResultWithImage(toolCallId: String, name: String, result: String, imageDataUrl: String) {
        addToolResult(toolCallId, name, result)
        _messages += ChatMessage.multimodalUser(
            "（这是 take_screenshot 刚截取的屏幕图像，请结合上图与上方工具结果作答）",
            imageDataUrl,
        )
    }

    fun trim(maxMessages: Int = ToolExecutionLimits.AGENT_MAX_CONVERSATION_MESSAGES) {
        if (_messages.size <= maxMessages + 1) return
        val systemMsg = _messages.firstOrNull { it.role == Role.SYSTEM }
        val recent = _messages.takeLast(maxMessages).toMutableList()
        while (recent.isNotEmpty() && recent.first().role == Role.TOOL) {
            val idx = _messages.indexOf(recent.first()) - 1
            if (idx < 0) break
            recent.add(0, _messages[idx])
        }
        _messages.clear()
        if (systemMsg != null) _messages.add(systemMsg)
        _messages.addAll(recent)
    }

    fun isEmpty() = _messages.isEmpty()
    fun size() = _messages.size

    fun estimateTokens(): Int {
        var chars = 0
        for (m in _messages) {
            chars += m.contentText().length
            chars += m.imageCount() * 1280
            m.toolCalls?.forEach { chars += it.function.name.length + it.function.arguments.length }
        }
        return (chars / 1.6).toInt() + 1
    }

    fun excerptForCompress(keepLast: Int): String? {
        val old = _messages.drop(1).dropLast(keepLast.coerceAtMost(_messages.size - 1))
        if (old.isEmpty()) return null
        return old.joinToString("\n") { m ->
            val tc = m.toolCalls?.joinToString(";") { "${it.function.name}(${it.function.arguments})" }
            buildString {
                append("[${m.role}] ")
                append(m.contentText().take(500))
                if (!tc.isNullOrEmpty()) append(" 调用工具: ").append(tc.take(300))
            }
        }
    }

    fun compressWith(summary: String, keepLast: Int) {
        if (_messages.size <= keepLast + 1) return
        val keepCount = keepLast.coerceAtMost(_messages.size - 1)
        val keep = _messages.takeLast(keepCount)
        val system = _messages.first()
        _messages.clear()
        _messages.add(system)
        _messages.add(
            ChatMessage.text(
                "system",
                "[历史摘要] 更早的对话已压缩为以下要点：\n${summary.take(ToolExecutionLimits.TOOL_RESULT_MAX_CHARS)}",
            ),
        )
        _messages.addAll(keep)
    }
}
