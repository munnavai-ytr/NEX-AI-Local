package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_facts ORDER BY timestamp DESC")
    fun getAllFacts(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts WHERE isEnabled = 1 ORDER BY timestamp DESC")
    suspend fun getEnabledFacts(): List<MemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: MemoryFactEntity): Long

    @Update
    suspend fun updateFact(fact: MemoryFactEntity)

    @Query("UPDATE memory_facts SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun toggleFactEnabled(id: Long, isEnabled: Boolean)

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteFact(id: Long)

    @Query("DELETE FROM memory_facts")
    suspend fun clearAllFacts()
}
