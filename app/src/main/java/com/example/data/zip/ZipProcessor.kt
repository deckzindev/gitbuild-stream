package com.example.data.zip

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
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
    val standardizationResult: StandardizationResult = StandardizationResult(0, 0, emptyList())
)

object ZipProcessor {

    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB per individual file
    private const val MAX_TOTAL_FILES = 800

    // Constantes de padronização inteligente e estável
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
                                break // Skip overly large binary file
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

        // If all files share a common single root folder (e.g., project-master/src/...), strip it
        val cleanedFiles = stripCommonRootDirectory(rawFiles)

        // Check if original ZIP had any workflows
        val hadOriginalWorkflows = cleanedFiles.any {
            it.path.startsWith(".github/workflows/")
        }

        // Filter out any third-party/old workflows so GitCompile standardization is ALWAYS 100% enforced
        val filteredFiles = cleanedFiles.filterNot {
            it.path.startsWith(".github/workflows/")
        }.toMutableList()

        // 1. Remover recursos duplicados do Android (.png vs .webp vs .jpg com mesmo nome na mesma pasta de densidade)
        val (dedupedFiles, dedupDetails) = deduplicateAndroidResources(filteredFiles)

        // 2. Executar a COMPILAÇÃO INTELIGENTE: padronizar todos os arquivos de configuração do projeto antes de enviar ao GitHub
        val (standardizedFiles, standardizationDetails) = standardizeProjectFiles(dedupedFiles)

        // ALWAYS inject GitCompile's official verified CI workflow
        val workflowContent = generateUniversalWorkflow()
        standardizedFiles.add(
            ExtractedFile(
                path = ".github/workflows/compile.yml",
                bytes = workflowContent.toByteArray(Charsets.UTF_8),
                isText = true
            )
        )

        val detectedTechnologies = detectTechnologies(standardizedFiles)
        val combinedDetails = dedupDetails + standardizationDetails.details

