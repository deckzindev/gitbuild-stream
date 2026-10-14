package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.data.db.CompilationEntity
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.CompilerViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: CompilerViewModel,
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedErrorEntity by remember { mutableStateOf<CompilationEntity?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val rawList = uiState.compilationHistory

    val filteredList = remember(rawList, selectedFilter) {
        when (selectedFilter) {
            "SUCCESS" -> rawList.filter { it.status.equals("SUCCESS", ignoreCase = true) }
            "FAILURE" -> rawList.filter { it.status.equals("FAILURE", ignoreCase = true) }
            "IN_PROGRESS" -> rawList.filter {
                it.status.equals("IN_PROGRESS", ignoreCase = true) || it.status.equals("QUEUED", ignoreCase = true)
            }
            else -> rawList
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Histórico",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (rawList.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${rawList.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncHistoryFromGitHub() },
                        enabled = !uiState.isSyncingHistory,
                        modifier = Modifier.testTag("sync_history_button")
                    ) {
                        if (uiState.isSyncingHistory) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar"
                            )
                        }
                    }

                    if (rawList.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Limpar histórico"
                            )
                        }
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
        ) {
            // Filtros rápidos limpos
            if (rawList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("Todas (${rawList.size})", fontSize = 12.sp) },
                        shape = RoundedCornerShape(20.dp)
                    )
                    FilterChip(
                        selected = selectedFilter == "SUCCESS",
                        onClick = { selectedFilter = "SUCCESS" },
                        label = {
                            Text(
                                "Sucesso (${rawList.count { it.status.equals("SUCCESS", ignoreCase = true) }})",
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2E7D32).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF3FB950)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    FilterChip(
                        selected = selectedFilter == "FAILURE",
                        onClick = { selectedFilter = "FAILURE" },
                        label = {
                            Text(
                                "Falhas (${rawList.count { it.status.equals("FAILURE", ignoreCase = true) }})",
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (rawList.isEmpty()) "Histórico vazio" else "Nenhum resultado",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (rawList.isEmpty())
                                "As compilações que você realizar aparecerão aqui com status e download do APK."
                            else
                                "Nenhuma compilação encontrada para o filtro selecionado.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        if (rawList.isEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.selectTab(AppTab.COMPILER) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Nova Compilação", fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        CleanHistoryCard(
                            entity = item,
                            isDownloading = uiState.downloadingRecordId == item.id,
                            downloadProgress = if (uiState.downloadingRecordId == item.id) uiState.downloadRecordProgress else 0f,
                            onDownloadClick = {
                                viewModel.downloadApkForRecord(context, item)
                            },
                            onViewLogsClick = {
                                selectedErrorEntity = item
                            },
                            onOpenGitHubClick = {
                                val url = item.htmlUrl ?: if (item.repoFullName.isNotBlank())
                                    "https://github.com/${item.repoFullName}/actions"
                                else "https://github.com"
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            },
                            onTrackRunningClick = {
                                viewModel.selectTab(AppTab.COMPILER)
                            },
                            onDeleteClick = {
                                viewModel.deleteCompilationRecord(item)
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal para visualizar erro
    selectedErrorEntity?.let { entity ->
        AlertDialog(
            onDismissRequest = { selectedErrorEntity = null },
            title = {
                Text(
                    text = "Logs do Erro",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entity.fileName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = entity.errorMessage ?: "Erro desconhecido durante a compilação.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 15.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Erro", entity.errorMessage ?: "")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Erro copiado!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Copiar")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedErrorEntity = null }) {
                    Text("Fechar")
                }
            }
        )
    }

    // Confirmação para limpar todo o histórico
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Limpar Histórico", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja apagar todos os registros do histórico?") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearCompilationHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Limpar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CleanHistoryCard(
    entity: CompilationEntity,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownloadClick: () -> Unit,
    onViewLogsClick: () -> Unit,
    onOpenGitHubClick: () -> Unit,
    onTrackRunningClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("pt", "BR")) }
    val formattedDate = remember(entity.timestamp) { dateFormat.format(Date(entity.timestamp)) }

    val isSuccess = entity.status.equals("SUCCESS", ignoreCase = true)
    val isFailure = entity.status.equals("FAILURE", ignoreCase = true)
    val isInProgress = entity.status.equals("IN_PROGRESS", ignoreCase = true) ||
            entity.status.equals("QUEUED", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${entity.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Linha superior: Nome do arquivo + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderZip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = entity.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                CleanStatusBadge(status = entity.status)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Informações secundárias limpas
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entity.runNumber != null) {
                    Text(
                        text = "• #${entity.runNumber}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entity.branch.isNotBlank()) {
                    Text(
                        text = "• ${entity.branch}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }

            // Barra de download sutil integrada
            AnimatedVisibility(visible = isDownloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    val pct = (downloadProgress * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Baixando...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text(text = "$pct%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Ações limpas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSuccess) {
                    Button(
                        onClick = onDownloadClick,
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("download_apk_history_${entity.id}")
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Baixando", fontSize = 12.5.sp)
                        } else if (entity.localApkPath != null) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Instalar APK", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Baixar APK", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isInProgress) {
                    Button(
                        onClick = onTrackRunningClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Acompanhar", fontSize = 12.5.sp)
                    }
                } else if (isFailure) {
                    OutlinedButton(
                        onClick = onViewLogsClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Erro", fontSize = 12.5.sp)
                    }
                } else {
                    Box(modifier = Modifier.size(1.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (entity.htmlUrl != null || entity.repoFullName.isNotBlank()) {
                        IconButton(
                            onClick = onOpenGitHubClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Ver no GitHub",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Excluir",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanStatusBadge(status: String) {
    val (label, bg, text) = when (status.uppercase()) {
        "SUCCESS" -> Triple("Sucesso", Color(0xFF2E7D32).copy(alpha = 0.15f), Color(0xFF3FB950))
        "FAILURE" -> Triple("Falha", MaterialTheme.colorScheme.error.copy(alpha = 0.15f), MaterialTheme.colorScheme.error)
        "IN_PROGRESS", "QUEUED" -> Triple("Em andamento", MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary)
        else -> Triple("Cancelado", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
