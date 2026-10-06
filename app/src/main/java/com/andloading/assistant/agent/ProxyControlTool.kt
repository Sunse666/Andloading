package com.andloading.assistant.agent

import android.content.Context
import com.andloading.assistant.AssistantApp
import com.andloading.assistant.service.AssistantForegroundService
import com.google.gson.JsonObject

class ProxyControlTool(private val context: Context) : AssistantTool {

    override val name = "proxy_control"
    override val dangerous = true
    override val description =
        "启用或停用 AI 代理。启用后代理自动检查指定应用的消息并模拟用户本人操作回复，" +
            "独立于当前对话运行；可选实时模式。" +
            "需要用户批准；无障碍服务需已开启。"

    override val parameters = schemaOf(
        "enabled" to JsonObject().apply {
            addProperty("type", "boolean")
            addProperty("description", "true=启用代理，false=停用代理")
        },
        "realtime" to JsonObject().apply {
            addProperty("type", "boolean")
            addProperty(
                "description",
                "是否同时开启实时模式。省略时保持现设置",
            )
        },
        "packages" to JsonObject().apply {
            addProperty("type", "array")
            addProperty(
                "description",
                "要盯的应用包名列表，提供时替换现有白名单。" +
                    "启用时白名单不能为空，否则代理不会触发任何任务",
            )
            add("items", JsonObject().apply { addProperty("type", "string") })
        },
    )

    override suspend fun execute(args: JsonObject): String {
        val settings = AssistantApp.instance.settings
        val enabled = args.get("enabled")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: return "错误：缺少 enabled 参数（true=启用，false=停用）"

        val packages = args.get("packages")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { element -> runCatching { element.asString }.getOrNull() }
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
        if (enabled && packages != null && packages.isEmpty()) {
            return "错误：packages 是空数组——白名单为空的代理永远不会触发，请至少提供一个包名"
        }
        packages?.let { settings.proxyWhitelist = it.toSet() }
        args.get("realtime")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.let { settings.proxyRealtimeEnabled = it.asBoolean }

        settings.autoEnabled = enabled
        if (!enabled) return "AI 代理已停用，通知/巡检/实时三条通道全部静默。"

        AssistantForegroundService.start(context)
        val mode =
            if (settings.proxyRealtimeEnabled) {
                "实时模式（前台每 ${settings.proxyRealtimeSeconds} 秒观测，屏幕变化才处理）"
            } else {
                "定时巡检（每 ${settings.proxyIntervalMinutes} 分钟一轮）+ 白名单通知触发"
            }
        return buildString {
            append("AI 代理已启用：")
            append("白名单 ${settings.proxyWhitelist.sorted().joinToString()}，")
            append(mode)
            append("。代理轮独立于本对话运行，执行记录见 设置 → AI 代理 的日志；")
            append("停用可再次调用本工具（enabled=false）或在设置里关闭。")
        }
    }
}
