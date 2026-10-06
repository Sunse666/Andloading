package com.andloading.assistant.data.repository

import com.andloading.assistant.data.db.AppDatabase
import com.andloading.assistant.data.model.AgentLogEntity
import com.andloading.assistant.data.model.AgentRunStatus
import com.andloading.assistant.data.model.MemoryEntity
import com.andloading.assistant.data.model.MessageEntity
import com.andloading.assistant.data.model.TaskEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val db: AppDatabase) {

    fun messages(taskId: String): Flow<List<MessageEntity>> =
        db.chatDao().observeMessages(taskId)

    suspend fun recent(taskId: String, limit: Int): List<MessageEntity> =
        db.chatDao().getRecent(taskId, limit)

    suspend fun append(message: MessageEntity): Long = db.chatDao().insert(message)

    suspend fun clearTask(taskId: String) = db.chatDao().clearTask(taskId)

    suspend fun clearAll() = db.chatDao().clearAll()

    fun tasks(): Flow<List<TaskEntity>> = db.taskDao().observeTasks()

    suspend fun getTask(taskId: String): TaskEntity? = db.taskDao().get(taskId)

    suspend fun insertTask(task: TaskEntity) = db.taskDao().insert(task)

    suspend fun renameTask(taskId: String, title: String, updatedAt: Long) =
        db.taskDao().rename(taskId, title, updatedAt)

    suspend fun setTaskStatus(taskId: String, status: String, updatedAt: Long) =
        db.taskDao().setStatus(taskId, status, updatedAt)

    suspend fun deleteTask(taskId: String) = db.taskDao().delete(taskId)

    fun enabledMemories(): Flow<List<MemoryEntity>> = db.memoryDao().observeEnabled()

    fun memories(): Flow<List<MemoryEntity>> = db.memoryDao().observeAll()

    suspend fun countEnabledMemories(): Int = db.memoryDao().countEnabled()

    suspend fun insertMemory(memory: MemoryEntity): Long = db.memoryDao().insert(memory)

    suspend fun insertMemories(memories: List<MemoryEntity>) = db.memoryDao().insertAll(memories)

    suspend fun updateMemory(memory: MemoryEntity) = db.memoryDao().update(memory)

    suspend fun deleteMemory(memory: MemoryEntity) = db.memoryDao().delete(memory)

    suspend fun clearMemories() = db.memoryDao().deleteAll()

    fun agentLogs(): Flow<List<AgentLogEntity>> = db.agentLogDao().observeRecent()

    suspend fun appendAgentLog(log: AgentLogEntity) = db.agentLogDao().insert(log)

    suspend fun lastAgentRunAt(sourcePackage: String): Long? = db.agentLogDao().lastRunAt(
        sourcePackage,
        listOf(AgentRunStatus.SUCCESS, AgentRunStatus.FAILED),
    )

    suspend fun agentRunCountSince(sourcePackage: String, since: Long): Int =
        db.agentLogDao().countSince(
            sourcePackage,
            since,
            listOf(AgentRunStatus.SUCCESS, AgentRunStatus.FAILED),
        )

    suspend fun lastAgentLogAtOfStatus(sourcePackage: String, status: String): Long? =
        db.agentLogDao().lastLogAtOfStatus(sourcePackage, status)

    suspend fun clearAgentLogs() = db.agentLogDao().clearAll()
}