        return ZipScanResult(
            fileName = originalFileName,
            totalSize = totalBytesRead,
            files = standardizedFiles,
            detectedTechnologies = detectedTechnologies,
            hadOriginalWorkflows = hadOriginalWorkflows,
            workflowStandardized = true,
            standardizationResult = standardizationDetails.copy(details = combinedDetails)
        )
    }

    /**
     * Remove recursos duplicados no Android (ex: ic_launcher.png e ic_launcher.webp na mesma pasta de mipmap ou drawable).
     * O Android Resource Merger lança 'Error: Duplicate resources' quando dois arquivos com mesmo nome base e extensões diferentes
     * coexistem na mesma pasta de densidade (ex: mipmap-mdpi/ic_launcher.png e mipmap-mdpi/ic_launcher.webp).
     */
    fun deduplicateAndroidResources(files: List<ExtractedFile>): Pair<List<ExtractedFile>, List<String>> {
        val deduplicated = mutableListOf<ExtractedFile>()
        val removedDetails = mutableListOf<String>()

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
                        // Prioridade: png > webp > jpg
                        val shouldReplace = when {
                            existingExt == "webp" && extension == "png" -> true
                            existingExt == "jpg" && (extension == "png" || extension == "webp") -> true
                            existingExt == "xml" && (extension == "png" || extension == "webp") -> false
                            else -> false
                        }

                        if (shouldReplace) {
                            deduplicated.remove(existing)
                            deduplicated.add(file)
                            seenResources[resourceKey] = file
                            removedDetails.add("Removido recurso duplicado: ${existing.path} (mantido ${file.path})")
                        } else {
                            removedDetails.add("Removido recurso duplicado: ${file.path} (mantido ${existing.path})")
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
     * Padroniza inteligentemente as configurações do projeto para evitar erros de compilação:
     * - gradle-wrapper.properties: fixa Gradle 8.7 (evita incompatibilidades de 9.x, 9.31, etc)
     * - build.gradle / build.gradle.kts / settings.gradle / toml: alinha AGP (8.5.2) e Kotlin (1.9.24)
     * - injeta gradlew e gradlew.bat se ausentes
     * - injeta gradle.properties otimizado (JVM args, AndroidX, non-transitive R)
     */
    fun standardizeProjectFiles(files: List<ExtractedFile>): Pair<MutableList<ExtractedFile>, StandardizationResult> {
        val resultList = mutableListOf<ExtractedFile>()
        val details = mutableListOf<String>()
        var modifiedCount = 0
        var addedCount = 0

        val fileMap = files.associateBy { it.path }.toMutableMap()
        val isGradleProject = files.any {
            it.path.endsWith("build.gradle") ||
            it.path.endsWith("build.gradle.kts") ||
            it.path.endsWith("settings.gradle") ||
            it.path.endsWith("settings.gradle.kts") ||
            it.path.contains("gradle-wrapper.properties")
        }

        // 1. Processar ou atualizar arquivos existentes
        for (file in files) {
            var currentBytes = file.bytes
            var modified = false

            // Padronizar gradle-wrapper.properties
            if (file.path.endsWith("gradle/wrapper/gradle-wrapper.properties")) {
                val originalText = String(currentBytes, Charsets.UTF_8)
                val newText = standardizeGradleWrapperProperties(originalText)
                if (newText != originalText) {
                    currentBytes = newText.toByteArray(Charsets.UTF_8)
                    modified = true
                    details.add("Gradle Wrapper ajustado para a versão estável $TARGET_GRADLE_VERSION")
                }
            }
            // Padronizar build.gradle (Groovy)
            else if (file.path.endsWith("build.gradle")) {
                val originalText = String(currentBytes, Charsets.UTF_8)
                val newText = standardizeBuildGradle(originalText)
                if (newText != originalText) {
                    currentBytes = newText.toByteArray(Charsets.UTF_8)
                    modified = true
                    details.add("Ajustada compatibilidade em ${file.path}")
                }
            }
            // Padronizar build.gradle.kts (Kotlin DSL)
            else if (file.path.endsWith("build.gradle.kts")) {
                val originalText = String(currentBytes, Charsets.UTF_8)
                val newText = standardizeBuildGradleKts(originalText)
                if (newText != originalText) {
                    currentBytes = newText.toByteArray(Charsets.UTF_8)
                    modified = true
                    details.add("Ajustada compatibilidade em ${file.path}")
                }
            }
            // Padronizar libs.versions.toml
            else if (file.path.endsWith("gradle/libs.versions.toml")) {
                val originalText = String(currentBytes, Charsets.UTF_8)
                val newText = standardizeVersionsToml(originalText)
                if (newText != originalText) {
                    currentBytes = newText.toByteArray(Charsets.UTF_8)
                    modified = true
                    details.add("Versões de AGP e Kotlin padronizadas em libs.versions.toml")
                }
            }

            if (modified) {
                modifiedCount++
                resultList.add(file.copy(bytes = currentBytes))
                fileMap[file.path] = file.copy(bytes = currentBytes)
            } else {
                resultList.add(file)
            }
        }

        // 2. Se for projeto Gradle/Android, garantir que arquivos essenciais existam
        if (isGradleProject) {
            // Garantir gradle-wrapper.properties
            val wrapperPath = "gradle/wrapper/gradle-wrapper.properties"
            if (!fileMap.containsKey(wrapperPath)) {
                val wrapperContent = """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=$TARGET_GRADLE_DISTRIBUTION_URL
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""".trimIndent()
                resultList.add(ExtractedFile(wrapperPath, wrapperContent.toByteArray(Charsets.UTF_8), isText = true))
                addedCount++
                details.add("Criado gradle-wrapper.properties apontando para Gradle $TARGET_GRADLE_VERSION")
            }

            // Garantir gradlew executável unix
            if (!fileMap.containsKey("gradlew")) {
                val gradlewScript = generateDefaultGradlew()
                resultList.add(ExtractedFile("gradlew", gradlewScript.toByteArray(Charsets.UTF_8), isText = true))
                addedCount++
                details.add("Injetado script executável 'gradlew' universal")
            }

            // Garantir gradle.properties com flags anti-falha
            val gradlePropsPath = "gradle.properties"
            val existingProps = fileMap[gradlePropsPath]
            if (existingProps == null) {
                val propsContent = """
org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
android.nonTransitiveRClass=true
org.gradle.parallel=true
org.gradle.caching=true
""".trimIndent()
                resultList.add(ExtractedFile(gradlePropsPath, propsContent.toByteArray(Charsets.UTF_8), isText = true))
                addedCount++
                details.add("Criado gradle.properties com flags otimizadas e anti-crash")
            } else {
                val originalText = String(existingProps.bytes, Charsets.UTF_8)
                var propsText = originalText
                if (!propsText.contains("android.useAndroidX")) {
                    propsText += "\nandroid.useAndroidX=true\n"
                }
                if (!propsText.contains("android.enableJetifier")) {
                    propsText += "\nandroid.enableJetifier=true\n"
                }
                if (propsText != originalText) {
                    val idx = resultList.indexOfFirst { it.path == gradlePropsPath }
                    val updated = ExtractedFile(gradlePropsPath, propsText.toByteArray(Charsets.UTF_8), isText = true)
                    if (idx >= 0) {
                        resultList[idx] = updated
                    } else {
                        resultList.add(updated)
                    }
                    modifiedCount++
                    details.add("Ativado AndroidX e Jetifier em gradle.properties")
                }
            }
        }

        return Pair(resultList, StandardizationResult(modifiedCount, addedCount, details))
    }

    /**
     * Substitui qualquer versão do Gradle (ex: 9.31, 9.0, 7.x, 8.x) pela versão fixa e testada 8.7
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
        // Ex: classpath 'com.android.tools.build:gradle:9.3.1' -> 8.5.2
        val agpRegex = Regex("""com\.android\.tools\.build:gradle:[\d\w.-]+""")
        if (agpRegex.containsMatchIn(updated)) {
            updated = agpRegex.replace(updated, "com.android.tools.build:gradle:$TARGET_AGP_VERSION")
        }
        // Ex: classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:2.x" -> 1.9.24
        val kotlinRegex = Regex("""org\.jetbrains\.kotlin:kotlin-gradle-plugin:[\d\w.-]+""")
        if (kotlinRegex.containsMatchIn(updated)) {
            updated = kotlinRegex.replace(updated, "org.jetbrains.kotlin:kotlin-gradle-plugin:$TARGET_KOTLIN_VERSION")
        }
        // Plugins id 'com.android.application' version '...'
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
        // agp = "..."
        val agpToml = Regex("""agp\s*=\s*"[\d\w.-]+"""")
        if (agpToml.containsMatchIn(updated)) {
            updated = agpToml.replace(updated, "agp = \"$TARGET_AGP_VERSION\"")
        }
        // androidGradlePlugin = "..."
        val agpToml2 = Regex("""androidGradlePlugin\s*=\s*"[\d\w.-]+"""")
        if (agpToml2.containsMatchIn(updated)) {
            updated = agpToml2.replace(updated, "androidGradlePlugin = \"$TARGET_AGP_VERSION\"")
        }
        // kotlin = "..."
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

          # Resolver automaticamente recursos duplicados no Android (ex: ic_launcher.png vs ic_launcher.webp)
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
