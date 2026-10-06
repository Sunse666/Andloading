package com.andloading.assistant.agent

import com.andloading.assistant.accessibility.ScreenshotTool
import com.andloading.assistant.data.model.Role
import com.andloading.assistant.data.model.ToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed interface AgentStep {
    data class Partial(val text: String) : AgentStep
    data class ToolCallStarted(val call: ToolCall) : AgentStep
    data class ToolFinished(val call: ToolCall, val result: String) : AgentStep
    data class WaitingApproval(val call: ToolCall) : AgentStep
    data class Done(val finalText: String) : AgentStep
    data class Failed(val reason: String) : AgentStep
}

class AgentLoop(
    private val settings: com.andloading.assistant.data.SettingsStore,
    private val llm: LLMClient,
    val tools: ToolRegistry,
    private val prompts: PromptBuilder = PromptBuilder(),
) {

    val approvalGate = ApprovalGate()
    val conversation = ConversationManager(prompts)

    fun resetConversation() = conversation.reset()

    fun addUserText(text: String) {
        conversation.addUserText(PiiFilter.sanitize(text, PiiFilter.fromSettings(settings)))
    }

    fun restore(messages: List<ChatMessage>) {
        conversation.reset()
        val opts = PiiFilter.fromSettings(settings)
        messages.forEach { m ->
            if (m.role == Role.USER) {
                conversation.add(ChatMessage.text(Role.USER, PiiFilter.sanitize(m.contentText(), opts)))
            } else {
                conversation.add(m)
            }
        }
    }

    fun isEmpty() = conversation.isEmpty()

    @Volatile
    var stepCapOverride: Int? = null

    @Volatile
    var screenshotApprovalScope: ScreenshotApprovalScope = ScreenshotApprovalScope.NONE

    fun run(): Flow<AgentStep> = flow {
        compressIfNeeded()
        val maxSteps = stepCapOverride
            ?: if (settings.agentStepsUnlimited) Int.MAX_VALUE else settings.agentMaxSteps
        fun resetRunScope() {
            if (screenshotApprovalScope == ScreenshotApprovalScope.THIS_RUN) {
                screenshotApprovalScope = ScreenshotApprovalScope.NONE
            }
        }
        var lastCallKey: String? = null
        var identicalCount = 0
        repeat(maxSteps) {
            conversation.trim()

            val sb = StringBuilder()
            var calls: List<ToolCallDto> = emptyList()
            try {
                llm.chatStreamTools(conversation.messages, tools.toolSpecs()).collect { ev ->
                    when (ev) {
                        is LLMClient.StreamEvent.TextDelta -> {
                            sb.append(ev.text)
                            emit(AgentStep.Partial(sb.toString()))
                        }
                        is LLMClient.StreamEvent.ToolCallsReady -> calls = ev.calls
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                resetRunScope()
                emit(AgentStep.Failed("模型调用失败: ${e.message}"))
                return@flow
            }

            val text = sb.toString()
            conversation.add(
                ChatMessage.text(Role.ASSISTANT, text, toolCalls = calls.ifEmpty { null }),
            )
            val visibleText = stripThinking(text)
            if (calls.isEmpty()) {
                resetRunScope()
                emit(AgentStep.Done(visibleText))
                return@flow
            }

            for (dto in calls) {
                val call = ToolCall(dto.id, dto.function.name, dto.function.arguments)
                val callKey = "${call.name}|${call.argumentsJson}"
                identicalCount = if (callKey == lastCallKey) identicalCount + 1 else 1
                lastCallKey = callKey
                emit(AgentStep.ToolCallStarted(call))
                when (tools.effectivePermission(call.name)) {
                    PermissionLevel.FORBID -> {
                        val denied = "工具 ${call.name} 已被用户禁止"
                        emit(AgentStep.ToolFinished(call, denied))
                        conversation.addToolResult(call.id, call.name, denied)
                        continue
                    }
                    PermissionLevel.ASK -> {
                        if (call.name == ScreenshotTool.NAME &&
                            screenshotApprovalScope != ScreenshotApprovalScope.NONE
                        ) {
                            Unit
                        } else {
                            emit(AgentStep.WaitingApproval(call))
                            val approved = approvalGate.awaitApproval()
                            if (!approved) {
                                val denied = "用户拒绝了该操作"
                                emit(AgentStep.ToolFinished(call, denied))
                                conversation.addToolResult(call.id, call.name, denied)
                                continue
                            }
                        }
                    }
                    PermissionLevel.ALLOW -> Unit
                }
                val result = tools.execute(call)
                val sanitized = PiiFilter.sanitize(result, PiiFilter.fromSettings(settings))
                val guarded = if (identicalCount >= 2) {
                    "$sanitized\n[系统提示] 该工具已用相同参数连续调用 $identicalCount 次" +
                        "请停止重复，换一种策略（先 read_screen 确认界面，或直接向用户说明卡点）。"
                } else {
                    sanitized
                }
                emit(AgentStep.ToolFinished(call, guarded))
                val image = if (call.name == ScreenshotTool.NAME) {
                    ScreenshotTool.consumePendingImage()
                } else {
                    null
                }
                if (image != null) {
                    conversation.addToolResultWithImage(call.id, call.name, guarded, image)
                } else {
                    conversation.addToolResult(call.id, call.name, guarded)
                }
            }
        }
        resetRunScope()
        emit(AgentStep.Done("已达最大步数（$maxSteps），任务子步骤过多"))
    }.flowOn(Dispatchers.Default)

    private fun stripThinking(text: String): String {
        if (settings.showThinking) return text
        var cleaned = text.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.DOT_MATCHES_ALL), "")
        val idx = cleaned.indexOf("</think>")
        if (idx >= 0) cleaned = cleaned.substring(idx + 8).trimStart()
        return cleaned.trim()
    }

    private suspend fun compressIfNeeded() {
        if (conversation.estimateTokens() <= ToolExecutionLimits.TOKEN_BUDGET) return
        val excerpt = conversation.excerptForCompress(ToolExecutionLimits.COMPRESS_KEEP_LAST)
            ?: return
        val summary = try {
            llm.chat(
                listOf(
                    ChatMessage.text(
                        Role.USER,
                        "请把以下对话历史压缩为要点摘要（500 字以内，保留用户指令、已执行操作与结果、用户偏好）：\n\n$excerpt",
                    ),
                ),
            ).message.contentText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        if (summary.isNotBlank()) {
            conversation.compressWith(summary, ToolExecutionLimits.COMPRESS_KEEP_LAST)
        }
    }
}
