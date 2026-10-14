package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GitHubApiClient
import com.example.data.db.AppDatabase
import com.example.data.db.CompilationEntity
import com.example.data.model.*
import com.example.data.storage.TokenStorage
import com.example.data.zip.ArtifactDownloader
import com.example.data.zip.ZipProcessor
import com.example.data.zip.ZipScanResult
import com.example.ui.notification.CompilationNotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class AppTab {
    COMPILER,
    HISTORY,
    PROFILE
}

enum class AppScreen {
    AUTH,
    MAIN_APP,
    SELECT_ZIP,
    COMPILATION_STEPS,
    COMPILATION_LOGS
}

enum class BuildStatus {
    IDLE,
    UPLOADING,
    QUEUED,
    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    CANCELLED
}

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILURE
}

data class UiState(
    val selectedTab: AppTab = AppTab.COMPILER,
    val currentScreen: AppScreen = AppScreen.AUTH,
    val token: String = "",
    val currentUser: GitHubUser? = null,
    val isCheckingAuth: Boolean = false,
    val authError: String? = null,

    // Repos
    val userRepos: List<GitHubRepo> = emptyList(),
    val isLoadingRepos: Boolean = false,
    val selectedRepoFullName: String = "",
    val targetBranch: String = "main",
    val isCreatingRepo: Boolean = false,

    // Zip & File
    val selectedFileName: String = "",
    val zipScan: ZipScanResult? = null,
    val isProcessingZip: Boolean = false,
    val zipError: String? = null,

    // Upload & Compilation
    val buildStatus: BuildStatus = BuildStatus.IDLE,
    val uploadProgress: Float = 0f,
    val uploadStageText: String = "",
    val activeRun: WorkflowRun? = null,
    val activeJobs: List<WorkflowJob> = emptyList(),
    val liveLogs: String = "",
    val artifacts: List<ArtifactItem> = emptyList(),
    val buildErrorMessage: String? = null,
    val isDownloadingApk: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApkFile: File? = null,
    val downloadErrorMessage: String? = null,

    // Diagnostic & Static Analysis
    val diagnosticReport: com.example.data.diagnostic.ProjectDiagnosticReport? = null,
    val isDiagnosticExpanded: Boolean = true,

    // 5 Real Pipeline Stages Tracking
    val step1Status: StepStatus = StepStatus.PENDING,
    val step1Detail: String = "Análise estática do código-fonte e verificação de falhas",
    val step2Status: StepStatus = StepStatus.PENDING,
    val step2Detail: String = "Preparação do projeto e empacotamento Git",
    val step3Status: StepStatus = StepStatus.PENDING,
    val step3Detail: String = "Envio seguro dos arquivos para o repositório",
    val step4Status: StepStatus = StepStatus.PENDING,
    val step4Detail: String = "Compilação no runner cloud do GitHub Actions",
    val step5Status: StepStatus = StepStatus.PENDING,
    val step5Detail: String = "Validação final e disponibilização do APK",

    // History (salvo localmente em cache/Room)
    val compilationHistory: List<CompilationEntity> = emptyList(),
    val downloadingRecordId: Long? = null,
    val downloadRecordProgress: Float = 0f,
    val currentRecordId: Long? = null
)

class CompilerViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = TokenStorage(application)
    private var apiClient: GitHubApiClient = GitHubApiClient { storage.token }
    private val notificationHelper = CompilationNotificationHelper(application)
    private val db = AppDatabase.getInstance(application)
    private val compilationDao = db.compilationDao()

    private val _uiState = MutableStateFlow(
        UiState(
            token = storage.token,
            selectedRepoFullName = storage.selectedRepo,
            targetBranch = storage.targetBranch.ifBlank { "main" }
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var compilationPollingJob: Job? = null
    private var isCurrentlyCompiling: Boolean = false

    init {
        // Observa o histórico de compilações localmente no Room (apenas compilações feitas pelo usuário no app)
        viewModelScope.launch {
            compilationDao.getAllCompilations().collect { list ->
                _uiState.value = _uiState.value.copy(compilationHistory = list)
            }
        }

        // Limpa quaisquer registros externos antigos previamente puxados do GitHub
        viewModelScope.launch(Dispatchers.IO) {
            try {
                compilationDao.deleteExternalSyncedRuns()
            } catch (_: Exception) { }
        }

        if (storage.token.isNotBlank()) {
            validateAndLoadUser(storage.token)
        }
    }

    private fun extractErrorMessage(e: Throwable): String {
        if (e is HttpException) {
            val code = e.code()
            val rawBody = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            if (!rawBody.isNullOrBlank()) {
                try {
                    val json = JSONObject(rawBody)
                    val msg = json.optString("message", "")
                    if (msg.isNotBlank()) {
                        return "HTTP $code: $msg"
                    }
                } catch (_: Exception) {
                    return "HTTP $code: $rawBody"
                }
            }
            return "HTTP $code: ${e.message()}"
        }
        return e.localizedMessage ?: e.javaClass.simpleName
    }

    fun onTokenChanged(newToken: String) {
        _uiState.value = _uiState.value.copy(token = newToken, authError = null)
    }

    fun selectTab(tab: AppTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun submitToken(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            _uiState.value = _uiState.value.copy(authError = "Por favor, insira um token do GitHub válido.")
            return
        }
        storage.token = cleanToken
        apiClient = GitHubApiClient { cleanToken }
        validateAndLoadUser(cleanToken)
    }

    private fun validateAndLoadUser(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingAuth = true, authError = null)
            try {
                val user = apiClient.api.getCurrentUser()
                storage.lastUsername = user.login
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    isCheckingAuth = false,
                    currentScreen = if (_uiState.value.currentScreen == AppScreen.AUTH) AppScreen.SELECT_ZIP else _uiState.value.currentScreen
                )
                loadUserRepos()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCheckingAuth = false,
                    authError = "Falha ao validar token: ${extractErrorMessage(e)}"
                )
            }
        }
    }

    fun loadUserRepos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRepos = true)
            try {
                val repos = apiClient.api.getUserRepos(perPage = 100)
                var currentSelected = _uiState.value.selectedRepoFullName
                var currentBranch = _uiState.value.targetBranch

                if (currentSelected.isBlank() && repos.isNotEmpty()) {
                    currentSelected = repos.first().fullName
                    currentBranch = repos.first().defaultBranch.ifBlank { "main" }
                    storage.selectedRepo = currentSelected
                    storage.targetBranch = currentBranch
                } else if (repos.isNotEmpty()) {
                    val found = repos.firstOrNull { it.fullName.equals(currentSelected, ignoreCase = true) }
                    if (found != null && currentBranch.isBlank()) {
                        currentBranch = found.defaultBranch.ifBlank { "main" }
                        storage.targetBranch = currentBranch
                    }
                }

                _uiState.value = _uiState.value.copy(
                    userRepos = repos,
                    selectedRepoFullName = currentSelected,
                    targetBranch = currentBranch.ifBlank { "main" },
                    isLoadingRepos = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingRepos = false)
            }
        }
    }

    fun onSelectRepo(repoFullName: String) {
        val cleanName = repoFullName.trim()
        val matchingRepo = _uiState.value.userRepos.firstOrNull { it.fullName.equals(cleanName, ignoreCase = true) }
        val branch = matchingRepo?.defaultBranch?.ifBlank { "main" } ?: _uiState.value.targetBranch.ifBlank { "main" }

        storage.selectedRepo = cleanName
        storage.targetBranch = branch
        _uiState.value = _uiState.value.copy(selectedRepoFullName = cleanName, targetBranch = branch)
    }

    fun onBranchChanged(branch: String) {
        val cleanBranch = branch.trim().ifBlank { "main" }
        storage.targetBranch = cleanBranch
        _uiState.value = _uiState.value.copy(targetBranch = cleanBranch)
    }

    fun createDedicatedBuildRepo(repoName: String = "git-compile-runner", onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingRepo = true)
            try {
                val newRepo = apiClient.api.createRepo(
                    CreateRepoRequest(
                        name = repoName,
                        description = "Repositório dedicado criado pelo GitCompile para compilação via GitHub Actions",
                        private = true,
                        autoInit = true
                    )
                )
                storage.selectedRepo = newRepo.fullName
                storage.targetBranch = newRepo.defaultBranch.ifBlank { "main" }
                loadUserRepos()
                _uiState.value = _uiState.value.copy(
                    selectedRepoFullName = newRepo.fullName,
                    targetBranch = newRepo.defaultBranch.ifBlank { "main" },
                    isCreatingRepo = false
                )
                onComplete()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingRepo = false,
                    authError = "Não foi possível criar o repositório: ${extractErrorMessage(e)}"
                )
            }
        }
    }

    fun logout() {
        compilationPollingJob?.cancel()
        isCurrentlyCompiling = false
        notificationHelper.cancelAll()
        storage.clear()
        apiClient = GitHubApiClient { "" }
        _uiState.value = UiState(currentScreen = AppScreen.AUTH)
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
    }

    fun toggleDiagnosticExpanded() {
        _uiState.value = _uiState.value.copy(isDiagnosticExpanded = !_uiState.value.isDiagnosticExpanded)
    }

    /**
     * Chamado imediatamente quando o usuário seleciona um arquivo ZIP.
     */
    fun onZipSelectedAndStartCompilation(uri: Uri, fileName: String) {
        if (isCurrentlyCompiling) {
            return
        }

        val cleanFileName = if (fileName.isNotBlank()) fileName else "codigo-fonte.zip"

        viewModelScope.launch {
            // Garante que o usuário está na aba do compilador
            _uiState.value = _uiState.value.copy(
                selectedTab = AppTab.COMPILER,
                selectedFileName = cleanFileName,
                currentScreen = AppScreen.COMPILATION_STEPS,
                isProcessingZip = true,
                zipError = null,
                buildStatus = BuildStatus.UPLOADING,
                diagnosticReport = null,
                isDiagnosticExpanded = true,
                step1Status = StepStatus.IN_PROGRESS,
                step1Detail = "Inspecionando arquivos e analisando erros...",
                step2Status = StepStatus.PENDING,
                step2Detail = "Aguardando diagnóstico...",
                step3Status = StepStatus.PENDING,
                step3Detail = "Aguardando preparação...",
                step4Status = StepStatus.PENDING,
                step4Detail = "Aguardando envio...",
                step5Status = StepStatus.PENDING,
                step5Detail = "Aguardando compilação...",
                activeRun = null,
                activeJobs = emptyList(),
                liveLogs = "",
                artifacts = emptyList(),
                downloadedApkFile = null,
                downloadErrorMessage = null,
                buildErrorMessage = null
            )

            // Registra imediatamente no banco de dados local
            val recordEntity = CompilationEntity(
                fileName = cleanFileName,
                timestamp = System.currentTimeMillis(),
                status = "IN_PROGRESS",
                repoFullName = _uiState.value.selectedRepoFullName,
                branch = _uiState.value.targetBranch
            )
            val recordId = withContext(Dispatchers.IO) {
                compilationDao.insertCompilation(recordEntity)
            }
            _uiState.value = _uiState.value.copy(currentRecordId = recordId)

            // Atualiza notificação: ETAPA 1
            notificationHelper.updateProgress(cleanFileName, 1, "Diagnóstico do código-fonte", totalSteps = 5, detail = "Inspecionando código")

            // Process Zip e Análise Estática
            val scanResult = try {
                withContext(Dispatchers.IO) {
                    ZipProcessor.processZipUri(getApplication(), uri, cleanFileName)
                }
            } catch (e: Exception) {
                val err = "Erro ao processar arquivo ZIP: ${e.localizedMessage ?: "Arquivo inválido"}"
                _uiState.value = _uiState.value.copy(
                    isProcessingZip = false,
                    buildStatus = BuildStatus.FAILURE,
                    step1Status = StepStatus.FAILURE,
                    step1Detail = err,
                    buildErrorMessage = err
                )
                notificationHelper.notifyFailure(cleanFileName, "Falha ao preparar o projeto")
                withContext(Dispatchers.IO) {
                    compilationDao.updateCompilation(recordEntity.copy(id = recordId, status = "FAILURE", errorMessage = err))
                }
                return@launch
            }

            // Diagnóstico concluído
            _uiState.value = _uiState.value.copy(
                zipScan = scanResult,
                diagnosticReport = scanResult.diagnosticReport,
                isProcessingZip = false,
                step1Status = StepStatus.SUCCESS,
                step1Detail = scanResult.diagnosticReport.summaryMessage,
                step2Status = StepStatus.IN_PROGRESS,
                step2Detail = "Estruturando árvore do projeto (${scanResult.files.size} arquivos)..."
            )
            notificationHelper.updateProgress(
                cleanFileName,
                1,
                "Diagnóstico do código-fonte",
                totalSteps = 5,
                detail = scanResult.diagnosticReport.summaryMessage
            )

            // Repositório selecionado ou criação automática
            var repoFullName = _uiState.value.selectedRepoFullName
            if (repoFullName.isBlank()) {
                if (_uiState.value.userRepos.isNotEmpty()) {
                    repoFullName = _uiState.value.userRepos.first().fullName
                    storage.selectedRepo = repoFullName
                    _uiState.value = _uiState.value.copy(selectedRepoFullName = repoFullName)
                } else {
                    try {
                        val newRepo = withContext(Dispatchers.IO) {
                            apiClient.api.createRepo(
                                CreateRepoRequest(
                                    name = "git-compile-runner",
                                    description = "Repositório criado pelo GitCompile para compilação",
                                    private = true,
                                    autoInit = true
                                )
                            )
                        }
                        repoFullName = newRepo.fullName
                        storage.selectedRepo = repoFullName
                        storage.targetBranch = newRepo.defaultBranch.ifBlank { "main" }
                        _uiState.value = _uiState.value.copy(
                            selectedRepoFullName = repoFullName,
                            targetBranch = newRepo.defaultBranch.ifBlank { "main" }
                        )
                    } catch (e: Exception) {
                        val err = "Falha ao configurar repositório no GitHub: ${extractErrorMessage(e)}"
                        _uiState.value = _uiState.value.copy(
                            buildStatus = BuildStatus.FAILURE,
                            step2Status = StepStatus.FAILURE,
                            step2Detail = err,
                            buildErrorMessage = err
                        )
                        notificationHelper.notifyFailure(cleanFileName, "Erro no repositório")
                        withContext(Dispatchers.IO) {
                            compilationDao.updateCompilation(recordEntity.copy(id = recordId, status = "FAILURE", errorMessage = err))
                        }
                        return@launch
                    }
                }
            }

            // Atualiza nome do repositório no registro
            withContext(Dispatchers.IO) {
                compilationDao.updateCompilation(
                    recordEntity.copy(
                        id = recordId,
                        repoFullName = repoFullName,
                        diagnosticSummary = scanResult.diagnosticReport.summaryMessage
                    )
                )
            }

            // Etapa 2 concluída com sucesso
            _uiState.value = _uiState.value.copy(
                step2Status = StepStatus.SUCCESS,
                step2Detail = "Projeto preparado (${scanResult.files.size} arquivos e CI universal)."
            )
            notificationHelper.updateProgress(cleanFileName, 2, "Preparação do projeto", totalSteps = 5, detail = "Estruturação dos arquivos concluída")

            // Executa compilação real
            executeCompilation(scanResult, repoFullName, _uiState.value.targetBranch, recordId)
        }
    }

    private fun executeCompilation(scan: ZipScanResult, repoFullName: String, branch: String, recordId: Long) {
        val parts = repoFullName.split("/")
        if (parts.size != 2) {
            val err = "Nome de repositório inválido: $repoFullName"
            _uiState.value = _uiState.value.copy(
                buildStatus = BuildStatus.FAILURE,
                step3Status = StepStatus.FAILURE,
                step3Detail = err,
                buildErrorMessage = err
            )
            viewModelScope.launch(Dispatchers.IO) {
                compilationDao.updateCompilation(CompilationEntity(id = recordId, fileName = scan.fileName, status = "FAILURE", errorMessage = err, repoFullName = repoFullName))
            }
            return
        }

        val owner = parts[0].trim()
        val repo = parts[1].trim()
        val effectiveBranch = branch.trim().ifBlank { "main" }
        val fileName = scan.fileName

        compilationPollingJob?.cancel()
        isCurrentlyCompiling = true

        _uiState.value = _uiState.value.copy(
            buildStatus = BuildStatus.UPLOADING,
            step3Status = StepStatus.IN_PROGRESS,
            step3Detail = "Conectando ao GitHub ($repoFullName)...",
            uploadProgress = 0.05f
        )
        // Atualiza notificação: ETAPA 3
        notificationHelper.updateProgress(fileName, 3, "Envio do código-fonte", totalSteps = 5, detail = "Conectando ao GitHub")

        compilationPollingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                appendLog("Conectando ao repositório GitHub ($repoFullName)...")
                val repoDetails = apiClient.api.getRepo(owner, repo)
                val defaultBranch = repoDetails.defaultBranch.ifBlank { "main" }
                val targetBranch = if (effectiveBranch.isBlank()) defaultBranch else effectiveBranch

                // Step 3.1: Check parent commit
                var parentCommitSha: String? = null
                var branchExists = false
                try {
                    val branchRefResp = apiClient.api.getBranchRef(owner, repo, targetBranch)
                    if (branchRefResp.isSuccessful && branchRefResp.body()?.obj != null) {
                        branchExists = true
                        parentCommitSha = branchRefResp.body()?.obj?.sha
                    }
                } catch (_: Exception) {
                    branchExists = false
                }

                if (!branchExists && targetBranch != defaultBranch) {
                    try {
                        val defRefResp = apiClient.api.getBranchRef(owner, repo, defaultBranch)
                        if (defRefResp.isSuccessful && defRefResp.body()?.obj != null) {
                            parentCommitSha = defRefResp.body()?.obj?.sha
                        }
                    } catch (_: Exception) { }
                }

                // Step 3.2: Upload blobs
                val totalFiles = scan.files.size
                appendLog("Enviando $totalFiles arquivos para o GitHub...")
                val treeItems = mutableListOf<GitTreeItem>()

                for ((index, file) in scan.files.withIndex()) {
                    if (file.isText && file.bytes.size < 60 * 1024) {
                        val contentString = String(file.bytes, Charsets.UTF_8)
                        treeItems.add(
                            GitTreeItem(
                                path = file.path,
                                mode = "100644",
                                type = "blob",
                                content = contentString
                            )
                        )
                    } else {
                        val b64 = Base64.encodeToString(file.bytes, Base64.NO_WRAP)
                        val blobResp = apiClient.api.createBlob(owner, repo, GitBlobRequest(content = b64))
                        treeItems.add(
                            GitTreeItem(
                                path = file.path,
                                mode = "100644",
                                type = "blob",
                                sha = blobResp.sha
                            )
                        )
                    }

                    val pct = ((index + 1) * 100 / totalFiles)
                    val progressFloat = 0.05f + (0.75f * (index + 1) / totalFiles)
                    _uiState.value = _uiState.value.copy(
                        uploadProgress = progressFloat,
                        step3Detail = "Enviando arquivos ($pct%): ${index + 1} de $totalFiles"
                    )

                    if (index % 12 == 0 || index == totalFiles - 1) {
                        notificationHelper.updateProgress(fileName, 3, "Envio do código-fonte ($pct%)", totalSteps = 5, detail = "${index + 1} de $totalFiles arquivos")
                    }
                }

                // Step 3.3: Create Git Tree
                appendLog("Criando árvore de arquivos Git...")
                val treeResponse = apiClient.api.createTree(
                    owner,
                    repo,
                    GitTreeRequest(tree = treeItems)
                )

                // Step 3.4: Create Commit
                appendLog("Registrando commit de compilação...")
                val commitMsg = "Compilação de ${scan.fileName} via GitCompile [${SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date())}]"
                val commitResponse = apiClient.api.createCommit(
                    owner,
                    repo,
                    GitCommitRequest(
                        message = commitMsg,
                        tree = treeResponse.sha,
                        parents = if (parentCommitSha != null) listOf(parentCommitSha) else emptyList()
                    )
                )

                // Step 3.5: Update branch
                appendLog("Atualizando ramo '$targetBranch'...")
                var refUpdated = false
                if (branchExists) {
                    try {
                        apiClient.api.updateBranchRef(
                            owner,
                            repo,
                            targetBranch,
                            GitUpdateRefRequest(sha = commitResponse.sha, force = true)
                        )
                        refUpdated = true
                    } catch (_: Exception) { }
                }

                if (!refUpdated) {
                    try {
                        apiClient.api.createRef(
                            owner,
                            repo,
                            GitCreateRefRequest(ref = "refs/heads/$targetBranch", sha = commitResponse.sha)
                        )
                    } catch (_: Exception) {
                        apiClient.api.updateBranchRef(
                            owner,
                            repo,
                            targetBranch,
                            GitUpdateRefRequest(sha = commitResponse.sha, force = true)
                        )
                    }
                }

                // Step 3 Concluída com sucesso -> Inicia Step 4
                _uiState.value = _uiState.value.copy(
                    step3Status = StepStatus.SUCCESS,
                    step3Detail = "Código enviado com sucesso para o GitHub.",
                    uploadProgress = 1.0f,
                    buildStatus = BuildStatus.QUEUED,
                    step4Status = StepStatus.IN_PROGRESS,
                    step4Detail = "Aguardando executor virtual do GitHub Actions..."
                )
                // Atualiza notificação: ETAPA 4
                notificationHelper.updateProgress(fileName, 4, "Compilação no GitHub Actions", totalSteps = 5, detail = "Iniciando executor virtual...")

                // Dispara workflow
                try {
                    apiClient.api.dispatchWorkflow(
                        owner,
                        repo,
                        "compile.yml",
                        WorkflowDispatchRequest(ref = targetBranch)
                    )
                } catch (_: Exception) { }

                // Step 4: Monitora em tempo real
                pollWorkflow(owner, repo, targetBranch, commitResponse.sha, fileName, recordId)

            } catch (e: Exception) {
                isCurrentlyCompiling = false
                val err = "Erro no envio do código: ${extractErrorMessage(e)}"
                appendLog("❌ $err")
                _uiState.value = _uiState.value.copy(
                    buildStatus = BuildStatus.FAILURE,
                    step3Status = StepStatus.FAILURE,
                    step3Detail = err,
                    buildErrorMessage = err
                )
                notificationHelper.notifyFailure(fileName, "Falha no envio do código")
                compilationDao.updateCompilation(
                    CompilationEntity(
                        id = recordId,
                        fileName = fileName,
                        status = "FAILURE",
                        errorMessage = err,
                        repoFullName = repoFullName,
                        branch = effectiveBranch
                    )
                )
            }
        }
    }

    private suspend fun pollWorkflow(
        owner: String,
        repo: String,
        branch: String,
        commitSha: String,
        fileName: String,
        recordId: Long
    ) {
        var foundRun: WorkflowRun? = null
        var attempts = 0
        val maxWaitAttempts = 35 // ~75-80 segundos
        var lastDispatch = 0

        while (foundRun == null && attempts < maxWaitAttempts && currentCoroutineContext().isActive) {
            attempts++
            delay(2200)
            try {
                val runsResp = apiClient.api.getWorkflowRuns(owner, repo, branch = null, perPage = 10)
                val matching = runsResp.workflowRuns.firstOrNull { it.headSha == commitSha }
                    ?: runsResp.workflowRuns.firstOrNull { it.headBranch == branch }
                    ?: runsResp.workflowRuns.firstOrNull()

                if (matching != null) {
                    foundRun = matching
                    _uiState.value = _uiState.value.copy(
                        activeRun = matching,
                        step4Detail = "GitHub Actions #${matching.runNumber}: em andamento"
                    )
                    notificationHelper.updateProgress(
                        fileName,
                        4,
                        "Compilação do APK (#${matching.runNumber})",
                        totalSteps = 5,
                        detail = "Executando no GitHub Actions..."
                    )
                    // Atualiza no banco
                    compilationDao.updateCompilation(
                        CompilationEntity(
                            id = recordId,
                            fileName = fileName,
                            status = "IN_PROGRESS",
                            repoFullName = "$owner/$repo",
                            branch = branch,
                            runId = matching.id,
                            runNumber = matching.runNumber,
                            htmlUrl = matching.htmlUrl
                        )
                    )
                } else {
                    if (attempts >= 4 && attempts - lastDispatch >= 4) {
                        lastDispatch = attempts
                        try {
                            apiClient.api.dispatchWorkflow(owner, repo, "compile.yml", WorkflowDispatchRequest(ref = branch))
                        } catch (_: Exception) { }
                    }
                }
            } catch (e: Exception) {
                appendLog("Verificando status: ${extractErrorMessage(e)}")
            }
        }

        if (foundRun == null) {
            isCurrentlyCompiling = false
            val err = "O GitHub Actions não iniciou dentro do prazo esperado. Verifique se as Actions estão ativadas no repositório."
            appendLog("❌ $err")
            _uiState.value = _uiState.value.copy(
                buildStatus = BuildStatus.FAILURE,
                step4Status = StepStatus.FAILURE,
                step4Detail = "Tempo limite excedido no GitHub Actions.",
                buildErrorMessage = err
            )
            notificationHelper.notifyFailure(fileName, "Timeout no GitHub Actions")
            compilationDao.updateCompilation(
                CompilationEntity(
                    id = recordId,
                    fileName = fileName,
                    status = "FAILURE",
                    errorMessage = err,
                    repoFullName = "$owner/$repo",
                    branch = branch
                )
            )
            return
        }

        val runId = foundRun.id
        var isCompleted = false
        var pollCounter = 0

        while (!isCompleted && currentCoroutineContext().isActive) {
            pollCounter++
            try {
                // 1. Obtém o estado real atualizado do Workflow Run
                val currentRun = apiClient.api.getWorkflowRun(owner, repo, runId)
                val jobsResp = try {
                    apiClient.api.getWorkflowRunJobs(owner, repo, runId)
                } catch (_: Exception) {
                    null
                }
                val jobs = jobsResp?.jobs ?: emptyList()
                val primaryJob = jobs.firstOrNull()

                // Atualiza logs de forma leve periodicamente
                if (pollCounter % 2 == 0 && primaryJob != null) {
                    try {
                        val logs = apiClient.fetchJobLogsText(owner, repo, primaryJob.id)
                        if (logs.isNotBlank() && logs.length > 20) {
                            _uiState.value = _uiState.value.copy(liveLogs = logs)
                        }
                    } catch (_: Exception) { }
                }

                // Detecta o passo atual
                val currentStepName = primaryJob?.steps?.firstOrNull { it.status == "in_progress" }?.name
                    ?: "Executando Gradle no GitHub Actions..."

                // Checagem rigorosa de conclusão (evita que a notificação trave na etapa 4)
                val isFinished = currentRun.status.equals("completed", ignoreCase = true) ||
                        !currentRun.conclusion.isNullOrBlank()

                if (isFinished) {
                    isCompleted = true
                    isCurrentlyCompiling = false

                    val isSuccess = currentRun.conclusion.equals("success", ignoreCase = true)

                    if (isSuccess) {
                        // ETAPA 4 CONCLUÍDA -> ETAPA 5 INICIADA
                        _uiState.value = _uiState.value.copy(
                            activeRun = currentRun,
                            activeJobs = jobs,
                            step4Status = StepStatus.SUCCESS,
                            step4Detail = "Gradle finalizado com sucesso!",
                            step5Status = StepStatus.IN_PROGRESS,
                            step5Detail = "Localizando arquivos e preparando APK..."
                        )

                        // Atualiza notificação: ETAPA 5
                        notificationHelper.updateProgress(
                            fileName,
                            5,
                            "Finalização do APK",
                            totalSteps = 5,
                            detail = "Preparando APK gerado..."
                        )

                        // Busca artefatos
                        var artifactsList: List<ArtifactItem> = emptyList()
                        for (artAttempt in 1..6) {
                            try {
                                val artResp = apiClient.api.getWorkflowArtifacts(owner, repo, runId)
                                if (artResp.artifacts.isNotEmpty()) {
                                    artifactsList = artResp.artifacts
                                    break
                                }
                            } catch (_: Exception) { }
                            delay(1800)
                        }

                        val apkArtifact = artifactsList.firstOrNull { it.name.contains("apk", ignoreCase = true) }
                            ?: artifactsList.firstOrNull()

                        _uiState.value = _uiState.value.copy(
                            buildStatus = BuildStatus.SUCCESS,
                            step5Status = StepStatus.SUCCESS,
                            step5Detail = if (artifactsList.isNotEmpty()) "APK disponível para download e instalação!" else "Compilação concluída no GitHub Actions.",
                            artifacts = artifactsList
                        )

                        // Notificação FINAL de sucesso (cancela a etapa em andamento e emite sucesso)
                        notificationHelper.notifySuccess(fileName, "APK")
                        appendLog("\n🎉 COMPILAÇÃO FINALIZADA COM SUCESSO!")

                        // Atualiza no banco de dados local
                        compilationDao.updateCompilation(
                            CompilationEntity(
                                id = recordId,
                                fileName = fileName,
                                status = "SUCCESS",
                                repoFullName = "$owner/$repo",
                                branch = branch,
                                runId = currentRun.id,
                                runNumber = currentRun.runNumber,
                                htmlUrl = currentRun.htmlUrl,
                                artifactId = apkArtifact?.id,
                                artifactName = apkArtifact?.name ?: "APK",
                                artifactDownloadUrl = apkArtifact?.archiveDownloadUrl,
                                diagnosticSummary = _uiState.value.diagnosticReport?.summaryMessage
                            )
                        )
                    } else {
                        // Falha na compilação
                        val failReason = currentRun.conclusion ?: "falha na execução"
                        _uiState.value = _uiState.value.copy(
                            buildStatus = BuildStatus.FAILURE,
                            activeRun = currentRun,
                            activeJobs = jobs,
                            step4Status = StepStatus.FAILURE,
                            step4Detail = "Compilação falhou no Gradle ($failReason).",
                            step5Status = StepStatus.PENDING,
                            step5Detail = "Cancelado devido a falha na compilação.",
                            buildErrorMessage = "A compilação do APK falhou no GitHub Actions. Verifique os logs do erro e o diagnóstico para mais detalhes."
                        )

                        // Notificação FINAL de falha (cancela a etapa em andamento e emite falha)
                        notificationHelper.notifyFailure(fileName, "Falha no Gradle ($failReason)")
                        appendLog("\n❌ COMPILAÇÃO FALHOU NO GITHUB ACTIONS.")

                        compilationDao.updateCompilation(
                            CompilationEntity(
                                id = recordId,
                                fileName = fileName,
                                status = "FAILURE",
                                repoFullName = "$owner/$repo",
                                branch = branch,
                                runId = currentRun.id,
                                runNumber = currentRun.runNumber,
                                htmlUrl = currentRun.htmlUrl,
                                errorMessage = "Compilação falhou no Gradle: $failReason",
                                diagnosticSummary = _uiState.value.diagnosticReport?.summaryMessage
                            )
                        )
                    }
                } else {
                    // Ainda em andamento: atualiza a notificação com o passo em tempo real
                    if (currentRun.status.equals("in_progress", ignoreCase = true)) {
                        _uiState.value = _uiState.value.copy(
                            buildStatus = BuildStatus.IN_PROGRESS,
                            activeRun = currentRun,
                            activeJobs = jobs,
                            step4Status = StepStatus.IN_PROGRESS,
                            step4Detail = currentStepName
                        )
                        notificationHelper.updateProgress(
                            fileName,
                            4,
                            "Compilação do APK",
                            totalSteps = 5,
                            detail = currentStepName
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            buildStatus = BuildStatus.QUEUED,
                            activeRun = currentRun,
                            activeJobs = jobs,
                            step4Status = StepStatus.IN_PROGRESS,
                            step4Detail = "Na fila de execução do GitHub Actions..."
                        )
                        notificationHelper.updateProgress(
                            fileName,
                            4,
                            "Fila do GitHub Actions",
                            totalSteps = 5,
                            detail = "Aguardando executor virtual..."
                        )
                    }
                }

                if (isCompleted) break

            } catch (_: Exception) { }
            delay(2600)
        }
    }

    private fun appendLog(line: String) {
        val current = _uiState.value.liveLogs
        _uiState.value = _uiState.value.copy(liveLogs = if (current.isEmpty()) line else "$current\n$line")
    }

    /**
     * Baixa o APK da compilação atual.
     */
    fun downloadLatestApk(context: Context) {
        val state = _uiState.value
        val fileName = state.selectedFileName.ifBlank { "app.apk" }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloadingApk = true,
                downloadProgress = 0f,
                downloadErrorMessage = null
            )

            var artifactToDownload = state.artifacts.firstOrNull { it.name.contains("apk", ignoreCase = true) }
                ?: state.artifacts.firstOrNull()

            if (artifactToDownload == null && state.activeRun != null) {
                val parts = state.selectedRepoFullName.split("/")
                if (parts.size == 2) {
                    try {
                        val artResp = apiClient.api.getWorkflowArtifacts(parts[0], parts[1], state.activeRun.id)
                        if (artResp.artifacts.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(artifacts = artResp.artifacts)
                            artifactToDownload = artResp.artifacts.firstOrNull()
                        }
                    } catch (_: Exception) { }
                }
            }

            if (artifactToDownload != null) {
                val file = ArtifactDownloader.downloadAndExtractApk(
                    getApplication(),
                    apiClient,
                    artifactToDownload
                ) { progress ->
                    _uiState.value = _uiState.value.copy(downloadProgress = progress)
                }

                if (file != null && file.exists() && file.length() > 0L) {
                    _uiState.value = _uiState.value.copy(
                        isDownloadingApk = false,
                        downloadedApkFile = file,
                        downloadErrorMessage = null
                    )
                    // Atualiza caminho local no registro se disponível
                    state.currentRecordId?.let { recId ->
                        compilationDao.getCompilationById(recId)?.let { entity ->
                            compilationDao.updateCompilation(entity.copy(localApkPath = file.absolutePath))
                        }
                    }
                    ArtifactDownloader.installOrOpenApk(context, file)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDownloadingApk = false,
                        downloadErrorMessage = "Falha ao baixar o arquivo. Abrindo página de download..."
                    )
                    openRunInBrowser(context)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isDownloadingApk = false,
                    downloadErrorMessage = "Abrindo página do GitHub Actions para download..."
                )
                openRunInBrowser(context)
            }
        }
    }

    /**
     * Baixa o APK de um item do histórico de compilações.
     */
    fun downloadApkForRecord(context: Context, record: CompilationEntity) {
        // Se já foi baixado e existe localmente, abre direto
        if (!record.localApkPath.isNullOrBlank()) {
            val local = File(record.localApkPath)
            if (local.exists() && local.length() > 0L) {
                ArtifactDownloader.installOrOpenApk(context, local)
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadingRecordId = record.id,
                downloadRecordProgress = 0f
            )

            val repoFullName = record.repoFullName.ifBlank { _uiState.value.selectedRepoFullName }
            val parts = repoFullName.split("/")
            if (parts.size != 2 || record.runId == null) {
                _uiState.value = _uiState.value.copy(downloadingRecordId = null)
                val url = record.htmlUrl ?: "https://github.com"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return@launch
            }

            try {
                val artResp = apiClient.api.getWorkflowArtifacts(parts[0], parts[1], record.runId)
                val artifact = artResp.artifacts.firstOrNull { it.name.contains("apk", ignoreCase = true) }
                    ?: artResp.artifacts.firstOrNull()

                if (artifact != null) {
                    val file = ArtifactDownloader.downloadAndExtractApk(
                        getApplication(),
                        apiClient,
                        artifact
                    ) { progress ->
                        _uiState.value = _uiState.value.copy(downloadRecordProgress = progress)
                    }

                    if (file != null && file.exists() && file.length() > 0L) {
                        compilationDao.updateCompilation(record.copy(localApkPath = file.absolutePath))
                        ArtifactDownloader.installOrOpenApk(context, file)
                    } else {
                        val url = record.htmlUrl ?: "https://github.com/$repoFullName/actions"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    }
                } else {
                    val url = record.htmlUrl ?: "https://github.com/$repoFullName/actions"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                }
            } catch (e: Exception) {
                val url = record.htmlUrl ?: "https://github.com/$repoFullName/actions"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            } finally {
                _uiState.value = _uiState.value.copy(downloadingRecordId = null)
            }
        }
    }



    fun deleteCompilationRecord(record: CompilationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            compilationDao.deleteCompilation(record)
        }
    }

    fun clearCompilationHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            compilationDao.clearAll()
        }
    }

    fun openRunInBrowser(context: Context) {
        val state = _uiState.value
        val url = state.activeRun?.htmlUrl?.ifBlank { null }
            ?: if (state.selectedRepoFullName.isNotBlank()) "https://github.com/${state.selectedRepoFullName}/actions" else "https://github.com"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun installDownloadedApk(context: Context) {
        val file = _uiState.value.downloadedApkFile ?: return
        ArtifactDownloader.installOrOpenApk(context, file)
    }

    fun cancelCompilation() {
        val state = _uiState.value
        val run = state.activeRun
        val parts = state.selectedRepoFullName.split("/")

        notificationHelper.cancelAll()
        isCurrentlyCompiling = false
        compilationPollingJob?.cancel()

        if (run != null && parts.size == 2) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    apiClient.api.cancelWorkflowRun(parts[0], parts[1], run.id)
                } catch (_: Exception) { }
            }
        }

        state.currentRecordId?.let { recId ->
            viewModelScope.launch(Dispatchers.IO) {
                compilationDao.getCompilationById(recId)?.let { entity ->
                    compilationDao.updateCompilation(entity.copy(status = "CANCELLED"))
                }
            }
        }

        _uiState.value = _uiState.value.copy(
            buildStatus = BuildStatus.CANCELLED,
            step4Status = StepStatus.FAILURE,
            step4Detail = "Compilação cancelada pelo usuário.",
            step5Status = StepStatus.PENDING,
            step5Detail = "Processo interrompido."
        )
    }

    fun resetCompilation() {
        // Se ainda estiver compilando no GitHub Actions, não cancela o processo da nuvem;
        // apenas retorna à tela de seleção no app
        if (!isCurrentlyCompiling) {
            notificationHelper.cancelAll()
        }
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.SELECT_ZIP,
            buildStatus = if (isCurrentlyCompiling) _uiState.value.buildStatus else BuildStatus.IDLE,
            downloadedApkFile = null,
            downloadErrorMessage = null
        )
    }
}
