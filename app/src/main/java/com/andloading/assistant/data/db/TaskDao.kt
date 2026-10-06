package com.andloading.assistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.andloading.assistant.data.model.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Insert
    suspend fun insert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun get(taskId: String): TaskEntity?

    @Query("UPDATE tasks SET title = :title, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun rename(taskId: String, title: String, updatedAt: Long)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun setStatus(taskId: String, status: String, updatedAt: Long)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun delete(taskId: String)
}
