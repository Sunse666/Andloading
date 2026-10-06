package com.andloading.assistant.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.andloading.assistant.agent.AssistantTool
import com.andloading.assistant.agent.schemaOf
import com.andloading.assistant.agent.stringParam
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun buildUri(raw: String): Uri {
    val parsed = Uri.parse(raw)
    val query = parsed.query ?: return parsed
    val reEncoded = query.split('&').joinToString("&") { pair ->
        if ('=' in pair) {
            Uri.encode(pair.substringBefore('=')) + "=" + Uri.encode(pair.substringAfter('='))
        } else {
            Uri.encode(pair)
        }
    }
    return parsed.buildUpon().encodedQuery(reEncoded).build()
}

class LaunchAppTool(private val context: Context) : AssistantTool {
    override val name = "launch_app"
    override val description = "按包名或应用名启动一个应用"
    override val parameters = schemaOf(
        "package_name" to stringParam("应用包名或提供应用名 app_name 自动解析"),
        "app_name" to stringParam("应用名称，未提供 package_name 时按名称模糊查找"),
    )

    override suspend fun execute(args: JsonObject): String = withContext(Dispatchers.IO) {
        val pkg = runCatching { args.get("package_name")?.asString }.getOrNull()
            ?: runCatching { args.get("app_package")?.asString }.getOrNull()
            ?: runCatching { args.get("app_name")?.asString }.getOrNull()?.let { name ->
                resolvePackageByName(name)
            }
            ?: return@withContext "缺少 package_name/app_name 参数"
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return@withContext "未找到应用 $pkg"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) {
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                return@withContext "启动失败: ${e.message}"
            }
        }
        delay(800)
        "已启动 $pkg"
    }

    private fun resolvePackageByName(name: String): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
        val exact = apps.firstOrNull { it.second == name }
        if (exact != null) return exact.first
        return apps.firstOrNull { it.second.contains(name) || name.contains(it.second) }?.first
    }
}

class ListAppsTool(private val context: Context) : AssistantTool {
    override val name = "list_apps"
    override val description = "列出已安装且可启动的应用（名称 + 包名）"
    override val parameters = schemaOf("filter" to stringParam("名称过滤关键字，可为空"))

    override suspend fun execute(args: JsonObject): String = withContext(Dispatchers.IO) {
        val filter = runCatching { args.get("filter")?.asString }.getOrNull()?.lowercase().orEmpty()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { resolve ->
                resolve.loadLabel(pm).toString() to resolve.activityInfo.packageName
            }
            .distinctBy { it.second }
            .filter { filter.isEmpty() || it.first.lowercase().contains(filter) || it.second.contains(filter) }
            .sortedBy { it.first }
            .take(MAX_APPS)
        if (apps.isEmpty()) "未找到匹配应用"
        else apps.joinToString("\n") { (label, pkg) -> "$label -> $pkg" }
    }

    companion object { private const val MAX_APPS = 100 }
}

class OpenUrlTool(private val context: Context) : AssistantTool {
    override val name = "open_url"
    override val description = "用浏览器打开一个 http/https 网页链接"
    override val parameters = schemaOf(
        "url" to stringParam("完整 URL，需带 http:// 或 https:// 前缀"),
        required = listOf("url"),
    )

    override suspend fun execute(args: JsonObject): String {
        val url = runCatching { args.get("url")?.asString }.getOrNull()
            ?: return "缺少 url 参数"
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "仅支持 http/https 链接，收到: $url"
        }
        return withContext(Dispatchers.Main) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, buildUri(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                "已打开 $url"
            } catch (e: Exception) {
                "打开失败: ${e.message}"
            }
        }
    }
}

class OpenSchemeTool(private val context: Context) : AssistantTool {
    override val name = "open_scheme"
    override val description = "通过 URL Scheme 深链打开应用内页面。会直接跳转，敏感页面需谨慎使用"
    override val dangerous = true
    override val parameters = schemaOf(
        "scheme" to stringParam("完整 scheme 链接，如 alipays://platformapi/startapp?appId=20000001"),
        required = listOf("scheme"),
    )

    override suspend fun execute(args: JsonObject): String {
        val scheme = runCatching { args.get("scheme")?.asString }.getOrNull()
            ?: return "缺少 scheme 参数"
        return withContext(Dispatchers.Main) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, buildUri(scheme))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                "已打开 $scheme"
            } catch (e: Exception) {
                "打开失败（可能未安装对应应用或 scheme 无效）: ${e.message}"
            }
        }
    }
}
