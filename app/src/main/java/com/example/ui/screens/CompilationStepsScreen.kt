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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                            maxLines = 1
                        )
                        Text(
                            text = if (uiState.selectedRepoFullName.isNotBlank()) uiState.selectedRepoFullName else "GitCompile",
                            fontSize = 11.sp,
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Card agrupado e limpo contendo as 4 etapas
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // ETAPA 1
                    CleanStepRow(
                        stepNumber = 1,
                        title = "Preparação do projeto",
                        status = uiState.step1Status,
                        detail = uiState.step1Detail
                    )

                    CleanStepConnector(isActive = uiState.step1Status == StepStatus.SUCCESS)

                    // ETAPA 2
                    CleanStepRow(
                        stepNumber = 2,
                        title = "Envio do código-fonte",
                        status = uiState.step2Status,
                        detail = uiState.step2Detail
                    )

                    CleanStepConnector(isActive = uiState.step2Status == StepStatus.SUCCESS)

                    // ETAPA 3
                    CleanStepRow(
                        stepNumber = 3,
                        title = "Compilação do APK",
                        status = uiState.step3Status,
                        detail = uiState.step3Detail
                    )

                    CleanStepConnector(isActive = uiState.step3Status == StepStatus.SUCCESS)

                    // ETAPA 4
                    CleanStepRow(
                        stepNumber = 4,
                        title = "Finalização e APK",
                        status = uiState.step4Status,
                        detail = uiState.step4Detail
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Ações principais super limpas baseadas no status
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
                                text = if (pct > 0) "Baixando ($pct%)..." else "Baixando...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (uiState.downloadedApkFile != null) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Instalar APK", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Baixar APK", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                            Text("Novo projeto", fontSize = 13.sp)
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
                        Text("Visualizar logs do erro", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.resetCompilation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tentar novamente", fontSize = 13.sp)
                    }
                }

                BuildStatus.CANCELLED -> {
                    OutlinedButton(
                        onClick = { viewModel.resetCompilation() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Voltar ao início", fontSize = 13.sp)
                    }
                }

                else -> {
                    OutlinedButton(
                        onClick = { viewModel.cancelCompilation() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar compilação", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Modal limpo para visualizar e copiar logs de erro
    if (showErrorLogsDialog) {
        val errorLogs = remember(uiState.liveLogs, uiState.buildErrorMessage) {
            val combined = StringBuilder()
            if (!uiState.buildErrorMessage.isNullOrBlank()) {
                combined.append("MOTIVO:\n").append(uiState.buildErrorMessage).append("\n\n")
            }
            if (uiState.liveLogs.isNotBlank()) {
                combined.append("LOGS DO GITHUB ACTIONS:\n").append(uiState.liveLogs)
            } else {
                combined.append("Nenhum log retornado pelo runner.")
            }
            combined.toString()
        }

        AlertDialog(
            onDismissRequest = { showErrorLogsDialog = false },
            title = {
                Text(
                    text = "Logs de Erro",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
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
                        Toast.makeText(context, "Logs copiados!", Toast.LENGTH_SHORT).show()
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
private fun CleanStepRow(
    stepNumber: Int,
    title: String,
    status: StepStatus,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp),
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

            AnimatedVisibility(visible = detail.isNotBlank() && status != StepStatus.PENDING) {
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun CleanStepConnector(isActive: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 13.dp)
            .width(2.dp)
            .height(20.dp)
            .background(
                if (isActive) Color(0xFF3FB950).copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )
    )
}
