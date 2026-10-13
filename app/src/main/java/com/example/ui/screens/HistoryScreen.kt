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
                    Column {
                        Text(
                            text = "Histórico de Compilações",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = if (rawList.isEmpty()) "Nenhuma compilação" else "${rawList.size} compilações registradas",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Botão para sincronizar com GitHub Actions
                    IconButton(
                        onClick = { viewModel.syncHistoryFromGitHub() },
                        enabled = !uiState.isSyncingHistory,
                        modifier = Modifier.testTag("sync_history_button")
                    ) {
                        if (uiState.isSyncingHistory) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar histórico do GitHub"
                            )
                        }
                    }

                    if (rawList.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Limpar histórico",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            // Filtros rápidos
            if (rawList.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("Todas (${rawList.size})", fontSize = 12.sp) }
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
                            selectedContainerColor = Color(0xFFE8F5E9),
                            selectedLabelColor = Color(0xFF2E7D32)
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == "FAILURE",
                        onClick = { selectedFilter = "FAILURE" },
                        label = {
                            Text(
                                "Falha (${rawList.count { it.status.equals("FAILURE", ignoreCase = true) }})",
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFEBEE),
                            selectedLabelColor = Color(0xFFC62828)
                        )
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
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (rawList.isEmpty()) "Nenhuma compilação realizada" else "Nenhum resultado para este filtro",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (rawList.isEmpty())
                                "Selecione um arquivo ZIP na aba Compilador para gerar seu primeiro APK."
                            else
                                "Alterne para outro filtro para visualizar seus registros.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            lineHeight = 18.sp
                        )

                        if (rawList.isEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.selectTab(AppTab.COMPILER) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Iniciar Nova Compilação")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        CompilationHistoryCard(
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

    // Modal para visualizar erro de uma compilação do histórico
    selectedErrorEntity?.let { entity ->
        AlertDialog(
            onDismissRequest = { selectedErrorEntity = null },
            title = {
                Text(
                    text = "Detalhes do Erro",
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
                        text = "Arquivo: ${entity.fileName}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = entity.errorMessage ?: "Erro desconhecido durante a compilação.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Erro de Compilação", entity.errorMessage ?: "")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Erro copiado!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
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
            title = {
                Text("Limpar Histórico", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Deseja realmente apagar todos os registros do histórico de compilações?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearCompilationHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
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
fun CompilationHistoryCard(
    entity: CompilationEntity,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownloadClick: () -> Unit,
    onViewLogsClick: () -> Unit,
    onOpenGitHubClick: () -> Unit,
    onTrackRunningClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd 'de' MMM, HH:mm", Locale("pt", "BR")) }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Linha superior: Nome do arquivo + Badge de status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entity.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Badge de status
                StatusChip(status = entity.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Informações secundárias: Data, Repositório e Ramo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (entity.runNumber != null) {
                    Text(
                        text = " • #${entity.runNumber}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (entity.repoFullName.isNotBlank()) {
                    Text(
                        text = " • ${entity.repoFullName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }

            // Se for falha, exibe prévia do erro
            if (isFailure && !entity.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entity.errorMessage,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 2
                    )
                }
            }

            // Barra de progresso de download caso esteja baixando
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
                        Text(
                            text = "Baixando APK...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$pct%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Linha de ações
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ação principal baseada no status
                if (isSuccess) {
                    Button(
                        onClick = onDownloadClick,
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("download_apk_history_${entity.id}")
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Baixando...", fontSize = 13.sp)
                        } else if (entity.localApkPath != null) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Instalar APK", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Baixar APK", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isInProgress) {
                    Button(
                        onClick = onTrackRunningClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Acompanhar", fontSize = 13.sp)
                    }
                } else if (isFailure) {
                    OutlinedButton(
                        onClick = onViewLogsClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver Erro", fontSize = 13.sp)
                    }
                } else {
                    Box(modifier = Modifier.size(1.dp))
                }

                // Ações secundárias: Abrir no GitHub Actions e Apagar registro
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entity.htmlUrl != null || entity.repoFullName.isNotBlank()) {
                        IconButton(
                            onClick = onOpenGitHubClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Ver no GitHub",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Excluir registro",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (label, bgColor, textColor, icon) = when (status.uppercase()) {
        "SUCCESS" -> Quad(
            "Sucesso",
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            Icons.Default.CheckCircle
        )
        "FAILURE" -> Quad(
            "Falha",
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            Icons.Default.Cancel
        )
        "IN_PROGRESS", "QUEUED" -> Quad(
            "Em andamento",
            Color(0xFFE3F2FD),
            Color(0xFF1565C0),
            Icons.Default.Sync
        )
        "CANCELLED" -> Quad(
            "Cancelado",
            Color(0xFFEEEEEE),
            Color(0xFF616161),
            Icons.Default.Block
        )
        else -> Quad(
            status,
            Color(0xFFEEEEEE),
            Color(0xFF616161),
            Icons.Default.Info
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
