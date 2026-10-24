package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project_folders ORDER BY lastIndexedTimestamp DESC")
    fun getAllProjects(): Flow<List<ProjectFolderEntity>>

    @Query("SELECT * FROM project_folders WHERE uriString = :uriString LIMIT 1")
    suspend fun getProject(uriString: String): ProjectFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectFolderEntity)

    @Query("DELETE FROM project_folders WHERE uriString = :uriString")
    suspend fun deleteProject(uriString: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<CodeChunkEntity>)

    @Query("DELETE FROM code_chunks WHERE projectUri = :projectUri")
    suspend fun deleteChunksForProject(projectUri: String)

    @Query("SELECT * FROM code_chunks WHERE projectUri = :projectUri")
    suspend fun getChunksForProject(projectUri: String): List<CodeChunkEntity>

    @Query("""
        SELECT * FROM code_chunks 
        WHERE projectUri = :projectUri 
        AND (content LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%' OR filePath LIKE '%' || :query || '%')
        LIMIT :limit
    """)
    suspend fun searchChunks(projectUri: String, query: String, limit: Int = 10): List<CodeChunkEntity>

    @Transaction
    suspend fun replaceProjectChunks(project: ProjectFolderEntity, chunks: List<CodeChunkEntity>) {
        deleteChunksForProject(project.uriString)
        insertProject(project)
        insertChunks(chunks)
    }
}
