# Football Analysis — protótipo Android 0.1

**Projeto novo e independente do System Color.** Nome provisório.

## Ajustes para primeira compilação
- Inicialização obrigatória em orientação horizontal (landscape).
- Toque prolongado nos comandos exibe explicação contextual.
- A escolha de pasta de downloads, zoom e exportação MP4 ainda serão desenvolvidos.

## Implementado
- Abrir foto via seletor Android
- Abrir vídeo e escolher quadro pelo controle deslizante
- Desenhar setas, círculos e linhas diretamente sobre o quadro
- Desfazer a última marcação
- Exportar quadro/imagem anotado em PNG

## Ainda não implementado
- IA para detectar jogadores
- Calibração do gramado e linha de impedimento
- Remoção/inserção realista de jogadores
- Inserção de congelamentos na linha do tempo e exportação MP4
- Salvamento de projetos editáveis
- Controles precisos quadro a quadro (o controle atual usa MediaMetadataRetriever)

## Compilar
GitHub Actions: envie os arquivos para um repositório novo na branch `main`, abra Actions > Build Android APK > Run workflow e baixe o artifact `FootballAnalysis-debug-apk`.

Localmente, com Android SDK e JDK 17: `gradle :app:assembleDebug` (Gradle 8.9).

O projeto foi inspecionado estaticamente, mas não compilado neste ambiente. O Gradle wrapper não acompanha o pacote; o workflow instala Gradle 8.9.

## Arquitetura futura
- Kotlin: Android/editor
- Java: integração quando necessária
- C++/NDK: processamento especializado, quando justificar
- Python: preparação/treinamento de modelos fora do APK

## Nota
A linha de impedimento futura será uma estimativa tática, não uma decisão oficial de VAR.
