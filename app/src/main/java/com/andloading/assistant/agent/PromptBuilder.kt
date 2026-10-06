package com.andloading.assistant.agent

import com.andloading.assistant.data.model.MemoryEntity

class PromptBuilder {

    var personaProvider: () -> String? = { null }

    @Volatile
    var memoriesCache: List<MemoryEntity> = emptyList()

    fun systemPrompt(): String = buildString {
        append(BASE_PROMPT)

        personaProvider()?.let { persona ->
            append("\n\n【人设】\n")
            append(persona.trim().take(PERSONA_MAX_CHARS))
        }

        val memories = memoriesCache.take(MEMORY_INDEX_LIMIT)
        if (memories.isNotEmpty()) {
            append(
                "\n\n【操作记忆索引】以下是历史任务沉淀的经验标题（方括号内是记忆编号）。" +
                    "与当前任务相关时，先调用 read_memory 工具按编号读取完整内容" +
                    "（含应用包名、Activity、深链、具体点击步骤等细节），再照做：\n",
            )
            memories.forEach { m ->
                val title = m.title.ifBlank { m.content.take(TITLE_FALLBACK_CHARS) }
                append("[${m.id}] ${title.take(MEMORY_TITLE_MAX_CHARS)}\n")
            }
        }
    }

    companion object {
        private val BASE_PROMPT =
            "你是运行在 Android 手机上的智能助手。用简体中文回复，" +
                "回复使用 Markdown 格式（代码块标注语言，列表和标题按需使用）。" +
                "简洁直接，先给结论再给细节。\n\n" +
                "设备操作规范：\n" +
                "1. 同一动作失败后不要原样重试；先 read_screen 看清界面再决定下一步。\n" +
                "2. 启动 App 后先 read_screen 确认真的到了目标界面，再继续操作。\n" +
                "3. App 内搜索的优先路径：launch_app → read_screen 找搜索框 → click_element 点它 → " +
                "input_text 输入关键词 → 点搜索按钮。深链 scheme 只是备选，失败就回到点击路径。\n" +
                "4. global_action(home/back) 是逃手段，不要在任务中途随意按。\n" +
                "5. 步数有限：每一步都要离目标更近，不要重复已完成的事。"

        const val MEMORY_INDEX_LIMIT = 50
        const val MEMORY_TITLE_MAX_CHARS = 40
        const val TITLE_FALLBACK_CHARS = 20
        const val PERSONA_MAX_CHARS = 2000
    }
}
