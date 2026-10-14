# Plano de Modernização do GitCompile: Diagnóstico Inteligente, Auto-Correção e Redesign Visual

Este plano detalha a modernização completa da experiência do aplicativo, integrando um motor robusto de análise estática e diagnóstico do código-fonte com auto-correção segura de falhas (recursos duplicados, versões de Gradle/AGP, dependências e manifest), visualização em pipeline com relatório expansível e redesign Material 3 minimalista em todas as telas.

---

## Decisões Críticas e Preferências do Usuário (Fase 1)

> [!IMPORTANT]
> As decisões abaixo foram alinhadas diretamente com o usuário durante a etapa de esclarecimento:

- **Modo de Correção:** Auto-correção automática de problemas seguros com exibição de resumo detalhado das alterações.
- **Apresentação do Diagnóstico:** Etapa visual integrada no pipeline de compilação com cartão/relatório expansível de inspeção.
- **Identidade Visual:** Material 3 minimalista, com cartões organizados, espaçamento consistente de 8dp/16dp, contraste acessível e animações de estado fluidas.

---

## 1. Visão Geral e Conceito Central

### O que o aplicativo faz
O GitCompile recebe um arquivo ZIP com o código-fonte de um projeto Android (ou multi-linguagem), extrai e inspeciona sua estrutura de arquivos em memória, detecta vulnerabilidades e erros comuns de compilação (recursos duplicados `.png`/`.webp`, versões incompatíveis do Gradle/AGP, falta de wrapper executável, configurações depreciadas), aplica correções automáticas seguras sem alterar a lógica de negócio do usuário, envia a árvore higienizada para o GitHub Actions e monitora a compilação em tempo real, entregando o APK gerado com logs completos e histórico persistido localmente em cache (Room).

### Público e Valor Principal
Desenvolvedores e entusiastas que desejam compilar APKs a partir de código-fonte diretamente pelo celular, sem precisar de ambiente Android Studio ou terminal configurado na máquina local, com a garantia de que erros de configuração comuns serão identificados e corrigidos antes da falha no GitHub Actions.

---

## 2. Experiência do Usuário e Design Visual (Material 3)

### Diretrizes de Estilo
- **Paleta de Cores:** Fundo limpo e suave (`surface` / `surfaceContainerLow`), cartões com contornos sutis (`outlineVariant` a 30%), verde de sucesso semafórico (`#2E7D32` / `#3FB950`), azul primário elegante para progresso e vermelho suave para falhas (`errorContainer`).
- **Tipografia:** Hierarquia clara com títulos em `TitleMedium`/`TitleLarge` com peso semi-bold e código/logs com `FontFamily.Monospace` em `BodySmall`.
- **Micro-interações:** Ícones de status com transições suaves de preenchimento, indicadores circulares proporcionais de 20dp e acordes expansíveis com `AnimatedVisibility`.

### Telas do Fluxo
1. **Tela Inicial / Seleção de ZIP (`SelectZipScreen`):**
   - Cabeçalho minimalista com indicador discreto do repositório conectado e botão de logout seguro.
   - Área de upload elegante com ícone suave, suporte a seleção rápida e cartões informativos sobre o motor de diagnóstico integrado.
2. **Tela de Etapas de Compilação (`CompilationStepsScreen`):**
   - Pipeline de 5 etapas claras e conectadas:
     1. *Inspeção & Diagnóstico do Código-Fonte* (Nova etapa com contagem de arquivos e problemas diagnosticados/corrigidos).
     2. *Preparação e Higienização do Projeto*.
     3. *Envio ao GitHub*.
     4. *Execução no Runner na Nuvem*.
     5. *Geração e Assinatura do APK*.
   - **Cartão de Diagnóstico Expansível:** Mostra a lista de arquivos analisados, tecnologias detectadas, problemas corrigidos automaticamente (ex.: recursos duplicados removidos, Gradle alinhado) e avisos importantes.
   - Botão de ação dinâmico no rodapé: *Baixar APK* / *Instalar APK* em caso de sucesso, ou *Ver Diagnóstico & Logs* em caso de erro.
3. **Tela de Histórico (`HistoryScreen`):**
   - Lista minimalista alimentada exclusivamente pelo banco Room local.
   - Filtros visuais por abas ou chips modernos (*Todas*, *Sucesso*, *Falhas*).
   - Cartões com visual limpo, badge de status, hora da compilação e ações rápidas (baixar novamente, ver logs, excluir do cache).
4. **Visualizador de Logs (`CompilationLogsScreen`):**
   - Terminal limpo com visual escuro de alto contraste, busca/filtro de texto, botão de cópia de logs com um toque e diagnóstico destacado no topo.

---

## 3. Arquitetura do Motor de Diagnóstico e Auto-Correção

