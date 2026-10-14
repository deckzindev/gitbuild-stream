package com.example.data.zip

import android.content.Context
import android.net.Uri
import com.example.data.diagnostic.DiagnosticIssue
import com.example.data.diagnostic.IssueSeverity
import com.example.data.diagnostic.ProjectDiagnosticReport
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractedFile(
    val path: String,
    val bytes: ByteArray,
    val isText: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExtractedFile
        return path == other.path
    }

    override fun hashCode(): Int = path.hashCode()
}

data class StandardizationResult(
    val modifiedFilesCount: Int,
    val addedFilesCount: Int,
    val details: List<String>
)

data class ZipScanResult(
    val fileName: String,
    val totalSize: Long,
    val files: List<ExtractedFile>,
    val detectedTechnologies: List<String>,
    val hadOriginalWorkflows: Boolean,
    val workflowStandardized: Boolean = true,
    val standardizationResult: StandardizationResult = StandardizationResult(0, 0, emptyList()),
    val diagnosticReport: ProjectDiagnosticReport
)

object ZipProcessor {

    private const val MAX_FILE_SIZE_BYTES = 12 * 1024 * 1024 // 12MB per file
    private const val MAX_TOTAL_FILES = 1200

    // Constantes de padronização universal estável
    const val TARGET_GRADLE_VERSION = "8.7"
    const val TARGET_GRADLE_DISTRIBUTION_URL = "https\\://services.gradle.org/distributions/gradle-8.7-bin.zip"
    const val TARGET_AGP_VERSION = "8.5.2"
    const val TARGET_KOTLIN_VERSION = "1.9.24"

