package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "compilations")
data class CompilationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "SUCCESS", "FAILURE", "IN_PROGRESS", "CANCELLED"
    val repoFullName: String,
    val branch: String = "main",
    val runId: Long? = null,
    val runNumber: Int? = null,
    val htmlUrl: String? = null,
    val artifactId: Long? = null,
    val artifactName: String? = null,
    val artifactDownloadUrl: String? = null,
    val errorMessage: String? = null,
    val localApkPath: String? = null,
    val diagnosticSummary: String? = null
)
