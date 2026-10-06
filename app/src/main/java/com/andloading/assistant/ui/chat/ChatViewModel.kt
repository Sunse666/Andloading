package com.andloading.assistant.ui.chat

import androidx.lifecycle.ViewModel
import com.andloading.assistant.agent.AgentSession
import com.andloading.assistant.agent.ScreenshotApprovalScope

class ChatViewModel(
    private val session: AgentSession,
) : ViewModel() {

    val currentTaskId: kotlinx.coroutines.flow.StateFlow<String> get() = session.currentTaskId
    val tasks get() = session.tasks
    val messages get() = session.messages
    val streaming get() = session.streaming
    val activeTool get() = session.activeTool
    val pendingApproval get() = session.pendingApproval

    fun send(text: String) = session.send(text)
    fun stop() = session.stop()
    fun approve(always: Boolean) = session.approve(always)
    fun approveScreenshot(scope: ScreenshotApprovalScope) = session.approveScreenshot(scope)
    fun deny() = session.deny()
    fun newConversation() = session.newConversation()
    fun switchThread(id: String) = session.switchThread(id)
    fun deleteThread(id: String) = session.deleteThread(id)
}
