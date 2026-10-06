package com.andloading.assistant.agent

object ToolExecutionLimits {
    const val AGENT_MAX_STEPS = 14
    const val AGENT_MAX_CONVERSATION_MESSAGES = 40
    const val TOOL_RESULT_MAX_CHARS = 4000
    const val TOKEN_BUDGET = 16000
    const val COMPRESS_KEEP_LAST = 8
}
