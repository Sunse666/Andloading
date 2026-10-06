package com.andloading.assistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.andloading.assistant.data.model.AgentLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentLogDao {

    @Query("SELECT * FROM agent_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = MAX_OBSERVE): Flow<List<AgentLogEntity>>

    @Insert
    suspend fun insert(log: AgentLogEntity): Long

    @Query(
        "SELECT MAX(createdAt) FROM agent_logs " +
            "WHERE sourcePackage = :sourcePackage AND status IN (:executedStatuses)",
    )
    suspend fun lastRunAt(sourcePackage: String, executedStatuses: List<String>): Long?

    @Query(
        "SELECT COUNT(*) FROM agent_logs " +
            "WHERE sourcePackage = :sourcePackage AND createdAt >= :since AND status IN (:executedStatuses)",
    )
    suspend fun countSince(sourcePackage: String, since: Long, executedStatuses: List<String>): Int

    @Query(
        "SELECT MAX(createdAt) FROM agent_logs " +
            "WHERE sourcePackage = :sourcePackage AND status = :status",
    )
    suspend fun lastLogAtOfStatus(sourcePackage: String, status: String): Long?

    @Query("DELETE FROM agent_logs")
    suspend fun clearAll()

    companion object {
        const val MAX_OBSERVE = 50
    }
}
