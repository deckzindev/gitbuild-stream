package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CompilationDao {

    @Query("SELECT * FROM compilations ORDER BY timestamp DESC")
    fun getAllCompilations(): Flow<List<CompilationEntity>>

    @Query("SELECT * FROM compilations WHERE id = :id LIMIT 1")
    suspend fun getCompilationById(id: Long): CompilationEntity?

    @Query("SELECT * FROM compilations WHERE runId = :runId LIMIT 1")
    suspend fun getCompilationByRunId(runId: Long): CompilationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompilation(entity: CompilationEntity): Long

    @Update
    suspend fun updateCompilation(entity: CompilationEntity)

    @Delete
    suspend fun deleteCompilation(entity: CompilationEntity)

    @Query("DELETE FROM compilations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM compilations")
    suspend fun clearAll()
}
