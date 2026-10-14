package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.CompilerViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch

enum class LogFilter {
    ALL,
    ERRORS,
    WARNINGS,
    GRADLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilationLogsScreen(
    viewModel: CompilerViewModel,
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var autoScroll by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }

    BackHandler {
        viewModel.navigateTo(AppScreen.COMPILATION_STEPS)
    }

    val allLines = remember(uiState.liveLogs) {
        uiState.liveLogs.lines()
    }

    val filteredLines = remember(allLines, selectedFilter, searchQuery) {
        allLines.filterIndexed { _, line ->
            val matchesFilter = when (selectedFilter) {
                LogFilter.ALL -> true
                LogFilter.ERRORS -> {
                    val lower = line.lowercase()
                    lower.contains("error") || lower.contains("fail") || lower.contains("fatal") || lower.contains("exception")
                }
                LogFilter.WARNINGS -> {
                    val lower = line.lowercase()
                    lower.contains("warn") || lower.contains("warning")
                }
                LogFilter.GRADLE -> {
                    val lower = line.lowercase()
                    lower.contains("gradle") || lower.contains("task") || lower.startsWith("> task") || lower.contains("compiling")
                }
            }

            val matchesSearch = if (searchQuery.isBlank()) true else line.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    // Auto scroll when lines update
    LaunchedEffect(filteredLines.size, autoScroll) {
        if (autoScroll && filteredLines.isNotEmpty()) {
            listState.animateScrollToItem(filteredLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selectedTabIndex == 0) "Logs do Terminal" else "Diagnóstico do Código",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (uiState.activeRun != null)
                                "Execução #${uiState.activeRun.runNumber} • ${filteredLines.size} linhas"
                            else
                                "${uiState.selectedFileName.ifBlank { uiState.selectedRepoFullName }} • ${filteredLines.size} linhas",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.COMPILATION_STEPS) },
                        modifier = Modifier.testTag("logs_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar para as Etapas")
                    }
                },
                actions = {
                    if (selectedTabIndex == 0) {
                        // Search Toggle
                        IconButton(onClick = { showSearchField = !showSearchField }) {
                            Icon(Icons.Default.Search, contentDescription = "Pesquisar")
                        }
                    }

                    // Copy All Logs
                    IconButton(
                        onClick = {
                            val contentToCopy = if (selectedTabIndex == 0) {
                                uiState.liveLogs
                            } else {
                                buildDiagnosticSummaryString(uiState)
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Logs de Compilação", contentToCopy)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Conteúdo copiado com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_logs_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Conteúdo")
                    }

                    // Share
                    IconButton(
                        onClick = {
                            val contentToShare = if (selectedTabIndex == 0) {
                                uiState.liveLogs
                            } else {
                                buildDiagnosticSummaryString(uiState)
                            }
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, contentToShare)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Compartilhar")
                            context.startActivity(shareIntent)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Compartilhar")
                    }

                    // Open in GitHub Actions
                    if (uiState.selectedRepoFullName.isNotBlank()) {
                        val actionsUrl = uiState.activeRun?.htmlUrl?.ifBlank { null }
                            ?: "https://github.com/${uiState.selectedRepoFullName}/actions"
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(actionsUrl))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Abrir no GitHub Actions")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = {
                        autoScroll = !autoScroll
                        if (!autoScroll && filteredLines.isNotEmpty()) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(filteredLines.size - 1)
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = if (autoScroll) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (autoScroll) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(
                        imageVector = if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.Pause,
                        contentDescription = if (autoScroll) "Auto-Scroll Ativo" else "Auto-Scroll Pausado"
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Abas Super Clean entre "Logs do Terminal" e "Diagnóstico"
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Terminal (${filteredLines.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        val fixes = uiState.diagnosticReport?.autoFixesCount ?: 0
                        Text(if (fixes > 0) "Diagnóstico ($fixes)" else "Diagnóstico", fontWeight = FontWeight.Bold)
                    },
                    icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (selectedTabIndex == 0) {
                // Aba 1: Terminal
                // Search Input Field (Collapsible)
                AnimatedVisibility(visible = showSearchField) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filtrar texto nos logs...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == LogFilter.ALL,
                        onClick = { selectedFilter = LogFilter.ALL },
                        label = { Text("Todos (${allLines.size})", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == LogFilter.ERRORS,
                        onClick = { selectedFilter = LogFilter.ERRORS },
                        label = { Text("Erros", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GitHubRedAccent.copy(alpha = 0.2f),
                            selectedLabelColor = GitHubRedAccent
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == LogFilter.WARNINGS,
                        onClick = { selectedFilter = LogFilter.WARNINGS },
                        label = { Text("Avisos", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GitHubYellow.copy(alpha = 0.2f),
                            selectedLabelColor = GitHubYellow
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == LogFilter.GRADLE,
                        onClick = { selectedFilter = LogFilter.GRADLE },
                        label = { Text("Gradle", fontSize = 12.sp) }
                    )
                }

                // Terminal Screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GitHubDarkBg)
                        .border(1.dp, GitHubBorder, RoundedCornerShape(10.dp))
                ) {
                    if (filteredLines.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = GitHubTextSecondary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Nenhum resultado para a busca." else "Aguardando logs da compilação...",
                                    color = GitHubTextSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            itemsIndexed(filteredLines) { index, line ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}".padStart(4, ' '),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = GitHubTextSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                    LogLineColoredText(line = line)
                                }
                            }
                        }
                    }
                }

                // Terminal Status Footer
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (autoScroll) "• Auto-scroll ativado" else "• Auto-scroll pausado",
                            fontSize = 11.sp,
                            color = if (autoScroll) GitHubGreenAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        TextButton(
                            onClick = { viewModel.navigateTo(AppScreen.COMPILATION_STEPS) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ver Etapas", fontSize = 11.5.sp)
                        }
                    }
                }
            } else {
                // Aba 2: Diagnóstico Completo do Projeto
                val report = uiState.diagnosticReport
                if (report == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nenhum relatório de diagnóstico gerado ainda.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Card Resumo
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Resumo da Inspeção Estática",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = report.summaryMessage,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SummaryBadge(label = "Arquivos", count = "${report.totalFilesAnalyzed}")
                                    SummaryBadge(label = "Correções", count = "${report.autoFixesCount}", color = Color(0xFF2E7D32))
                                    SummaryBadge(label = "Avisos", count = "${report.warningsCount}", color = Color(0xFFE65100))
                                    if (report.errorsCount > 0) {
                                        SummaryBadge(label = "Erros", count = "${report.errorsCount}", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        // Lista de Correções
                        if (report.fixedIssues.isNotEmpty()) {
                            Text(
                                text = "PROBLEMAS CORRIGIDOS AUTOMATICAMENTE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                            report.fixedIssues.forEach { issue ->
                                DiagnosticItemCard(
                                    title = issue.title,
                                    filePath = issue.filePath,
                                    description = issue.fixAction ?: issue.description,
                                    badgeText = "Auto-Fix",
                                    badgeColor = Color(0xFF2E7D32),
                                    icon = Icons.Default.CheckCircle
                                )
                            }
                        }

                        // Lista de Avisos
                        if (report.warningIssues.isNotEmpty()) {
                            Text(
                                text = "AVISOS & RECOMENDAÇÕES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            report.warningIssues.forEach { issue ->
                                DiagnosticItemCard(
                                    title = issue.title,
                                    filePath = issue.filePath,
                                    description = issue.description + if (!issue.suggestedSolution.isNullOrBlank()) "\nSugestão: ${issue.suggestedSolution}" else "",
                                    badgeText = "Aviso",
                                    badgeColor = Color(0xFFFFA000),
                                    icon = Icons.Default.Warning
                                )
                            }
                        }

                        // Lista de Erros
                        if (report.errorIssues.isNotEmpty()) {
                            Text(
                                text = "FALHAS QUE IMPEDEM A COMPILAÇÃO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            report.errorIssues.forEach { issue ->
                                DiagnosticItemCard(
                                    title = issue.title,
                                    filePath = issue.filePath,
                                    description = issue.description + if (!issue.suggestedSolution.isNullOrBlank()) "\nSolução recomendada: ${issue.suggestedSolution}" else "",
                                    badgeText = "Erro",
                                    badgeColor = MaterialTheme.colorScheme.error,
                                    icon = Icons.Default.Error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBadge(label: String, count: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = count, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun DiagnosticItemCard(
    title: String,
    filePath: String?,
    description: String,
    badgeText: String,
    badgeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.padding(top = 2.dp).size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (!filePath.isNullOrBlank()) {
                    Text(
                        text = filePath,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                    lineHeight = 15.sp
                )
            }
        }
    }
}

private fun buildDiagnosticSummaryString(uiState: UiState): String {
    val report = uiState.diagnosticReport ?: return "Nenhum relatório de diagnóstico disponível."
    val sb = StringBuilder()
    sb.append("=== RELATÓRIO DE DIAGNÓSTICO DO PROJETO ===\n")
    sb.append("Projeto: ${uiState.selectedFileName}\n")
    sb.append("Arquivos inspecionados: ${report.totalFilesAnalyzed}\n")
    sb.append("Correções automáticas aplicadas: ${report.autoFixesCount}\n\n")

    if (report.fixedIssues.isNotEmpty()) {
        sb.append("CORREÇÕES EFETUADAS:\n")
        report.fixedIssues.forEach {
            sb.append("- [Auto-Fix] ${it.title} (${it.filePath ?: "Geral"}): ${it.fixAction}\n")
        }
        sb.append("\n")
    }

    if (report.warningIssues.isNotEmpty()) {
        sb.append("AVISOS & RECOMENDAÇÕES:\n")
        report.warningIssues.forEach {
            sb.append("- [Aviso] ${it.title}: ${it.description}\n")
        }
        sb.append("\n")
    }

    if (report.errorIssues.isNotEmpty()) {
        sb.append("FALHAS CRÍTICAS:\n")
        report.errorIssues.forEach {
            sb.append("- [Erro] ${it.title}: ${it.description}\n")
            if (!it.suggestedSolution.isNullOrBlank()) {
                sb.append("  Solução: ${it.suggestedSolution}\n")
            }
        }
    }

    return sb.toString()
}

@Composable
fun LogLineColoredText(line: String) {
    val lower = line.lowercase()
    val textColor = when {
        lower.contains("error") || lower.contains("fail") || lower.contains("fatal") || lower.contains("exception") ->
            GitHubRedAccent
        lower.contains("success") || lower.contains("successful") || lower.contains("concluído") || lower.contains("done") ->
            GitHubGreenAccent
        lower.contains("warning") || lower.contains("warn") ->
            GitHubYellow
        lower.startsWith(">>>") || lower.startsWith("===") || lower.startsWith("> task") || lower.contains("compiling") || lower.contains("building") ->
            GitHubBlueAccent
        else ->
            GitHubTextPrimary
    }

    Text(
        text = line,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.5.sp,
        color = textColor,
        lineHeight = 16.sp
    )
}
