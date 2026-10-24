package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM local_models ORDER BY addedTimestamp DESC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM local_models WHERE isLastUsed = 1 LIMIT 1")
    suspend fun getLastUsedModel(): ModelEntity?

    @Query("SELECT * FROM local_models WHERE filePath = :path LIMIT 1")
    suspend fun getModelByPath(path: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity): Long

    @Update
    suspend fun updateModel(model: ModelEntity)

    @Query("UPDATE local_models SET isLastUsed = 0")
    suspend fun clearLastUsed()

    @Query("UPDATE local_models SET isLastUsed = 1 WHERE filePath = :path")
    suspend fun setLastUsed(path: String)

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteModel(id: Long)

    @Query("DELETE FROM local_models WHERE filePath = :path")
    suspend fun deleteModelByPath(path: String)
}
