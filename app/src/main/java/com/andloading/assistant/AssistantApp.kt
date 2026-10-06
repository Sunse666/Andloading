package com.andloading.assistant

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.andloading.assistant.accessibility.ClickElementTool
import com.andloading.assistant.accessibility.GlobalActionTool
import com.andloading.assistant.accessibility.InputTextTool
import com.andloading.assistant.accessibility.ReadScreenTool
import com.andloading.assistant.accessibility.ScrollTool
import com.andloading.assistant.accessibility.ScreenshotTool
import com.andloading.assistant.agent.AgentSession
import com.andloading.assistant.agent.ProxyControlTool
import com.andloading.assistant.agent.ProxyRunner
import com.andloading.assistant.agent.PromptBuilder
import com.andloading.assistant.agent.ReadMemoryTool
import com.andloading.assistant.agent.ToolRegistry
import com.andloading.assistant.data.SettingsStore
import com.andloading.assistant.data.db.AppDatabase
import com.andloading.assistant.data.repository.ChatRepository
import com.andloading.assistant.service.FloatingWindowService
import com.andloading.assistant.tools.LaunchAppTool
import com.andloading.assistant.tools.ListAppsTool
import com.andloading.assistant.tools.LaunchComponentTool
import com.andloading.assistant.tools.OpenSchemeTool
import com.andloading.assistant.tools.OpenUrlTool
import com.andloading.assistant.tools.ShizukuStatusTool

class AssistantApp : Application() {

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry(this@AssistantApp).apply {
            register(LaunchAppTool(this@AssistantApp))
            register(ListAppsTool(this@AssistantApp))
            register(OpenUrlTool(this@AssistantApp))
            register(OpenSchemeTool(this@AssistantApp))
            register(LaunchComponentTool())
            register(ShizukuStatusTool(this@AssistantApp))
            register(ReadScreenTool())
            register(ClickElementTool())
            register(InputTextTool())
            register(ScrollTool())
            register(GlobalActionTool())
            register(ScreenshotTool())
            register(ProxyControlTool(this@AssistantApp))
            register(ReadMemoryTool(database))
        }
    }

    val agentSession: AgentSession by lazy {
        AgentSession(
            repo = ChatRepository(database),
            settings = settings,
            prompts = PromptBuilder(),
            tools = toolRegistry,
        )
    }

    val proxyRunner: ProxyRunner by lazy {
        ProxyRunner(
            settings = settings,
            repo = ChatRepository(database),
            tools = toolRegistry,
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLog()
        com.andloading.assistant.service.KeepAliveJobService.schedule(this)
        registerOverlaySuppression()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AGENT,
                getString(R.string.channel_agent),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.channel_agent_description) },
        )
    }

    private fun installCrashLog() {
        val delegate = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = getExternalFilesDir(null) ?: filesDir
                java.io.File(dir, "crash_log.txt").appendText(
                    "=== ${java.util.Date()} thread=${thread.name} ===\n" +
                        android.util.Log.getStackTraceString(throwable) + "\n\n",
                )
            }
            delegate?.uncaughtException(thread, throwable)
        }
    }

    private fun registerOverlaySuppression() {
        var resumedCount = 0
        var pendingRestore: Runnable? = null
        val handler = Handler(Looper.getMainLooper())
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                resumedCount++
                pendingRestore?.let { handler.removeCallbacks(it) }
                pendingRestore = null
                FloatingWindowService.setOwnAppForeground(true)
            }

            override fun onActivityPaused(activity: Activity) {
                resumedCount = (resumedCount - 1).coerceAtLeast(0)
                if (resumedCount == 0) {
                    pendingRestore = Runnable {
                        pendingRestore = null
                        FloatingWindowService.setOwnAppForeground(false)
                    }.also { handler.postDelayed(it, 300) }
                }
            }
        })
    }

    companion object {
        const val CHANNEL_AGENT = "assistant_status"

        lateinit var instance: AssistantApp
            private set
    }
}