### Mecanismos de Diagnóstico Implementados
1. **Detector de Recursos Duplicados (`ResourceCollisionDetector`):**
   - Varre pastas `res/drawable*`, `res/mipmap*`, `res/raw*`, `res/anim*` e `res/color*`.
   - Identifica colisões de mesmo nome base com extensões diferentes (ex.: `ic_launcher.png` e `ic_launcher.webp`, `ic_launcher_round.png` e `ic_launcher_round.webp`).
   - Remove automaticamente o arquivo redundante mantendo o de maior prioridade (`.png` > `.webp` > `.jpg`), eliminando o erro fatal do Android Resource Merger.
2. **Detector de Compatibilidade Gradle & AGP (`GradleCompatibilityEngine`):**
   - Detecta versões incompatíveis do Gradle Wrapper (como 9.x que quebra AGP legado ou plugins antigos).
   - Ajusta de forma segura `gradle-wrapper.properties` para Gradle 8.7 estável.
   - Alinha AGP (8.5.2) e Kotlin (1.9.24) em `build.gradle`, `build.gradle.kts` e `libs.versions.toml`.
   - Injeta `gradle.properties` com `android.useAndroidX=true`, `android.nonTransitiveRClass=true` e flags de alocação de memória segura para runners.
3. **Higienização de Workflows Antigos e Prevenção de Conflitos:**
   - Remove workflows conflitantes legados que forçam versões antigas de Gradle (como `Setup Gradle 9.3.1` que causou a falha anterior).
   - Injeta o workflow oficial universal testado do GitCompile com passos de auto-cura no próprio runner antes do `assembleRelease`.
4. **Relatório de Diagnóstico Estruturado:**
   - Modelo de dados `ProjectDiagnosticReport` com:
     - `issuesFound: List<DiagnosticIssue>` (gravidade: ERRO, ALERTA, INFO).
     - `autoFixesApplied: List<AutoFixAction>` (descrição do que foi corrigido e arquivos afetados).
     - `detectedStack: ProjectStackInfo` (pacote Android, versão min/target sdk, build tool).

```
┌─────────────────────────────────────────────────────────────┐
│                    Fluxo de Compilação                      │
├─────────────────┬─────────────────────────┬─────────────────┤
│  1. ZIP Input   │   2. Análise Estática   │  3. Auto-Fix    │
│  (Descompacta   │   - Recursos Duplicados │  - Remove dupes │
│   em memória)   │   - Gradle & AGP        │  - Ajusta confs │
│                 │   - Workflows legados   │  - Gera Wrapper │
└────────┬────────┴────────────┬────────────┴────────┬────────┘
         │                     │                     │
         ▼                     ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Pipeline Visual (Material 3)                             │
│    [ ✓ ] 1. Diagnóstico do Código (Expansível: 4 correções) │
│    [ ✓ ] 2. Preparação dos Arquivos                         │
│    [ ⟳ ] 3. Envio ao GitHub                                 │
│    [   ] 4. Compilação no Runner Cloud                      │
│    [   ] 5. APK Finalizado e Pronto                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Plano de Execução Técnica

1. **Camada de Dados & Análise (`com.example.data.zip`):**
   - Refatorar e enriquecer `ZipProcessor.kt` com a classe de diagnóstico `ProjectDiagnostic` e `DiagnosticIssue`.
   - Fortalecer a detecção e desduplicação de recursos Android de forma determinística em todas as subpastas `res/mipmap*` e `res/drawable*`.
   - Garantir que nenhum workflow legado do ZIP ou do repositório interfira no runner.
2. **Camada de Estado (`CompilerViewModel.kt`):**
   - Adicionar estado de diagnóstico estruturado ao `UiState` (`diagnosticReport`, `isDiagnosticExpanded`).
   - Integrar os passos de diagnóstico visualmente no pipeline de compilação.
   - Salvar o sumário de diagnóstico no banco de dados Room (`CompilationEntity`) para consulta posterior no Histórico.
3. **Design e Interface (`com.example.ui.screens`):**
   - **`CompilationStepsScreen.kt`:** Redesenhar com cartões M3 limpos, espaçamento consistente, acordeão de diagnóstico detalhado com tags de "Corrigido Automaticamente" e botões modernos.
   - **`SelectZipScreen.kt`:** Aprimorar o visual com visualizador prévio de capacidades, cartão de boas-vindas organizado e remoção de poluição visual.
   - **`HistoryScreen.kt`:** Polir a listagem com filtros rápidos, badges limpos, modal de detalhes do diagnóstico e opções de limpeza simples.
   - **`CompilationLogsScreen.kt`:** Estilo terminal limpo com abas para Alternar entre "Diagnóstico Inicial" e "Logs de Compilação".
4. **Validação e Compilação:**
   - Executar `compile_applet` para certificar que todo o código compila sem erros ou incompatibilidades.
