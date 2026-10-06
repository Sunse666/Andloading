package com.andloading.assistant.agent

import android.content.Context
import android.content.SharedPreferences

class ToolPermissionSystem(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tool_permissions", Context.MODE_PRIVATE)

    fun effectiveLevel(toolName: String, defaultLevel: PermissionLevel): PermissionLevel {
        val stored = prefs.getString(toolName, null)
        return stored?.let { runCatching { PermissionLevel.valueOf(it) }.getOrNull() }
            ?: defaultLevel
    }

    fun setOverride(toolName: String, level: PermissionLevel) {
        prefs.edit().putString(toolName, level.name).apply()
    }

    fun clearOverride(toolName: String) {
        prefs.edit().remove(toolName).apply()
    }
}
