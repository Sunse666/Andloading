package com.andloading.assistant.accessibility

import com.andloading.assistant.agent.AssistantTool
import com.andloading.assistant.agent.intParam
import com.andloading.assistant.agent.schemaOf
import com.andloading.assistant.agent.stringParam
import com.google.gson.JsonObject

private fun executorOrError(): Pair<ActionExecutor?, String?> {
    val service = AssistantAccessibilityService.instance
        ?: return null to "无障碍服务未开启：请在系统设置 → 无障碍中启用「Andloading」，然后重试"
    return ActionExecutor(service) to null
}

class ReadScreenTool : AssistantTool {
    override val name = "read_screen"
    override val description = "读取当前屏幕的可交互元素列表（需无障碍服务）"
    override val parameters = schemaOf()

    override suspend fun execute(args: JsonObject): String {
        val service = AssistantAccessibilityService.instance
            ?: return "无障碍服务未开启：请在系统设置 → 无障碍中启用「Andloading」，然后重试"
        val reader = ScreenReader(service)
        val elements = reader.readTree()
        val pkg = reader.lastReadPackage ?: "未知"
        val header = if (pkg == service.packageName) {
            "前台应用: $pkg（读到的是本应用界面，目标应用可能未在前台）"
        } else {
            "前台应用: $pkg"
        }
        if (elements.isEmpty()) return "$header\n（未读取到可交互元素）"
        val sb = StringBuilder()
        sb.appendLine(header)
        sb.appendLine("交互元素（${elements.size}）:")
        elements.forEach { e ->
            val marks = buildList {
                if (e.clickable) add("可点")
                if (e.editable) add("可编")
            }.joinToString(",")
            sb.appendLine("${e.index}: ${e.className} \"${e.text}\" [${e.bounds.toShortString()}] $marks")
        }
        return sb.toString()
    }
}

class ClickElementTool : AssistantTool {
    override val name = "click_element"
    override val dangerous = true
    override val description = "点击屏幕上指定编号的可交互元素（编号来自 read_screen）"
    override val parameters = schemaOf("index" to intParam("元素编号"), required = listOf("index"))

    override suspend fun execute(args: JsonObject): String {
        val (executor, error) = executorOrError()
        if (executor == null) return error!!
        val index = args.get("index")?.asInt ?: return "缺少 index 参数"
        return executor.clickElement(index)
    }
}

class InputTextTool : AssistantTool {
    override val name = "input_text"
    override val dangerous = true
    override val description = "在指定编号的输入框元素中输入文本（覆盖原有内容）"
    override val parameters = schemaOf(
        "index" to intParam("输入框元素编号"),
        "text" to stringParam("要输入的文本"),
        required = listOf("index", "text"),
    )

    override suspend fun execute(args: JsonObject): String {
        val (executor, error) = executorOrError()
        if (executor == null) return error!!
        val index = args.get("index")?.asInt ?: return "缺少 index 参数"
        val text = args.get("text")?.asString ?: return "缺少 text 参数"
        return executor.inputText(index, text)
    }
}

class ScrollTool : AssistantTool {
    override val name = "scroll"
    override val description = "在屏幕中部执行滚动/滑动"
    override val parameters = schemaOf(
        "direction" to stringParam("滚动方向", listOf("up", "down", "left", "right")),
        required = listOf("direction"),
    )

    override suspend fun execute(args: JsonObject): String {
        val (executor, error) = executorOrError()
        if (executor == null) return error!!
        val direction = args.get("direction")?.asString ?: return "缺少 direction 参数"
        return executor.scroll(direction)
    }
}

class GlobalActionTool : AssistantTool {
    override val name = "global_action"
    override val dangerous = true
    override val description = "执行全局按键：返回(back) 或 回桌面(home)"
    override val parameters = schemaOf(
        "action" to stringParam("按键动作", listOf("back", "home")),
        required = listOf("action"),
    )

    override suspend fun execute(args: JsonObject): String {
        val (executor, error) = executorOrError()
        if (executor == null) return error!!
        return when (args.get("action")?.asString?.lowercase()) {
            "back" -> executor.globalBack()
            "home" -> executor.globalHome()
            else -> "未知 action（支持 back/home）"
        }
    }
}
