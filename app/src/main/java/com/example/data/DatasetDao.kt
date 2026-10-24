package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DatasetDao {
    @Query("SELECT * FROM dataset_examples ORDER BY timestamp DESC")
    fun getAllExamples(): Flow<List<DatasetExampleEntity>>

    @Query("SELECT * FROM dataset_examples ORDER BY timestamp DESC")
    suspend fun getAllExamplesList(): List<DatasetExampleEntity>

    @Query("""
        SELECT * FROM dataset_examples 
        WHERE userPrompt LIKE '%' || :query || '%' 
           OR assistantAnswer LIKE '%' || :query || '%' 
           OR systemPrompt LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchExamples(query: String): Flow<List<DatasetExampleEntity>>

    @Query("SELECT * FROM dataset_examples WHERE id = :id LIMIT 1")
    suspend fun getExampleById(id: Long): DatasetExampleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExample(example: DatasetExampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamples(examples: List<DatasetExampleEntity>)

    @Update
    suspend fun updateExample(example: DatasetExampleEntity)

    @Query("DELETE FROM dataset_examples WHERE id = :id")
    suspend fun deleteExample(id: Long)

    @Query("DELETE FROM dataset_examples")
    suspend fun clearAllExamples()

    @Query("SELECT COUNT(*) FROM dataset_examples")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dataset_examples WHERE feedback > 0")
    fun getThumbsUpCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM dataset_examples WHERE feedback < 0")
    fun getThumbsDownCount(): Flow<Int>
}
