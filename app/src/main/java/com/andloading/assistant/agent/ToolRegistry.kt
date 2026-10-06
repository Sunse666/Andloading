package com.andloading.assistant.agent

import android.content.Context
import com.andloading.assistant.data.model.ToolCall
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException

interface AssistantTool {
    val name: String
    val description: String
    val parameters: JsonObject
    val dangerous: Boolean get() = false
    val permissionLevel: PermissionLevel
        get() = PermissionLevel.fromDangerous(dangerous)

    val enabled: Boolean get() = true
    val persistableApproval: Boolean get() = true

    suspend fun execute(args: JsonObject): String
}

class ToolRegistry(context: Context?) {

    private val gson = Gson()
    private val tools = LinkedHashMap<String, AssistantTool>()

    private val permissionSystem = context?.let { ToolPermissionSystem(it) }

    fun register(tool: AssistantTool) {
        tools[tool.name] = tool
    }

    fun toolSpecs(): List<ToolSpec> = tools.values.filter { it.enabled }.map {
        ToolSpec(function = FunctionSpec(it.name, it.description, it.parameters))
    }

    fun defaultPermission(name: String): PermissionLevel =
        tools[name]?.permissionLevel ?: PermissionLevel.ALLOW

    fun effectivePermission(name: String): PermissionLevel =
        if (permissionSystem != null) {
            permissionSystem.effectiveLevel(name, defaultPermission(name))
        } else {
            defaultPermission(name)
        }

    fun isForbidden(name: String): Boolean =
        effectivePermission(name) == PermissionLevel.FORBID

    fun setAlwaysAllow(name: String) {
        if (tools[name]?.persistableApproval == false) return
        permissionSystem?.setOverride(name, PermissionLevel.ALLOW)
    }

    fun clearOverride(name: String) {
        permissionSystem?.clearOverride(name)
    }

    suspend fun execute(call: ToolCall): String {
        val tool = tools[call.name] ?: return "错误：未注册的工具 ${call.name}"
        if (!tool.enabled) return "错误：工具 ${call.name} 未启用"
        if (isForbidden(call.name)) return "错误：工具 ${call.name} 已被用户禁止"
        val args = try {
            gson.fromJson(call.argumentsJson, JsonObject::class.java) ?: JsonObject()
        } catch (e: Exception) {
            return "错误：工具参数不是合法 JSON - ${e.message}"
        }
        return try {
            tool.execute(args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "工具 ${call.name} 执行失败: ${e.message}"
        }
    }
}

fun schemaOf(vararg properties: Pair<String, JsonObject>, required: List<String> = emptyList()): JsonObject {
    val schema = JsonObject()
    schema.addProperty("type", "object")
    val props = JsonObject()
    properties.forEach { (k, v) -> props.add(k, v) }
    schema.add("properties", props)
    if (required.isNotEmpty()) {
        val arr = JsonArray()
        required.forEach { arr.add(it) }
        schema.add("required", arr)
    }
    return schema
}

fun stringParam(desc: String, enum: List<String>? = null): JsonObject = JsonObject().apply {
    addProperty("type", "string")
    addProperty("description", desc)
    enum?.let { values ->
        val arr = JsonArray()
        values.forEach { arr.add(it) }
        add("enum", arr)
    }
}

fun intParam(desc: String): JsonObject = JsonObject().apply {
    addProperty("type", "integer")
    addProperty("description", desc)
}
