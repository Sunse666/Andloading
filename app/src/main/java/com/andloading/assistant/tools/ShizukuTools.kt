package com.andloading.assistant.tools

import android.content.Context
import android.os.ParcelFileDescriptor
import com.andloading.assistant.agent.AssistantTool
import com.andloading.assistant.agent.schemaOf
import com.andloading.assistant.agent.stringParam
import com.andloading.assistant.shizuku.ShizukuGate
import com.andloading.assistant.shizuku.ShizukuState
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

class LaunchComponentTool : AssistantTool {
    override val name = "launch_component"
    override val description = "用 Shizuku 通过 am start -n 启动指定 Activity 组件（可启动未导出组件）。需要 Shizuku 就绪"
    override val dangerous = true
    override val parameters = schemaOf(
        "package_name" to stringParam("目标应用包名，如 com.tencent.mm"),
        "activity" to stringParam("完整组件类名或相对类名，如 com.tencent.mm.ui.LauncherUI 或 .ui.LauncherUI"),
        required = listOf("package_name", "activity"),
    )

    override suspend fun execute(args: JsonObject): String = withContext(Dispatchers.IO) {
        val pkg = runCatching { args.get("package_name")?.asString }.getOrNull()
            ?: return@withContext "缺少 package_name 参数"
        val activity = runCatching { args.get("activity")?.asString }.getOrNull()
            ?: return@withContext "缺少 activity 参数"
        try {
            val binder = Shizuku.getBinder()
                ?: throw IllegalStateException("Shizuku binder 未就绪")
            val service = IShizukuService.Stub.asInterface(ShizukuBinderWrapper(binder))
            val process = service.newProcess(
                arrayOf("am", "start", "-n", "$pkg/$activity"),
                null,
                null,
            )
            val output = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                .bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            if (exit == 0) "已通过 Shizuku 启动组件 $pkg/$activity\n$output"
            else "am start 失败(exit=$exit)：${output.take(500)}"
        } catch (e: Exception) {
            "Shizuku 通道不可用（${e.message}）。请改用 launch_app 按 Intent 直启。"
        }
    }
}

class ShizukuStatusTool(private val context: android.content.Context) : AssistantTool {
    override val name = "shizuku_status"
    override val description = "查询 Shizuku 当前状态（未安装/未运行/未授权/就绪），用于判断能否执行需要 shell 权限的操作"
    override val parameters = schemaOf()

    override suspend fun execute(args: JsonObject): String = withContext(Dispatchers.IO) {
        when (ShizukuGate.state(context)) {
            ShizukuState.NOT_INSTALLED -> "Shizuku 未安装：无法执行 shell 级操作，只能用 Intent 直启（launch_app/open_url）"
            ShizukuState.NOT_RUNNING -> "Shizuku 已安装但未运行：请用户打开 Shizuku 并激活"
            ShizukuState.NOT_AUTHORIZED -> "Shizuku 运行中但本应用未获授权：请用户在权限引导页授权"
            ShizukuState.READY -> "Shizuku 就绪：可以执行 launch_component 等 shell 级操作"
        }
    }
}
