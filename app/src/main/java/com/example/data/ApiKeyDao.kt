package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ApiKeyDao {
    @Insert
    suspend fun insert(apiKey: ApiKeyEntity): Long

    @Query("SELECT * FROM api_keys WHERE modelId = :modelId")
    suspend fun getKeysForModel(modelId: String): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE hash = :hash")
    suspend fun getByKeyHash(hash: String): ApiKeyEntity?

    @Update
    suspend fun update(apiKey: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun delete(id: Long)
}