    fun processZipUri(context: Context, uri: Uri, originalFileName: String = "source.zip"): ZipScanResult {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir o arquivo ZIP selecionado.")

        val rawFiles = mutableListOf<ExtractedFile>()
        var totalBytesRead = 0L

        ZipInputStream(inputStream.buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && !name.contains("__MACOSX") && !name.endsWith(".DS_Store")) {
                    val normalizedPath = normalizePath(name)
                    if (normalizedPath.isNotBlank()) {
                        val baos = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var read: Int
                        var entryBytes = 0L
                        while (zis.read(buffer).also { read = it } != -1) {
                            entryBytes += read
                            if (entryBytes > MAX_FILE_SIZE_BYTES) {
                                break // Arquivo binário muito pesado, pula
                            }
                            baos.write(buffer, 0, read)
                        }
                        val contentBytes = baos.toByteArray()
                        totalBytesRead += contentBytes.size
                        val isText = isLikelyText(contentBytes)
                        rawFiles.add(ExtractedFile(normalizedPath, contentBytes, isText))
                        if (rawFiles.size >= MAX_TOTAL_FILES) {
                            break
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (rawFiles.isEmpty()) {
            throw IllegalStateException("O arquivo ZIP está vazio ou não contém arquivos legíveis.")
        }

        // Se todos os arquivos compartilham uma pasta raiz (ex: NomeProjeto-master/src/...), remove o prefixo redundante
        val cleanedFiles = stripCommonRootDirectory(rawFiles)

        // Verifica se havia workflows originais no ZIP
        val hadOriginalWorkflows = cleanedFiles.any {
            it.path.startsWith(".github/workflows/")
        }

        // Remove workflows antigos conflitantes para garantir execução oficial estável
        val filteredFiles = cleanedFiles.filterNot {
            it.path.startsWith(".github/workflows/")
        }.toMutableList()

        // Executa o MOTOR DE ANÁLISE ESTÁTICA E DIAGNÓSTICO INTELIGENTE
        val (diagnosedFiles, diagnosticReport) = analyzeAndDiagnoseProject(filteredFiles, originalFileName)

        // Injeta o workflow oficial do GitCompile universal com auto-cura em tempo de execução
        val workflowContent = generateUniversalWorkflow()
        diagnosedFiles.add(
            ExtractedFile(
                path = ".github/workflows/compile.yml",
                bytes = workflowContent.toByteArray(Charsets.UTF_8),
                isText = true
            )
        )

        val summaryDetails = diagnosticReport.fixedIssues.map { it.fixAction ?: it.title }

        return ZipScanResult(
            fileName = originalFileName,
            totalSize = totalBytesRead,
            files = diagnosedFiles,
            detectedTechnologies = diagnosticReport.detectedTechnologies,
            hadOriginalWorkflows = hadOriginalWorkflows,
            workflowStandardized = true,
            standardizationResult = StandardizationResult(
                modifiedFilesCount = diagnosticReport.autoFixesCount,
                addedFilesCount = if (diagnosedFiles.size > filteredFiles.size) diagnosedFiles.size - filteredFiles.size else 0,
                details = summaryDetails
            ),
            diagnosticReport = diagnosticReport
        )
    }

    /**
     * Motor completo de inspeção, análise de erros comuns, detecção de falhas e auto-correção segura.
     */
    fun analyzeAndDiagnoseProject(
        inputFiles: List<ExtractedFile>,
        projectName: String
    ): Pair<MutableList<ExtractedFile>, ProjectDiagnosticReport> {
        val workingFiles = inputFiles.toMutableList()
        val detectedIssues = mutableListOf<DiagnosticIssue>()
        var issueCounter = 1

        val detectedTechnologies = detectTechnologies(workingFiles)

        // -------------------------------------------------------------
        // 1. Diagnóstico e Resolução de Recursos Android Duplicados
        // -------------------------------------------------------------
        val (dedupedFiles, removedResourceDetails) = deduplicateAndroidResources(workingFiles)
        workingFiles.clear()
        workingFiles.addAll(dedupedFiles)

        for (removedDetail in removedResourceDetails) {
            detectedIssues.add(
                DiagnosticIssue(
                    id = "RES_${issueCounter++}",
                    title = "Recurso Android duplicado",
                    description = "Dois ou mais arquivos com extensões concorrentes (.png, .webp, .jpg) colidiam na mesma pasta de recursos do Android, o que causaria erro fatal no AAPT / Resource Merger.",
                    filePath = removedDetail.filePath,
                    severity = IssueSeverity.WARNING,
                    isAutoFixable = true,
                    isFixed = true,
                    fixAction = removedDetail.description,
                    suggestedSolution = "Mantenha apenas um formato para cada recurso com o mesmo nome na mesma pasta de densidade."
                )
            )
        }

        // -------------------------------------------------------------
        // 2. Diagnóstico e Validação do AndroidManifest.xml
        // -------------------------------------------------------------
        val manifestIndices = workingFiles.indices.filter { workingFiles[it].path.endsWith("AndroidManifest.xml") }
        for (idx in manifestIndices) {
            val manifestFile = workingFiles[idx]
            val manifestText = String(manifestFile.bytes, Charsets.UTF_8)
            var updatedManifest = manifestText
            var manifestModified = false

            // Verifica se possui activities/services/receivers com intent-filter sem android:exported
            // (Requisito obrigatório no Android 12+ / targetSdk >= 31)
            val intentFilterRegex = Regex("""(<(activity|service|receiver)[\s\S]*?<intent-filter>[\s\S]*?</\2>)""", RegexOption.IGNORE_CASE)
            val matches = intentFilterRegex.findAll(manifestText)

            for (match in matches) {
                val componentBlock = match.value
                val tagOpening = componentBlock.substringBefore(">")
                if (!tagOpening.contains("android:exported")) {
                    // Falha crítica detectada! Auto-correção segura
                    val isLauncher = componentBlock.contains("android.intent.action.MAIN") || componentBlock.contains("android.intent.category.LAUNCHER")
                    val exportedValue = if (isLauncher) "true" else "false"
                    val fixedOpening = tagOpening.trimEnd() + " android:exported=\"$exportedValue\""
                    val fixedBlock = componentBlock.replaceFirst(tagOpening, fixedOpening)
                    updatedManifest = updatedManifest.replace(componentBlock, fixedBlock)
                    manifestModified = true

                    detectedIssues.add(
                        DiagnosticIssue(
                            id = "MAN_${issueCounter++}",
                            title = "android:exported ausente no AndroidManifest",
                            description = "No Android 12 (API 31)+, todo componente com <intent-filter> requer o atributo 'android:exported' explícito para compilar sem erros.",
                            filePath = manifestFile.path,
                            severity = IssueSeverity.ERROR,
                            isAutoFixable = true,
                            isFixed = true,
                            fixAction = "Inserido android:exported=\"$exportedValue\" no componente correspondente.",
                            suggestedSolution = "Declare sempre android:exported=\"true\" ou \"false\" para activities, services e receivers com intent-filter."
                        )
                    )
                }
            }

            // Verifica se a tag <application> existe
            if (!manifestText.contains("<application")) {
                detectedIssues.add(
                    DiagnosticIssue(
                        id = "MAN_${issueCounter++}",
                        title = "Tag <application> não encontrada no manifest",
                        description = "O arquivo AndroidManifest.xml não parece conter a declaração principal de <application>.",
                        filePath = manifestFile.path,
                        severity = IssueSeverity.WARNING,
                        isAutoFixable = false,
                        isFixed = false,
                        suggestedSolution = "Certifique-se de que o AndroidManifest.xml possui <application android:label=\"...\">."
                    )
                )
            }

            if (manifestModified) {
                workingFiles[idx] = manifestFile.copy(bytes = updatedManifest.toByteArray(Charsets.UTF_8))
            }
        }

        // -------------------------------------------------------------
        // 3. Diagnóstico e Compatibilidade do Gradle Wrapper
        // -------------------------------------------------------------
        val wrapperPath = "gradle/wrapper/gradle-wrapper.properties"
        val wrapperFileIndex = workingFiles.indexOfFirst { it.path.endsWith(wrapperPath) }

        if (wrapperFileIndex >= 0) {
            val wrapperFile = workingFiles[wrapperFileIndex]
            val content = String(wrapperFile.bytes, Charsets.UTF_8)
            val distUrlMatch = Regex("""distributionUrl\s*=\s*(.*)""").find(content)
            val currentUrl = distUrlMatch?.groupValues?.getOrNull(1)?.trim() ?: ""

            val isIncompatibleGradle = currentUrl.contains("gradle-9.") ||
                    currentUrl.contains("gradle-6.") ||
                    currentUrl.contains("gradle-7.") ||
                    currentUrl.startsWith("http://")

            if (isIncompatibleGradle || !currentUrl.contains(TARGET_GRADLE_VERSION)) {
                val standardized = standardizeGradleWrapperProperties(content)
                workingFiles[wrapperFileIndex] = wrapperFile.copy(bytes = standardized.toByteArray(Charsets.UTF_8))

                detectedIssues.add(
                    DiagnosticIssue(
                        id = "GRADLE_${issueCounter++}",
                        title = "Versão do Gradle Wrapper instável ou incompatível",
                        description = "O projeto utilizava uma versão de Gradle ($currentUrl) sujeita a quebras de API ou incompatibilidade com o Android Gradle Plugin.",
                        filePath = wrapperFile.path,
                        severity = IssueSeverity.WARNING,
                        isAutoFixable = true,
                        isFixed = true,
                        fixAction = "Gradle Wrapper alinhado para a distribuição estável $TARGET_GRADLE_VERSION.",
                        suggestedSolution = "Utilize Gradle 8.7 para compatibilidade estável com AGP 8.5+ e JDK 17."
                    )
                )
            }
        } else if (workingFiles.any { it.path.endsWith("build.gradle") || it.path.endsWith("build.gradle.kts") }) {
            // Ausência de wrapper em projeto Gradle
            val newWrapperContent = """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=$TARGET_GRADLE_DISTRIBUTION_URL
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""".trimIndent()
            workingFiles.add(ExtractedFile(wrapperPath, newWrapperContent.toByteArray(Charsets.UTF_8), isText = true))
            detectedIssues.add(
                DiagnosticIssue(
                    id = "GRADLE_${issueCounter++}",
                    title = "gradle-wrapper.properties ausente",
                    description = "O projeto Android não possuía o arquivo gradle-wrapper.properties, o que impediria a compilação no runner na nuvem.",
                    filePath = wrapperPath,
                    severity = IssueSeverity.ERROR,
                    isAutoFixable = true,
                    isFixed = true,
                    fixAction = "Criado gradle-wrapper.properties apontando para Gradle $TARGET_GRADLE_VERSION.",
                    suggestedSolution = "Sempre inclua a pasta gradle/wrapper no seu repositório."
                )
            )
        }

        // -------------------------------------------------------------
        // 4. Diagnóstico do Script Executável 'gradlew'
        // -------------------------------------------------------------
        val isGradleProject = workingFiles.any {
            it.path.endsWith("build.gradle") || it.path.endsWith("build.gradle.kts") || it.path.endsWith("settings.gradle")
        }

        if (isGradleProject && workingFiles.none { it.path == "gradlew" }) {
            val gradlewScript = generateDefaultGradlew()
            workingFiles.add(ExtractedFile("gradlew", gradlewScript.toByteArray(Charsets.UTF_8), isText = true))
            detectedIssues.add(
                DiagnosticIssue(
                    id = "GRADLE_${issueCounter++}",
                    title = "Script 'gradlew' ausente no projeto",
                    description = "O executável gradlew não foi encontrado na raiz do projeto.",
                    filePath = "gradlew",
                    severity = IssueSeverity.WARNING,
                    isAutoFixable = true,
                    isFixed = true,
                    fixAction = "Injetado wrapper executável 'gradlew' universal.",
                    suggestedSolution = "Gere o wrapper com 'gradle wrapper' na raiz do seu projeto local."
                )
            )
        }

        // -------------------------------------------------------------
        // 5. Diagnóstico de Flags no gradle.properties
        // -------------------------------------------------------------
        if (isGradleProject) {
            val propsIndex = workingFiles.indexOfFirst { it.path == "gradle.properties" }
            if (propsIndex < 0) {
                val defaultProps = """
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
android.nonTransitiveRClass=true
org.gradle.parallel=true
org.gradle.caching=true
""".trimIndent()
                workingFiles.add(ExtractedFile("gradle.properties", defaultProps.toByteArray(Charsets.UTF_8), isText = true))
                detectedIssues.add(
                    DiagnosticIssue(
                        id = "CONF_${issueCounter++}",
                        title = "gradle.properties ausente",
                        description = "Sem gradle.properties, projetos modernos do Android podem falhar por falta de flags do AndroidX e limites de memória JVM.",
                        filePath = "gradle.properties",
                        severity = IssueSeverity.WARNING,
                        isAutoFixable = true,
                        isFixed = true,
                        fixAction = "Criado gradle.properties com suporte total a AndroidX, Jetifier e memória otimizada.",
                        suggestedSolution = "Adicione gradle.properties com android.useAndroidX=true."
                    )
                )
            } else {
                val propsFile = workingFiles[propsIndex]
                val originalProps = String(propsFile.bytes, Charsets.UTF_8)
                var updatedProps = originalProps
                var modified = false

                if (!updatedProps.contains("android.useAndroidX")) {
                    updatedProps += "\nandroid.useAndroidX=true\n"
                    modified = true
                }
                if (!updatedProps.contains("android.enableJetifier")) {
                    updatedProps += "\nandroid.enableJetifier=true\n"
                    modified = true
                }
                if (!updatedProps.contains("org.gradle.jvmargs")) {
                    updatedProps += "\norg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8\n"
                    modified = true
                }

                if (modified) {
                    workingFiles[propsIndex] = propsFile.copy(bytes = updatedProps.toByteArray(Charsets.UTF_8))
                    detectedIssues.add(
                        DiagnosticIssue(
                            id = "CONF_${issueCounter++}",
                            title = "Flags de compatibilidade AndroidX aplicadas",
                            description = "Injetadas diretivas de compatibilidade e otimização de memória em gradle.properties.",
                            filePath = "gradle.properties",
                            severity = IssueSeverity.INFO,
                            isAutoFixable = true,
                            isFixed = true,
                            fixAction = "Adicionadas flags android.useAndroidX=true, Jetifier e alocação JVM.",
                            suggestedSolution = "Configure android.useAndroidX=true no gradle.properties."
                        )
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 6. Diagnóstico de Incompatibilidades no build.gradle / build.gradle.kts
        // -------------------------------------------------------------
        for (i in workingFiles.indices) {
            val file = workingFiles[i]
            if (file.path.endsWith("build.gradle")) {
                val original = String(file.bytes, Charsets.UTF_8)
                val standardized = standardizeBuildGradle(original)
                if (standardized != original) {
                    workingFiles[i] = file.copy(bytes = standardized.toByteArray(Charsets.UTF_8))
                    detectedIssues.add(
                        DiagnosticIssue(
                            id = "BUILD_${issueCounter++}",
                            title = "Incompatibilidade no build.gradle",
                            description = "Versões de plugins do Android Gradle Plugin (AGP) ou Kotlin foram alinhadas para versões universais testadas.",
                            filePath = file.path,
                            severity = IssueSeverity.WARNING,
                            isAutoFixable = true,
                            isFixed = true,
                            fixAction = "Alinhado AGP para $TARGET_AGP_VERSION e Kotlin para $TARGET_KOTLIN_VERSION.",
                            suggestedSolution = "Alinhe as versões dos plugins no bloco buildscript ou plugins."
                        )
                    )
                }
            } else if (file.path.endsWith("build.gradle.kts")) {
                val original = String(file.bytes, Charsets.UTF_8)
                val standardized = standardizeBuildGradleKts(original)
                if (standardized != original) {
                    workingFiles[i] = file.copy(bytes = standardized.toByteArray(Charsets.UTF_8))
                    detectedIssues.add(
                        DiagnosticIssue(
                            id = "BUILD_${issueCounter++}",
                            title = "Incompatibilidade no build.gradle.kts",
                            description = "Plugins e dependências Kotlin DSL foram ajustados para versões compatíveis.",
                            filePath = file.path,
                            severity = IssueSeverity.WARNING,
                            isAutoFixable = true,
                            isFixed = true,
                            fixAction = "Alinhado AGP para $TARGET_AGP_VERSION e Kotlin para $TARGET_KOTLIN_VERSION.",
                            suggestedSolution = "Utilize versões compatíveis no catálogo toml ou nas chamadas id()."
                        )
                    )
                }
            } else if (file.path.endsWith("gradle/libs.versions.toml")) {
                val original = String(file.bytes, Charsets.UTF_8)
                val standardized = standardizeVersionsToml(original)
                if (standardized != original) {
                    workingFiles[i] = file.copy(bytes = standardized.toByteArray(Charsets.UTF_8))
                    detectedIssues.add(
                        DiagnosticIssue(
                            id = "TOML_${issueCounter++}",
                            title = "Versões desatualizadas em libs.versions.toml",
                            description = "O catálogo de versões do projeto continha versões de AGP ou Kotlin desalinhadas.",
                            filePath = file.path,
                            severity = IssueSeverity.INFO,
                            isAutoFixable = true,
                            isFixed = true,
                            fixAction = "Atualizadas versões de agp ($TARGET_AGP_VERSION) e kotlin ($TARGET_KOTLIN_VERSION) no TOML.",
                            suggestedSolution = "Mantenha o catálogo de versões sincronizado."
                        )
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 7. Diagnóstico de Repositórios Obsoletos (ex: jcenter())
        // -------------------------------------------------------------
        val repoIndices = workingFiles.indices.filter {
            workingFiles[it].path.endsWith("build.gradle") ||
            workingFiles[it].path.endsWith("build.gradle.kts") ||
            workingFiles[it].path.endsWith("settings.gradle") ||
            workingFiles[it].path.endsWith("settings.gradle.kts")
        }
        for (idx in repoIndices) {
            val file = workingFiles[idx]
            val text = String(file.bytes, Charsets.UTF_8)
            if (text.contains("jcenter()")) {
                val replaced = text.replace("jcenter()", "mavenCentral()")
                workingFiles[idx] = file.copy(bytes = replaced.toByteArray(Charsets.UTF_8))
                detectedIssues.add(
                    DiagnosticIssue(
                        id = "REPO_${issueCounter++}",
                        title = "Repositório obsoleto jcenter() detectado",
                        description = "O repositório jcenter foi descontinuado e causa falhas de resolução de dependências no Gradle.",
                        filePath = file.path,
                        severity = IssueSeverity.WARNING,
                        isAutoFixable = true,
                        isFixed = true,
                        fixAction = "Substituído jcenter() por mavenCentral().",
                        suggestedSolution = "Remova jcenter() e adicione mavenCentral() e google() nos repositórios."
                    )
                )
            }
        }

        val autoFixesCount = detectedIssues.count { it.isFixed }
        val warningsCount = detectedIssues.count { !it.isFixed && it.severity == IssueSeverity.WARNING }
        val errorsCount = detectedIssues.count { !it.isFixed && it.severity == IssueSeverity.ERROR }

        val summaryMessage = when {
            autoFixesCount > 0 -> "$autoFixesCount problemas foram diagnosticados e corrigidos automaticamente com sucesso."
            warningsCount > 0 -> "Diagnóstico concluído com $warningsCount avisos informativos."
            else -> "Código-fonte inspecionado com sucesso. Nenhuma falha estrutural detectada."
        }

        val report = ProjectDiagnosticReport(
            totalFilesAnalyzed = workingFiles.size,
            detectedTechnologies = detectedTechnologies,
            issues = detectedIssues,
            autoFixesCount = autoFixesCount,
            warningsCount = warningsCount,
            errorsCount = errorsCount,
            summaryMessage = summaryMessage,
            hasFatalUnresolvedErrors = errorsCount > 0
        )

        return Pair(workingFiles, report)
    }

    data class ResourceFixDetail(
        val filePath: String,
        val description: String
    )

    /**
     * Remove recursos duplicados no Android (.png vs .webp vs .jpg com mesmo nome na mesma pasta de densidade).
     * O Android Resource Merger lança 'Error: Duplicate resources' quando dois arquivos com mesmo nome base e extensões diferentes
     * coexistem na mesma pasta de densidade (ex: mipmap-mdpi/ic_launcher.png e mipmap-mdpi/ic_launcher.webp).
     */
    fun deduplicateAndroidResources(files: List<ExtractedFile>): Pair<List<ExtractedFile>, List<ResourceFixDetail>> {
        val deduplicated = mutableListOf<ExtractedFile>()
        val removedDetails = mutableListOf<ResourceFixDetail>()

        val resRegex = Regex("""^(.*(?:/|^)res/[^/]+)/([^/]+)\.([a-zA-Z0-9]+)$""")
        val seenResources = mutableMapOf<String, ExtractedFile>()

        for (file in files) {
            val match = resRegex.find(file.path)
            if (match != null) {
                val folder = match.groupValues[1]
                val baseName = match.groupValues[2]
                val extension = match.groupValues[3].lowercase()

                val folderName = folder.substringAfterLast('/')
                val isConflictingFolder = folderName.startsWith("drawable") ||
                        folderName.startsWith("mipmap") ||
                        folderName.startsWith("raw") ||
                        folderName.startsWith("anim") ||
                        folderName.startsWith("color")

                if (isConflictingFolder) {
                    val resourceKey = "$folder/$baseName"
                    val existing = seenResources[resourceKey]
                    if (existing != null) {
                        val existingExt = existing.path.substringAfterLast('.').lowercase()
                        // Prioridade: xml (vetor) > png > webp > jpg
                        val shouldReplace = when {
                            existingExt == "webp" && extension == "png" -> true
                            existingExt == "jpg" && (extension == "png" || extension == "webp") -> true
                            existingExt == "xml" && (extension == "png" || extension == "webp" || extension == "jpg") -> false
                            extension == "xml" -> true
                            else -> false
                        }

                        if (shouldReplace) {
                            deduplicated.remove(existing)
                            deduplicated.add(file)
                            seenResources[resourceKey] = file
                            removedDetails.add(
                                ResourceFixDetail(
                                    filePath = existing.path,
                                    description = "Removido recurso duplicado: ${existing.path} (mantido formato prioritário ${file.path})"
                                )
                            )
                        } else {
                            removedDetails.add(
                                ResourceFixDetail(
                                    filePath = file.path,
                                    description = "Removido recurso duplicado: ${file.path} (mantido formato prioritário ${existing.path})"
                                )
                            )
                        }
                        continue
                    } else {
                        seenResources[resourceKey] = file
                        deduplicated.add(file)
                        continue
                    }
                }
            }
            deduplicated.add(file)
        }

        return Pair(deduplicated, removedDetails)
    }

    /**
     * Substitui qualquer versão do Gradle pela versão fixa e testada 8.7
     */
    fun standardizeGradleWrapperProperties(content: String): String {
        val regex = Regex("""distributionUrl\s*=\s*.*""", RegexOption.MULTILINE)
        return if (regex.containsMatchIn(content)) {
            regex.replace(content, "distributionUrl=$TARGET_GRADLE_DISTRIBUTION_URL")
        } else {
            content + "\ndistributionUrl=$TARGET_GRADLE_DISTRIBUTION_URL\n"
        }
    }

    /**
     * Padroniza build.gradle Groovy
     */
    fun standardizeBuildGradle(content: String): String {
        var updated = content
        val agpRegex = Regex("""com\.android\.tools\.build:gradle:[\d\w.-]+""")
        if (agpRegex.containsMatchIn(updated)) {
            updated = agpRegex.replace(updated, "com.android.tools.build:gradle:$TARGET_AGP_VERSION")
        }
        val kotlinRegex = Regex("""org\.jetbrains\.kotlin:kotlin-gradle-plugin:[\d\w.-]+""")
        if (kotlinRegex.containsMatchIn(updated)) {
            updated = kotlinRegex.replace(updated, "org.jetbrains.kotlin:kotlin-gradle-plugin:$TARGET_KOTLIN_VERSION")
        }
        val pluginsAgp = Regex("""id\s+['"]com\.android\.(application|library)['"]\s+version\s+['"][\d\w.-]+['"]""")
        updated = pluginsAgp.replace(updated) { match ->
            val type = match.groupValues[1]
            "id 'com.android.$type' version '$TARGET_AGP_VERSION'"
        }
        return updated
    }

    /**
     * Padroniza build.gradle.kts (Kotlin DSL)
     */
    fun standardizeBuildGradleKts(content: String): String {
        var updated = content
        val agpRegex = Regex("""com\.android\.tools\.build:gradle:[\d\w.-]+""")
        if (agpRegex.containsMatchIn(updated)) {
            updated = agpRegex.replace(updated, "com.android.tools.build:gradle:$TARGET_AGP_VERSION")
        }
        val kotlinRegex = Regex("""org\.jetbrains\.kotlin:kotlin-gradle-plugin:[\d\w.-]+""")
        if (kotlinRegex.containsMatchIn(updated)) {
            updated = kotlinRegex.replace(updated, "org.jetbrains.kotlin:kotlin-gradle-plugin:$TARGET_KOTLIN_VERSION")
        }
        val pluginsAgp = Regex("""id\("com\.android\.(application|library)"\)\s+version\s+"[\d\w.-]+"""")
        updated = pluginsAgp.replace(updated) { match ->
            val type = match.groupValues[1]
            "id(\"com.android.$type\") version \"$TARGET_AGP_VERSION\""
        }
        return updated
    }

    /**
     * Padroniza libs.versions.toml
     */
    fun standardizeVersionsToml(content: String): String {
        var updated = content
        val agpToml = Regex("""agp\s*=\s*"[\d\w.-]+"""")
        if (agpToml.containsMatchIn(updated)) {
            updated = agpToml.replace(updated, "agp = \"$TARGET_AGP_VERSION\"")
        }
        val agpToml2 = Regex("""androidGradlePlugin\s*=\s*"[\d\w.-]+"""")
        if (agpToml2.containsMatchIn(updated)) {
            updated = agpToml2.replace(updated, "androidGradlePlugin = \"$TARGET_AGP_VERSION\"")
        }
        val kotlinToml = Regex("""kotlin\s*=\s*"[\d\w.-]+"""")
        if (kotlinToml.containsMatchIn(updated)) {
            updated = kotlinToml.replace(updated, "kotlin = \"$TARGET_KOTLIN_VERSION\"")
        }
        return updated
    }

    private fun generateDefaultGradlew(): String {
        return """#!/bin/sh
# Shell script to invoke Gradle Wrapper
exec gradle "$@"
"""
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimStart('/')
    }

    private fun stripCommonRootDirectory(files: List<ExtractedFile>): List<ExtractedFile> {
        if (files.isEmpty()) return files
        val firstSlashIndices = files.map { it.path.indexOf('/') }
        if (firstSlashIndices.any { it <= 0 }) {
            return files
        }
        val firstSegments = files.map { it.path.substring(0, it.path.indexOf('/')) }
        val commonRoot = firstSegments.first()
        if (firstSegments.all { it == commonRoot }) {
            return files.map {
                it.copy(path = it.path.substring(commonRoot.length + 1))
            }
        }
        return files
    }

    private fun isLikelyText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val checkLen = minOf(bytes.size, 512)
        var zeroCount = 0
        for (i in 0 until checkLen) {
            if (bytes[i] == 0.toByte()) zeroCount++
        }
        return zeroCount == 0
    }

    private fun detectTechnologies(files: List<ExtractedFile>): List<String> {
        val techs = mutableListOf<String>()
        val paths = files.map { it.path }

        if (paths.any { it.endsWith("build.gradle") || it.endsWith("build.gradle.kts") }) {
            techs.add("Android / Gradle (Kotlin / Java)")
        }
        if (paths.any { it.endsWith("pom.xml") }) {
            techs.add("Apache Maven")
        }
        if (paths.any { it.endsWith("package.json") }) {
            techs.add("Node.js / NPM")
        }
        if (paths.any { it.endsWith("Cargo.toml") }) {
            techs.add("Rust Cargo")
        }
        if (paths.any { it.endsWith("go.mod") }) {
            techs.add("Go Lang")
        }
        if (paths.any { it.endsWith("CMakeLists.txt") || it.endsWith("Makefile") }) {
            techs.add("C / C++ (Make / CMake)")
        }
        if (paths.any { it.endsWith("requirements.txt") || it.endsWith("pyproject.toml") }) {
            techs.add("Python")
        }
        if (paths.any { it.endsWith("compile.sh") || it.endsWith("build.sh") }) {
            techs.add("Custom Build Script (.sh)")
        }

        if (techs.isEmpty()) {
            techs.add("Código-fonte genérico")
        }
        return techs
    }

    fun generateUniversalWorkflow(): String {
        return """
name: "Cloud Compiler CI"
on:
  push:
    branches:
      - '**'

jobs:
  compile:
    name: "Compilação do Projeto"
    runs-on: ubuntu-latest
    timeout-minutes: 25

    steps:
      - name: "1. Baixar Código-Fonte"
        uses: actions/checkout@v4

      - name: "2. Inspecionar e Detectar Projeto"
        id: detect
        run: |
          echo "=============================================="
          echo ">>> Inspecionando arquivos do projeto:"
          ls -la
          echo "=============================================="
          
          if [ -f "gradlew" ] || [ -f "build.gradle" ] || [ -f "build.gradle.kts" ] || [ -f "pom.xml" ]; then
            echo "has_jvm=true" >> ${'$'}GITHUB_OUTPUT
            echo "Ambiente Java / Gradle detectado."
          fi
          if [ -f "package.json" ]; then
            echo "has_node=true" >> ${'$'}GITHUB_OUTPUT
            echo "Ambiente Node.js detectado."
          fi
          if [ -f "Cargo.toml" ]; then
            echo "has_rust=true" >> ${'$'}GITHUB_OUTPUT
            echo "Ambiente Rust detectado."
          fi
          if [ -f "go.mod" ]; then
            echo "has_go=true" >> ${'$'}GITHUB_OUTPUT
            echo "Ambiente Go detectado."
          fi
          if [ -f "requirements.txt" ] || [ -f "pyproject.toml" ]; then
            echo "has_python=true" >> ${'$'}GITHUB_OUTPUT
            echo "Ambiente Python detectado."
          fi

      - name: "3. Configurar JDK 17"
        if: steps.detect.outputs.has_jvm == 'true'
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: "4. Configurar Node.js 20"
        if: steps.detect.outputs.has_node == 'true'
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: "5. Configurar Go"
        if: steps.detect.outputs.has_go == 'true'
        uses: actions/setup-go@v5
        with:
          go-version: 'stable'

      - name: "6. Executar Compilação Padronizada"
        run: |
          set -e
          echo "=============================================="
          echo ">>> INICIANDO COMPILAÇÃO OFICIAL GITCOMPILE"
          echo "=============================================="
          
          # Garantir permissão de execução no wrapper
          if [ -f "gradlew" ]; then
            chmod +x ./gradlew
          fi

          # Se o projeto tiver wrapper apontando para gradle incompatível, certificar versão segura 8.7
          if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
            echo ">>> Verificando gradle-wrapper.properties do projeto..."
            cat gradle/wrapper/gradle-wrapper.properties
          fi

          # Resolver automaticamente recursos Android duplicados (ex: ic_launcher.png vs ic_launcher.webp)
          echo ">>> Verificando e resolvendo recursos Android duplicados..."
          find . -type d \( -name "mipmap*" -o -name "drawable*" \) 2>/dev/null | while read -r dir; do
            [ -d "${'$'}dir" ] || continue
            for webp in "${'$'}dir"/*.webp; do
              [ -f "${'$'}webp" ] || continue
              base="${'$'}{webp%.webp}"
              if [ -f "${'$'}{base}.png" ]; then
                echo ">>> [Auto-Fix] Removendo recurso duplicado: ${'$'}webp (mantendo ${'$'}{base}.png)"
                rm -f "${'$'}webp"
              fi
              if [ -f "${'$'}{base}.jpg" ]; then
                echo ">>> [Auto-Fix] Removendo recurso duplicado: ${'$'}{base}.jpg (mantendo ${'$'}webp)"
                rm -f "${'$'}{base}.jpg"
              fi
            done
            for jpg in "${'$'}dir"/*.jpg; do
              [ -f "${'$'}jpg" ] || continue
              base="${'$'}{jpg%.jpg}"
              if [ -f "${'$'}{base}.png" ]; then
                echo ">>> [Auto-Fix] Removendo recurso duplicado: ${'$'}jpg (mantendo ${'$'}{base}.png)"
                rm -f "${'$'}jpg"
              fi
            done
          done
          
          if [ -f "./compile.sh" ]; then
            echo ">>> Executando script customizado compile.sh..."
            chmod +x ./compile.sh
            ./compile.sh
          elif [ -f "./build.sh" ]; then
            echo ">>> Executando script customizado build.sh..."
            chmod +x ./build.sh
            ./build.sh
          elif [ -f "gradlew" ]; then
            echo ">>> Iniciando compilação no modo RELEASE com Gradle Wrapper..."
            chmod +x ./gradlew
            
            # Criar keystore temporária para assinar caso o projeto não tenha keystore própria
            keytool -genkeypair -v -keystore /tmp/gitcompile-release.jks -alias release -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Release, OU=GitCompile, O=App, C=BR" 2>/dev/null || true
            
            if ./gradlew assembleRelease --no-daemon --stacktrace; then
              echo ">>> Compilação assembleRelease concluída com sucesso!"
            else
              echo ">>> assembleRelease padrão falhou (keystore privada ausente). Tentando compilar Release com injeção segura de assinatura..."
              ./gradlew assembleRelease -Pandroid.injected.signing.store.file=/tmp/gitcompile-release.jks -Pandroid.injected.signing.store.password=android -Pandroid.injected.signing.key.alias=release -Pandroid.injected.signing.key.password=android --no-daemon || ./gradlew assembleDebug --no-daemon
            fi
            
            # Se gerou APK release unsigned (sem assinatura), assina automaticamente para o usuário poder instalar no celular
            find . -name "*release-unsigned.apk" | while read unsigned_apk; do
              signed_apk="${'$'}{unsigned_apk%-unsigned.apk}.apk"
              echo ">>> Assinando APK Release automaticamente: ${'$'}unsigned_apk -> ${'$'}signed_apk"
              jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore /tmp/gitcompile-release.jks -storepass android -keypass android "${'$'}unsigned_apk" release 2>/dev/null && mv "${'$'}unsigned_apk" "${'$'}signed_apk" || true
            done
          elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
            echo ">>> Iniciando compilação Gradle no modo RELEASE..."
            keytool -genkeypair -v -keystore /tmp/gitcompile-release.jks -alias release -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Release, OU=GitCompile, O=App, C=BR" 2>/dev/null || true
            gradle assembleRelease --no-daemon --stacktrace || gradle assembleDebug --no-daemon
          elif [ -f "pom.xml" ]; then
            echo ">>> Executando Apache Maven..."
            mvn clean package -B
          elif [ -f "Cargo.toml" ]; then
            echo ">>> Compilando com Rust Cargo..."
            cargo build --release
          elif [ -f "go.mod" ]; then
            echo ">>> Compilando com Go..."
            go build -v ./...
          elif [ -f "package.json" ]; then
            echo ">>> Compilando com Node.js..."
            npm install
            if grep -q "\"build\":" package.json; then
              npm run build
            else
              echo "Script de build não configurado em package.json. Dependências instaladas com sucesso."
            fi
          elif [ -f "Makefile" ]; then
            echo ">>> Compilando com Make..."
            make
          elif [ -f "CMakeLists.txt" ]; then
            echo ">>> Compilando com CMake..."
            mkdir -p build && cd build
            cmake ..
            cmake --build .
          elif [ -f "main.c" ]; then
            echo ">>> Compilando arquivo C com GCC..."
            gcc -Wall -O2 main.c -o app_output
          elif [ -f "main.cpp" ]; then
            echo ">>> Compilando arquivo C++ com G++..."
            g++ -Wall -O2 main.cpp -o app_output
          elif [ -f "requirements.txt" ]; then
            echo ">>> Instalando dependências e verificando sintaxe Python..."
            python -m pip install --upgrade pip
            pip install -r requirements.txt
            python -m compileall .
          else
            echo ">>> Nenhum build system padrão detectado. Verificando arquivos de código..."
            find . -maxdepth 3 -not -path '*/.*'
          fi
          
          echo "=============================================="
          echo ">>> COMPILAÇÃO FINALIZADA COM SUCESSO!"
          echo "=============================================="

      - name: "7. Salvar Artefatos Compilados (APKs e Binários)"
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: compiled-artifacts
          path: |
            **/build/outputs/apk/**/*.apk
            **/build/outputs/bundle/**/*.aab
            **/build/libs/*.jar
            **/target/*.jar
            **/target/release/*
            **/dist/**
            **/build/**
            app_output
          if-no-files-found: ignore
""".trimIndent()
    }
}
