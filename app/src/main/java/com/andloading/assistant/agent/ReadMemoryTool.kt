package com.andloading.assistant.agent

import com.andloading.assistant.data.db.AppDatabase
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class ReadMemoryTool(database: AppDatabase) : AssistantTool {

    private val dao = database.memoryDao()

    override val name = "read_memory"

    override val description =
        "按编号读取操作记忆的完整内容（含应用包名、Activity、深链 scheme、具体操作步骤等细节）。" +
            "当「操作记忆索引」中某条标题与当前任务相关时调用；一次可传 1~10 个编号。"

    override val parameters: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        val props = JsonObject()
        props.add(
            "ids",
            JsonObject().apply {
                addProperty("type", "array")
                addProperty("description", "要读取的记忆编号列表（索引中方括号内的数字），1~10 个")
                add("items", JsonObject().apply { addProperty("type", "integer") })
            },
        )
        add("properties", props)
        add("required", JsonArray().apply { add("ids") })
    }

    override suspend fun execute(args: JsonObject): String {
        val ids = args.getAsJsonArray("ids")
            ?.mapNotNull { runCatching { it.asLong }.getOrNull() }
            ?.distinct()
            ?.take(MAX_IDS)
            .orEmpty()
        if (ids.isEmpty()) return "错误：ids 必须是非空的编号数组"
        val found = dao.getByIds(ids)
        if (found.isEmpty()) return "没有找到编号 ${ids.joinToString()} 对应的记忆"
        val body = found.joinToString("\n\n") { m ->
            val title = m.title.ifBlank { "（无标题）" }
            "[${m.id}]【$title】\n${m.content}"
        }
        val missing = ids - found.map { it.id }.toSet()
        val missingNote = if (missing.isEmpty()) "" else "\n\n（编号 ${missing.joinToString()} 未找到）"
        return body + missingNote
    }

    companion object {
        private const val MAX_IDS = 10
    }
}
