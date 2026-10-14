package com.example.data.diagnostic

enum class IssueSeverity {
    INFO,
    WARNING,
    ERROR
}

data class DiagnosticIssue(
    val id: String,
    val title: String,
    val description: String,
    val filePath: String? = null,
    val severity: IssueSeverity,
    val isAutoFixable: Boolean,
    val isFixed: Boolean = false,
    val fixAction: String? = null,
    val suggestedSolution: String? = null
)

data class ProjectDiagnosticReport(
    val totalFilesAnalyzed: Int,
    val detectedTechnologies: List<String>,
    val issues: List<DiagnosticIssue>,
    val autoFixesCount: Int,
    val warningsCount: Int,
    val errorsCount: Int,
    val summaryMessage: String,
    val hasFatalUnresolvedErrors: Boolean = false
) {
    val fixedIssues: List<DiagnosticIssue>
        get() = issues.filter { it.isFixed }

    val warningIssues: List<DiagnosticIssue>
        get() = issues.filter { !it.isFixed && it.severity == IssueSeverity.WARNING }

    val errorIssues: List<DiagnosticIssue>
        get() = issues.filter { !it.isFixed && it.severity == IssueSeverity.ERROR }
}
