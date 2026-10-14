package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.diagnostic.DiagnosticIssue
import com.example.data.diagnostic.IssueSeverity
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.BuildStatus
import com.example.ui.viewmodel.CompilerViewModel
import com.example.ui.viewmodel.StepStatus
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilationStepsScreen(
    viewModel: CompilerViewModel,
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showErrorLogsDialog by remember { mutableStateOf(false) }

    // Notificações no Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* granted */ }
        LaunchedEffect(Unit) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val isRunning = uiState.buildStatus == BuildStatus.UPLOADING ||
            uiState.buildStatus == BuildStatus.QUEUED ||
            uiState.buildStatus == BuildStatus.IN_PROGRESS

    val fileName = uiState.selectedFileName.ifBlank {
        uiState.zipScan?.fileName ?: "codigo-fonte.zip"
    }

    BackHandler {
        if (isRunning) {
            Toast.makeText(context, "A compilação continua em execução no GitHub Actions.", Toast.LENGTH_SHORT).show()
            viewModel.navigateTo(AppScreen.SELECT_ZIP)
        } else {
            viewModel.resetCompilation()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (uiState.selectedRepoFullName.isNotBlank()) uiState.selectedRepoFullName else "GitCompile Cloud",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isRunning) {
                                Toast.makeText(context, "A compilação continua em execução no GitHub Actions.", Toast.LENGTH_SHORT).show()
                                viewModel.navigateTo(AppScreen.SELECT_ZIP)
                            } else {
                                viewModel.resetCompilation()
                            }
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.COMPILATION_LOGS) },
                        modifier = Modifier.testTag("steps_view_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Ver Logs do Terminal"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Header Banner
            StatusOverviewBanner(
                buildStatus = uiState.buildStatus,
                uploadProgress = uiState.uploadProgress,
                downloadProgress = uiState.downloadProgress,
                isDownloading = uiState.isDownloadingApk,
                onViewLogs = { viewModel.navigateTo(AppScreen.COMPILATION_LOGS) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card Principal do Pipeline de Compilação com as 5 Etapas
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Fluxo de Compilação",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "5 etapas coordenadas em tempo real",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // ETAPA 1: Diagnóstico e Análise do Código
                    PipelineStepRow(
                        stepNumber = 1,
                        title = "Diagnóstico e Análise do Código",
                        status = uiState.step1Status,
                        detail = uiState.step1Detail,
                        badge = if (uiState.diagnosticReport != null && uiState.diagnosticReport.autoFixesCount > 0)
                            "${uiState.diagnosticReport.autoFixesCount} correções" else null
                    )

                    PipelineStepConnector(isActive = uiState.step1Status == StepStatus.SUCCESS)

                    // ETAPA 2: Preparação do Projeto
                    PipelineStepRow(
                        stepNumber = 2,
                        title = "Preparação do Projeto",
                        status = uiState.step2Status,
                        detail = uiState.step2Detail
                    )

                    PipelineStepConnector(isActive = uiState.step2Status == StepStatus.SUCCESS)

                    // ETAPA 3: Envio ao GitHub
                    PipelineStepRow(
                        stepNumber = 3,
                        title = "Envio do Código ao GitHub",
                        status = uiState.step3Status,
                        detail = uiState.step3Detail,
                        progress = if (uiState.step3Status == StepStatus.IN_PROGRESS) uiState.uploadProgress else null
                    )

                    PipelineStepConnector(isActive = uiState.step3Status == StepStatus.SUCCESS)

                    // ETAPA 4: Compilação no Runner Cloud
                    PipelineStepRow(
                        stepNumber = 4,
                        title = "Compilação no GitHub Actions",
                        status = uiState.step4Status,
                        detail = uiState.step4Detail
                    )

                    PipelineStepConnector(isActive = uiState.step4Status == StepStatus.SUCCESS)

                    // ETAPA 5: Finalização e APK
                    PipelineStepRow(
                        stepNumber = 5,
                        title = "Geração e Assinatura do APK",
                        status = uiState.step5Status,
                        detail = uiState.step5Detail
                    )
                }
            }

            // Relatório de Diagnóstico Expansível do Código-Fonte (se houver diagnóstico disponível)
            if (uiState.diagnosticReport != null) {
                Spacer(modifier = Modifier.height(16.dp))
                DiagnosticReportCard(
                    report = uiState.diagnosticReport,
                    isExpanded = uiState.isDiagnosticExpanded,
                    onToggleExpand = { viewModel.toggleDiagnosticExpanded() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Ações do Rodapé Modernas e Intuitivas
            when (uiState.buildStatus) {
                BuildStatus.SUCCESS -> {
                    Button(
                        onClick = {
                            if (uiState.downloadedApkFile != null) {
                                viewModel.installDownloadedApk(context)
                            } else {
                                viewModel.downloadLatestApk(context)
                            }
                        },
                        enabled = !uiState.isDownloadingApk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("download_apk_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        if (uiState.isDownloadingApk) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val pct = (uiState.downloadProgress * 100).toInt()
                            Text(
                                text = if (pct > 0) "Baixando APK ($pct%)..." else "Baixando APK...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (uiState.downloadedApkFile != null) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Instalar APK no Celular", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Baixar APK Gerado", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.navigateTo(AppScreen.COMPILATION_LOGS) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ver Logs", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.openRunInBrowser(context) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GitHub", fontSize = 13.sp)
                        }

                        TextButton(
                            onClick = { viewModel.resetCompilation() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Novo ZIP", fontSize = 13.sp)
                        }
                    }
                }

                BuildStatus.FAILURE -> {
                    Button(
                        onClick = { showErrorLogsDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("view_error_logs_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Ver Diagnóstico & Logs", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.resetCompilation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Selecionar outro ZIP", fontSize = 13.5.sp)
                    }
                }

                BuildStatus.CANCELLED -> {
                    OutlinedButton(
                        onClick = { viewModel.resetCompilation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Voltar ao início", fontSize = 14.sp)
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.navigateTo(AppScreen.COMPILATION_LOGS) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Logs ao vivo", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.cancelCompilation() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Modal para visualizar e copiar logs de erro
    if (showErrorLogsDialog) {
        val errorLogs = remember(uiState.liveLogs, uiState.buildErrorMessage, uiState.diagnosticReport) {
            val combined = StringBuilder()
            if (!uiState.buildErrorMessage.isNullOrBlank()) {
                combined.append("=== CAUSA PRINCIPAL ===\n")
                combined.append(uiState.buildErrorMessage).append("\n\n")
            }
            if (uiState.diagnosticReport != null) {
                combined.append("=== DIAGNÓSTICO DO PROJETO ===\n")
                combined.append("Arquivos analisados: ${uiState.diagnosticReport.totalFilesAnalyzed}\n")
                combined.append("Problemas corrigidos: ${uiState.diagnosticReport.autoFixesCount}\n")
                uiState.diagnosticReport.fixedIssues.forEach {
                    combined.append("  [✓ Auto-Fix] ${it.title}: ${it.fixAction}\n")
                }
                uiState.diagnosticReport.errorIssues.forEach {
                    combined.append("  [✗ Erro] ${it.title}: ${it.description}\n")
                    if (!it.suggestedSolution.isNullOrBlank()) {
                        combined.append("    -> Solução recomendada: ${it.suggestedSolution}\n")
                    }
                }
                combined.append("\n")
            }
            if (uiState.liveLogs.isNotBlank()) {
                combined.append("=== LOGS DO GITHUB ACTIONS ===\n")
                combined.append(uiState.liveLogs)
            } else {
                combined.append("Nenhum log retornado pelo executor virtual.")
            }
            combined.toString()
        }

        AlertDialog(
            onDismissRequest = { showErrorLogsDialog = false },
            title = {
                Text(
                    text = "Diagnóstico & Logs de Erro",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = errorLogs,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Logs de Erro", errorLogs)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Logs copiados para a área de transferência!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar Logs")
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorLogsDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
private fun StatusOverviewBanner(
    buildStatus: BuildStatus,
    uploadProgress: Float,
    downloadProgress: Float,
    isDownloading: Boolean,
    onViewLogs: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = when (buildStatus) {
            BuildStatus.SUCCESS -> Color(0xFF2E7D32).copy(alpha = 0.12f)
            BuildStatus.FAILURE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            BuildStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (buildStatus) {
                BuildStatus.SUCCESS -> Color(0xFF3FB950).copy(alpha = 0.4f)
                BuildStatus.FAILURE -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                BuildStatus.CANCELLED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                when (buildStatus) {
                    BuildStatus.SUCCESS -> {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Compilação Concluída",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "APK Release assinado e pronto para uso",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    BuildStatus.FAILURE -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Falha na Compilação",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Consulte os detalhes e logs de erro abaixo",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    BuildStatus.CANCELLED -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Processo Interrompido",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Cancelado antes da finalização",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = when (buildStatus) {
                                    BuildStatus.UPLOADING -> "Enviando projeto..."
                                    BuildStatus.QUEUED -> "Aguardando executor virtual..."
                                    else -> "Compilando na nuvem..."
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Executando no GitHub Actions",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (buildStatus == BuildStatus.IN_PROGRESS || buildStatus == BuildStatus.UPLOADING || buildStatus == BuildStatus.QUEUED) {
                TextButton(
                    onClick = onViewLogs,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineStepRow(
    stepNumber: Int,
    title: String,
    status: StepStatus,
    detail: String,
    progress: Float? = null,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                StepStatus.IN_PROGRESS -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StepStatus.SUCCESS -> {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Concluído",
                                tint = Color(0xFF3FB950),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                StepStatus.FAILURE -> {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Falha",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                StepStatus.PENDING -> {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$stepNumber. $title",
                    fontWeight = if (status == StepStatus.IN_PROGRESS) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.5.sp,
                    color = when (status) {
                        StepStatus.FAILURE -> MaterialTheme.colorScheme.error
                        StepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = detail.isNotBlank() && status != StepStatus.PENDING) {
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    lineHeight = 16.sp
                )
            }

            if (progress != null && status == StepStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun PipelineStepConnector(isActive: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 13.dp)
            .width(2.dp)
            .height(18.dp)
            .background(
                if (isActive) Color(0xFF3FB950).copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )
    )
}

@Composable
private fun DiagnosticReportCard(
    report: com.example.data.diagnostic.ProjectDiagnosticReport,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ArrowRotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header clicável para expandir/recolher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = Color(0xFF2E7D32).copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Diagnóstico do Código-Fonte",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${report.totalFilesAnalyzed} arquivos inspecionados • ${report.autoFixesCount} correções aplicadas",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Recolher relatório" else "Expandir relatório",
                        modifier = Modifier.rotate(arrowRotation)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 18.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )

                    // Tecnologias detectadas
                    if (report.detectedTechnologies.isNotEmpty()) {
                        Text(
                            text = "TECNOLOGIAS E AMBIENTE DETECTADOS",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            report.detectedTechnologies.forEach { tech ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        text = tech,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Problemas corrigidos automaticamente
                    if (report.fixedIssues.isNotEmpty()) {
                        Text(
                            text = "CORREÇÕES APLICADAS AUTOMATICAMENTE",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            report.fixedIssues.forEach { issue ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Color(0xFF2E7D32).copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF3FB950),
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = issue.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF2E7D32).copy(alpha = 0.1f)
                                                ) {
                                                    Text(
                                                        text = "Auto-Fix",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32),
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (!issue.filePath.isNullOrBlank()) {
                                                Text(
                                                    text = issue.filePath,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 1.dp)
                                                )
                                            }
                                            Text(
                                                text = issue.fixAction ?: issue.description,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 3.dp),
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Avisos informativos
                    if (report.warningIssues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "AVISOS & RECOMENDAÇÕES",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            report.warningIssues.forEach { issue ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Color(0xFFFFA000).copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFFFA000),
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = issue.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = issue.description,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            if (!issue.suggestedSolution.isNullOrBlank()) {
                                                Text(
                                                    text = "Sugestão: ${issue.suggestedSolution}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFFE65100),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Erros não resolvidos (se houver)
                    if (report.errorIssues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "ERROS CRÍTICOS IDENTIFICADOS",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            report.errorIssues.forEach { issue ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = issue.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = issue.description,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            if (!issue.suggestedSolution.isNullOrBlank()) {
                                                Text(
                                                    text = "Solução: ${issue.suggestedSolution}",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(top = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
