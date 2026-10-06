package com.andloading.assistant.agent

import com.andloading.assistant.AssistantApp
import com.andloading.assistant.accessibility.AssistantAccessibilityService
import com.andloading.assistant.accessibility.ScreenReader
import com.andloading.assistant.accessibility.ScreenshotTool
import com.andloading.assistant.data.SettingsStore
import com.andloading.assistant.data.model.AgentLogEntity
import com.andloading.assistant.data.model.AgentRunStatus
import com.andloading.assistant.data.model.ProxyTrigger
import com.andloading.assistant.data.repository.ChatRepository
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ProxyRunner(
    private val settings: SettingsStore,
    private val repo: ChatRepository,
    private val tools: ToolRegistry,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val promptBuilder = PromptBuilder()

    @Volatile
    private var running = false

    init {
        promptBuilder.personaProvider = { settings.personaPrompt.takeIf { it.isNotBlank() } }
        scope.launch {
            repo.enabledMemories().collect { list ->
                promptBuilder.memoriesCache = list.asReversed()
            }
        }
    }

    fun onNotification(sourcePackage: String, title: String, content: String) {
        if (!settings.autoEnabled) return
        if (sourcePackage !in settings.proxyWhitelist) return
        val contextSummary = "白名单应用 $sourcePackage 收到新通知。" +
            "标题：${title.take(80)}。内容：${content.take(120)}。"
        scope.launch { tryRun(ProxyTrigger.NOTIFICATION, sourcePackage, contextSummary) }
    }

    fun onSchedule() {
        if (!settings.autoEnabled) return
        val whitelist = settings.proxyWhitelist
        if (whitelist.isEmpty()) return
        val contextSummary = "定时巡检：检查白名单应用（${whitelist.sorted().joinToString()}）" +
            "是否有需要处理的消息"
        scope.launch { tryRun(ProxyTrigger.SCHEDULED, "", contextSummary) }
    }

    @Volatile
    private var lastRealtimeHash: Int = 0

    fun onRealtimeTick() {
        if (!settings.autoEnabled || !settings.proxyRealtimeEnabled) return
        if (settings.proxyWhitelist.isEmpty()) return
        if (running || AssistantApp.instance.agentSession.isBusy) return
        scope.launch { realtimeTick() }
    }

    private suspend fun realtimeTick() {
        val service = AssistantAccessibilityService.instance ?: return
        val whitelist = settings.proxyWhitelist
        val reader = ScreenReader(service)
        val elements = runCatching { reader.readTree() }.getOrDefault(emptyList())
        val pkg = reader.lastReadPackage
        if (pkg == null || pkg == service.packageName || pkg !in whitelist) return

        val hash: Int = if (elements.isNotEmpty()) {
            elements.hashCode()
        } else if (settings.proxyScreenshot && settings.screenshotEnabled) {
            ScreenshotTool.captureChangeHash() ?: return
        } else {
            return
        }
        if (hash == lastRealtimeHash) return
        lastRealtimeHash = hash

        val contextSummary = "实时模式：前台应用 $pkg 的屏幕内容相比上一轮有变化，" +
            "请检查并处理需要处理的消息"
        tryRun(ProxyTrigger.REALTIME, pkg, contextSummary)
    }

    private suspend fun tryRun(
        trigger: String,
        sourcePackage: String,
        contextSummary: String,
    ) {
        val now = System.currentTimeMillis()
        if (trigger == ProxyTrigger.NOTIFICATION) {
            val lastRunAt = repo.lastAgentRunAt(sourcePackage)
            if (lastRunAt != null && now - lastRunAt < COOLDOWN_MS) {
                logSuppressedOnce(sourcePackage, AgentRunStatus.COOLDOWN, now, "冷却中（${COOLDOWN_MS / 1000}s），跳过本次触发")
                return
            }
        }
        val limit = if (trigger == ProxyTrigger.REALTIME) REALTIME_DAILY_LIMIT else DAILY_LIMIT
        if (repo.agentRunCountSince(sourcePackage, startOfDay(now)) >= limit) {
            logSuppressedOnce(sourcePackage, AgentRunStatus.DAILY_LIMIT, now, "今日已执行 $limit 次，达到每日上限")
            return
        }
        if (running || AssistantApp.instance.agentSession.isBusy) {
            logSuppressedOnce(sourcePackage, AgentRunStatus.SKIPPED_BUSY, now, "主会话或另一代理轮执行中，跳过")
            return
        }
        running = true
        try {
            execute(trigger, sourcePackage, contextSummary)
        } finally {
            running = false
        }
    }

    private suspend fun execute(
        trigger: String,
        sourcePackage: String,
        contextSummary: String,
    ) {
        val loop = AgentLoop(settings, LLMClient(settings), tools, promptBuilder)
        loop.stepCapOverride = (settings.agentMaxSteps / 2).coerceAtLeast(4)
        if (settings.proxyScreenshot && settings.screenshotEnabled) {
            loop.screenshotApprovalScope = ScreenshotApprovalScope.THIS_RUN
        }
        val task = buildString {
            append(PROXY_TASK_PROMPT)
            append("\n\n本次触发上下文：$contextSummary")
        }
        loop.addUserText(task)
        var finalText: String? = null
        var failure: String? = null
        try {
            loop.run().collect { step ->
                when (step) {
                    is AgentStep.WaitingApproval ->
                        loop.approvalGate.resolve(step.call.name in AUTO_APPROVED_TOOLS)
                    is AgentStep.Done -> finalText = step.finalText
                    is AgentStep.Failed -> failure = step.reason
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e.message
        }
        val pii = PiiFilter.fromSettings(settings)
        val summary = PiiFilter.sanitize(contextSummary.take(150), pii)
        if (failure == null) {
            repo.appendAgentLog(
                AgentLogEntity(
                    trigger = trigger,
                    sourcePackage = sourcePackage,
                    triggerSummary = summary,
                    detail = finalText.orEmpty().ifBlank { "（无文本输出，任务执行完毕）" },
                    status = AgentRunStatus.SUCCESS,
                ),
            )
        } else {
            repo.appendAgentLog(
                AgentLogEntity(
                    trigger = trigger,
                    sourcePackage = sourcePackage,
                    triggerSummary = summary,
                    detail = "执行失败：$failure",
                    status = AgentRunStatus.FAILED,
                ),
            )
        }
    }

    private suspend fun logSuppressedOnce(
        sourcePackage: String,
        status: String,
        now: Long,
        detail: String,
    ) {
        val window = if (status == AgentRunStatus.COOLDOWN) COOLDOWN_MS else 30_000L
        val last = repo.lastAgentLogAtOfStatus(sourcePackage, status)
        if (last != null && now - last < window) return
        repo.appendAgentLog(
            AgentLogEntity(
                trigger = ProxyTrigger.NOTIFICATION,
                sourcePackage = sourcePackage,
                triggerSummary = "",
                detail = detail,
                status = status,
            ),
        )
    }

    private fun startOfDay(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    companion object {
        private const val PROXY_TASK_PROMPT =
            "你是运行在 Android 手机上的消息代理，处于【无人值守】模式：" +
                "用户不在场，没有人能回答你的提问或批准你的请求。\n\n" +
                "绝对禁止（出现即任务失败）：\n" +
                "1. 禁止向用户提问——包括「需要我…吗」「是否回复」「请确认」等任何形式的征询；\n" +
                "2. 禁止说出「等待确认」「等待授权」后停下；\n" +
                "3. 禁止把「先询问用户」写进任何计划。\n\n" +
                "行为准则：\n" +
                "1. 收到触发上下文后立即用工具查看目标应用；所有界面操作工具" +
                "（launch_app/read_screen/click_element/input_text/scroll/global_action）" +
                "和截图都已由系统自动批准，直接调用即可，不会弹出确认；\n" +
                "2. 是否回复由你自主判断：日常聊天、普通问题正常回复；涉及转账/付款、" +
                "验证码、密码等敏感内容时不要操作，只在结束时说明原因；\n" +
                "3. 需要回复时一口气完成全流程：点输入框 → input_text → 点发送，" +
                "中途不停顿、不求确认；回复内容与语气参考人设；\n" +
                "4. 没有需要处理的消息、或无法安全处理时，直接说明原因并结束，不做多余操作。"

        private const val COOLDOWN_MS = 60_000L
        private const val DAILY_LIMIT = 50
        private const val REALTIME_DAILY_LIMIT = 600
        private val AUTO_APPROVED_TOOLS = setOf(
            "launch_app",
            "list_apps",
            "read_screen",
            "scroll",
            "click_element",
            "input_text",
            "global_action",
            "shizuku_status",
        )
    }
}
