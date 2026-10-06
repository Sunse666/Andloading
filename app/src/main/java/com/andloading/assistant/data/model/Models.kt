package com.andloading.assistant.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

object TaskStatus {
    const val RUNNING = "running"
    const val WAITING_APPROVAL = "waiting_approval"
    const val DONE = "done"
    const val FAILED = "failed"
}

@Entity(
    tableName = "messages",
    indices = [Index("taskId")],
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val role: String,
    val content: String,
    val kind: String = MessageKind.TEXT,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

object Role {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TOOL = "tool"
    const val SYSTEM = "system"
}

object MessageKind {
    const val TEXT = "text"
    const val THINKING = "thinking"
    const val TOOL = "tool"
}

@Entity(tableName = "memories", indices = [Index("createdAt")])
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String,
    val source: String = MemorySource.MANUAL,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

object MemorySource {
    const val MANUAL = "manual"
}

@Entity(
    tableName = "agent_logs",
    indices = [Index("sourcePackage"), Index("createdAt")],
)
data class AgentLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,
    val sourcePackage: String,
    val triggerSummary: String,
    val detail: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis(),
)

object ProxyTrigger {
    const val SCHEDULED = "SCHEDULED"
    const val NOTIFICATION = "NOTIFICATION"
    const val REALTIME = "REALTIME"
}

object AgentRunStatus {
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val COOLDOWN = "COOLDOWN"
    const val DAILY_LIMIT = "DAILY_LIMIT"
    const val SKIPPED_BUSY = "SKIPPED_BUSY"
}

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)
