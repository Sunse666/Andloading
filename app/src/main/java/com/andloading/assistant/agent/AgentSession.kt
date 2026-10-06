package com.andloading.assistant.agent

import com.andloading.assistant.data.SettingsStore
import com.andloading.assistant.data.model.MessageEntity
import com.andloading.assistant.data.model.MessageKind
import com.andloading.assistant.data.model.Role
import com.andloading.assistant.data.model.TaskEntity
import com.andloading.assistant.data.model.TaskStatus
import com.andloading.assistant.data.model.ToolCall
import com.andloading.assistant.data.repository.ChatRepository
import com.google.gson.Gson
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AgentSession(
    private val repo: ChatRepository,
    private val settings: SettingsStore,
    prompts: PromptBuilder,
    tools: ToolRegistry,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val llm = LLMClient(settings)
    private val agent = AgentLoop(settings, llm, tools, prompts)

    private val _taskId = MutableStateFlow(UUID.randomUUID().toString())
    val currentTaskId: StateFlow<String> = _taskId.asStateFlow()
    private val taskId: String get() = _taskId.value

    val tasks: StateFlow<List<TaskEntity>> =
        repo.tasks()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> =
        _taskId
            .flatMapLatest { repo.messages(it) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _streaming = MutableStateFlow<String?>(null)
    val streaming: StateFlow<String?> = _streaming.asStateFlow()

    private val _activeTool = MutableStateFlow<String?>(null)
    val activeTool: StateFlow<String?> = _activeTool.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ToolCall?>(null)
    val pendingApproval: StateFlow<ToolCall?> = _pendingApproval.asStateFlow()

    private var job: Job? = null
    private var conversationPrimedFor: String? = null
    private val gson = Gson()

    init {
        prompts.personaProvider = { settings.personaPrompt.takeIf { it.isNotBlank() } }
        scope.launch {
            repo.enabledMemories().collect { list ->
                prompts.memoriesCache = list.asReversed()
            }
        }
    }

    val isBusy: Boolean
        get() = job?.isActive == true

    fun newConversation() {
        if (job?.isActive == true) return
        _taskId.value = UUID.randomUUID().toString()
        agent.resetConversation()
        conversationPrimedFor = null
        agent.screenshotApprovalScope = ScreenshotApprovalScope.NONE
    }

    fun switchThread(id: String) {
        if (id == taskId || job?.isActive == true) return
        _taskId.value = id
        agent.resetConversation()
        conversationPrimedFor = null
        agent.screenshotApprovalScope = ScreenshotApprovalScope.NONE
    }

    fun deleteThread(id: String) {
        if (id == taskId && job?.isActive == true) return
        scope.launch {
            repo.deleteTask(id)
            if (id == taskId) {
                _taskId.value = UUID.randomUUID().toString()
                agent.resetConversation()
                conversationPrimedFor = null
                agent.screenshotApprovalScope = ScreenshotApprovalScope.NONE
            }
        }
    }

    fun send(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || job?.isActive == true) return
        job = scope.launch {
            try {
                ensureTask(text)
                primeConversation()
                repo.append(MessageEntity(taskId = taskId, role = Role.USER, content = text))
                agent.addUserText(text)

                _streaming.value = ""
                var latestPartial = ""
                var preambleSaved = false
                val ticker = launch {
                    while (isActive) {
                        delay(100)
                        if (latestPartial.isNotEmpty()) _streaming.value = latestPartial
                    }
                }

                try {
                    agent.run().collect { step ->
                        when (step) {
                            is AgentStep.Partial -> {
                                latestPartial = step.text
                                _streaming.value = latestPartial
                            }
                            is AgentStep.ToolCallStarted -> {
                                _activeTool.value = step.call.name
                                if (!preambleSaved && latestPartial.isNotBlank()) {
                                    repo.append(
                                        MessageEntity(
                                            taskId = taskId,
                                            role = Role.ASSISTANT,
                                            content = latestPartial,
                                        ),
                                    )
                                }
                                preambleSaved = true
                            }
                            is AgentStep.WaitingApproval -> _pendingApproval.value = step.call
                            is AgentStep.ToolFinished -> {
                                _activeTool.value = null
                                _pendingApproval.value = null
                                preambleSaved = false
                                repo.append(
                                    MessageEntity(
                                        taskId = taskId,
                                        role = Role.TOOL,
                                        content = PiiFilter.sanitize(
                                            step.result,
                                            PiiFilter.fromSettings(settings),
                                        ),
                                        kind = MessageKind.TOOL,
                                        toolName = step.call.name,
                                        toolArgs = step.call.argumentsJson,
                                    ),
                                )
                            }
                            is AgentStep.Done -> {
                                ticker.cancel()
                                _streaming.value = step.finalText
                                _activeTool.value = null
                                _pendingApproval.value = null
                                if (step.finalText.isNotBlank()) {
                                    repo.append(
                                        MessageEntity(
                                            taskId = taskId,
                                            role = Role.ASSISTANT,
                                            content = step.finalText,
                                        ),
                                    )
                                }
                                _streaming.value = null
                            }
                            is AgentStep.Failed -> {
                                ticker.cancel()
                                _streaming.value = null
                                _activeTool.value = null
                                _pendingApproval.value = null
                                repo.append(
                                    MessageEntity(
                                        taskId = taskId,
                                        role = Role.ASSISTANT,
                                        content = "请求失败：${step.reason}",
                                    ),
                                )
                            }
                        }
                    }
                } finally {
                    ticker.cancel()
                    _streaming.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _streaming.value = null
                _activeTool.value = null
                runCatching {
                    repo.append(
                        MessageEntity(
                            taskId = taskId, role = Role.ASSISTANT,
                            content = "处理失败：${e.message}",
                        ),
                    )
                }
            }
        }
    }

    fun approve(always: Boolean) {
        val call = _pendingApproval.value ?: return
        if (always) agent.tools.setAlwaysAllow(call.name)
        _pendingApproval.value = null
        agent.approvalGate.resolve(true)
    }

    fun approveScreenshot(scope: ScreenshotApprovalScope) {
        agent.screenshotApprovalScope = scope
        _pendingApproval.value = null
        agent.approvalGate.resolve(true)
    }

    fun deny() {
        _pendingApproval.value = null
        agent.approvalGate.resolve(false)
    }

    fun stop() {
        val j = job ?: return
        if (!j.isActive) return
        if (_pendingApproval.value != null) agent.approvalGate.resolve(false)
        j.cancel()
        val partial = _streaming.value.orEmpty()
        if (partial.isNotBlank()) {
            scope.launch {
                repo.append(MessageEntity(taskId = taskId, role = Role.ASSISTANT, content = partial))
            }
        }
        _streaming.value = null
        _activeTool.value = null
        _pendingApproval.value = null
    }

    private suspend fun primeConversation() {
        if (conversationPrimedFor == taskId) return
        val recent = repo.recent(taskId, ToolExecutionLimits.AGENT_MAX_CONVERSATION_MESSAGES)
        val restored = ArrayList<ChatMessage>(recent.size + 1)
        recent.reversed().forEach { m ->
            when (m.role) {
                Role.USER -> restored += ChatMessage.text(Role.USER, m.content)
                Role.ASSISTANT -> restored += ChatMessage.text(Role.ASSISTANT, m.content)
                Role.TOOL -> {
                    val synthId = "restore_${restored.size}"
                    restored += ChatMessage.text(
                        Role.ASSISTANT,
                        "",
                        toolCalls = listOf(
                            ToolCallDto(
                                id = synthId,
                                function = FunctionCallDto(
                                    m.toolName ?: "unknown",
                                    m.toolArgs ?: "{}",
                                ),
                            ),
                        ),
                    )
                    restored += ChatMessage.text(
                        Role.TOOL,
                        m.content.take(ToolExecutionLimits.TOOL_RESULT_MAX_CHARS),
                        toolCallId = synthId,
                        name = m.toolName,
                    )
                }
            }
        }
        agent.restore(restored)
        conversationPrimedFor = taskId
    }

    private suspend fun ensureTask(firstMessage: String) {
        val now = System.currentTimeMillis()
        val existing = repo.getTask(taskId)
        if (existing == null) {
            repo.insertTask(
                TaskEntity(
                    id = taskId,
                    title = firstMessage.take(20),
                    status = TaskStatus.RUNNING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            repo.setTaskStatus(taskId, TaskStatus.RUNNING, now)
        }
    }
}
