# Evidências do candidato local — 24/11/2026

## Escopo e preservação

Foram consultados os scripts de build, instaladores separados, manifests e inventário SHA256, testes Java/PowerShell/Node existentes, fontes de tema/ordenação/tradução, documentação e estudo `compatibility/`. O trabalho local anterior foi preservado na branch `compatibility/jasper-java`. Nenhum bundle de produção, relatório original, credencial, conexão ou INI foi alterado. Não houve instalação em Program Files, publicação ou release.

O anexo “Texto colado(1).txt” não foi localizado entre os arquivos acessíveis da pasta de trabalho/Documentos do Codex. Não se presumiu seu conteúdo. `Test-Path Z:\` retornou falso: a unidade não estava disponível e o vault não foi consultado. Não foi possível confirmar instruções/decisões recentes exclusivas do vault. Não se atribui a indisponibilidade a uma causa de rede ou máquina específica.

## Alterações desta etapa

- `installer/windows/Program.cs`: interface WinForms x64 e executável autoextraível; detecção/seleção do Studio; seleção independente dos três módulos; conferência, instalação e abertura; extração segura e integridade; tratamento de falha e cancelamento. Sem elevação da interface inteira; UAC é solicitado somente ao instalar.
- `installer/windows/Unified.ps1`: reúne os inventários dos módulos selecionados em uma transação do instalador atual; separa stdout JSON de stderr; mantém logs e estado de abertura estável. O perfil vem de `compatibility/studio-profiles.json`.
- `installer/windows/Instalar.bat`, `Abrir JS.bat` e `app.manifest`: entradas para o mesmo EXE, manifesto Windows e suporte a caminhos longos/DPI. Os BATs separados anteriores continuam disponíveis.
- `installer/common/Instalar.ps1`: plano JSON somente leitura, perfil gráfico restrito, versão/arquitetura do Java e SWT, dependências obrigatórias, nomes de arquivos seguros, mutex por instalação gráfica, registro de cópia parcial antes da escrita e recuperação que continua após falha de restauração. Preserva os limites dos instaladores anteriores; não libera outras versões.
- `scripts/Build-Windows.ps1`: verifica artefatos e gera EXE, BATs, instruções, hashes e ZIP em saída nova. Não recompila nem altera versões dos complementos; não sobrescreve pacotes anteriores.
- `tests/ModuleRegressionTest.java` e `scripts/Test-Regressions.ps1`: carregamento real das sete paletas; comparador numérico com casos determinísticos e propriedades; identidade/idempotência das conexões; traduções carregadas via Java Properties. Reutiliza as verificações existentes de recursos, parâmetros, contraste, hashes, coleta, abertura e cache.
- `tests/Test-InstallRecovery.ps1`: injeta falhas no bloco de transação real contra arquivos sintéticos. Não chama elevação nem instala em um Studio real.
- `tests/Test-WindowsInstaller.ps1`: cabeçalho PE x64, hashes externos, extração e formulário, planos de perfis sintéticos, bloqueios e integridade. O parâmetro opcional `-StudioPath` apenas inspeciona a instalação real.
- `scripts/Test-StudioComponents.ps1`: torna reproduzíveis 16 testes existentes de componentes SWT/Studio, compilados contra a plataforma instalada. Usa somente controles, dados e contas sintéticos, com timeout e logs. Não acessa servidores reais.
- `tests/CheckSaveProgress.java`: inicializa a cor muted que a fixture deixava nula antes de testar a cor nativa desabilitada. Nenhuma rotina de tema foi alterada para fazer o teste passar.
- `compatibility/Test-Smoke.mjs` e `results/smoke.json`: cinco novas execuções, preservando o arquivo de evidências da matriz ampla. `results/features.json` foi atualizado com nova execução das seis fronteiras de recursos.
- `.github/workflows/ci.yml`: suíte de regressão, build e autoteste do EXE, retenção do candidato como artefato de CI; elimina execuções duplicadas da suíte. Não publica release. O workflow manual `compatibility.yml` inclui o smoke separado.
- `README.md`, `INSTALADOR-WINDOWS.md` e `VALIDACAO-MANUAL.md`: utilização, limites, recuperação, requisitos e conferência visual separada.

Os demais arquivos de `compatibility/`, catálogo de versões, matriz ampla e arquitetura já existiam como alterações locais antes desta etapa. Foram reutilizados, não apresentados como implementação nova desta rodada.

## Resultados

| Verificação | Resultado e limite |
|---|---|
| Inventário atual | 153 JARs, 49 recursos e 6.496 chaves aprovados; hashes, parâmetros e fontes correspondentes |
| Regressão de módulos | Paletas, tradução, comparador e identidade aprovados |
| Abertura/cache | Testes existentes aprovados; idioma/tema separados, clean controlado, caches exatos e preservação de preferências/segredos |
| Recuperação | Sucesso, cópia parcial e recuperação impedida aprovados em fixtures; erro não ocultado, tentativa de restaurar os demais arquivos e backup mantido |
| Componentes nativos | 16 testes recompilados aprovados, incluindo seleções, alternância de paletas, publicação, cores personalizadas, tradução, pesquisa e ordenação; não equivalem a inspeção do workbench completo |
| EXE | Build e autoteste aprovados; planos positivos/negativos e inspeção real somente leitura aprovados |
| Fronteiras JasperReports | 6 casos aprovados: styleExpression em 6.20.5/6.20.6/6.21.5 e DPI em 7.0.8/7.1.0/7.1.1, incluindo recusas esperadas |
| Smoke novo | 6.6.0/Java 8, 6.20.6/Java 17, 6.21.5/Java 21, 7.0.0/Java 17 e 7.1.1/Java 27 aprovados no escopo core |
| Matriz anterior | Evidências preservadas de 43 motores × 14 JVMs = 602 combinações core aprovadas; hashes do worker/runtime conferidos. Não foram repetidas todas as 602 nesta etapa |
| Testes de contrato | 6 aprovados localmente, sem skips; no CI sem runtimes preparados os testes de execução nativa indicam skip, não aprovação |
| CI hospedado | Configurado, mas não executado no GitHub nesta alteração local |
| Instalação/UAC e abertura reais | Não executadas, pois não autorizadas; validação manual pendente |

Falhas iniciais do novo fluxo foram corrigidas: caminhos longos no extrator .NET; leitura de array JSON no Windows PowerShell 5.1; separação de avisos de inicialização do ambiente do retorno JSON; limite de linha de comando para o classpath Java. No runner de componentes, esconder a primeira janela SWT inviabilizava a medição de pixels: foi substituído por execução sem console, preservando a pintura da janela sintética. O teste de progresso também tinha uma cor não inicializada na fixture. As verificações afetadas foram repetidas após as correções.

## Compatibilidade sustentada pelas evidências

A matriz integral está em `COMPATIBILIDADE-JASPER-JAVA.md`, acompanhada de `compatibility/catalog.json`, `schema-deltas.json`, `results/core-matrix.json`, `features.json` e `smoke.json`.

| Camada | Estado |
|---|---|
| JasperReports Library 6.6.0–7.1.1 | 43 versões estáveis analisadas; core sintético validado nas 602 combinações registradas |
| Java 8, 11 e 16–27 | Core aprovado nos patches exatos registrados; não é certificação oficial de todas as funcionalidades |
| Java 9, 10 e 12–15 | Não testado |
| Java 6/7 | Incompatível com bytecode 52 do núcleo examinado |
| Recursos opcionais/relatórios reais | PDF/XLSX, gráficos, Groovy, JavaScript, extensões, fontes personalizadas e demais recursos não cobertos permanecem não testados/parciais |
| Complementos do Studio | Perfil de referência 6.20.6.final, Java 17.0.8.1 x64 e SWT 3.124; somente esse perfil é oferecido pelo novo instalador |
| Outros Studios/JVMs do Studio | Bloqueados até adaptação e testes; aprovar um motor isolado não libera os complementos |

O processamento usa processos JVM separados e dependências por versão. O instalador de interface não copia motores arbitrários para plugins, não instala Java/Studio e não converte relatórios. Formatos 6/7 são distinguidos; conversão e compatibilidade de `.jasper` não são presumidas. Não foi localizada uma certificação oficial de todas as combinações de Java: dados de POM/bytecode e testes próprios são identificados separadamente.

## Fontes e consulta

Consulta/reconferência em **24/11/2026**:

- [Releases oficiais Jaspersoft](https://github.com/Jaspersoft/jasperreports/releases): versão estável mais recente 7.1.1, publicada em 03/11/2026.
- [Maven Central — metadata](https://repo.maven.apache.org/maven2/net/sf/jasperreports/jasperreports/maven-metadata.xml) e [POMs/artefatos](https://repo.maven.apache.org/maven2/net/sf/jasperreports/jasperreports/): versões publicadas, dependências e hashes registrados no catálogo existente.
- [Histórico oficial](https://github.com/Jaspersoft/jasperreports/blob/master/changes.txt): recursos, alterações e datas de changelog; datas de release são guardadas separadamente.
- [README oficial e mudanças da linha 7](https://github.com/Jaspersoft/jasperreports): ruptura de compatibilidade de JRXML/JRTX e serialização.
- [Adoptium](https://adoptium.net/support): fornecedor dos runtimes preparados; patches efetivamente usados constam das evidências, sem inferir certificação da biblioteca.

Os bundles originais da referência permanecem intactos. Os portes separados usam JNA 5.14.0 e auxiliares privados do JvmDowngrader 2.0.1, com versões/hashes fixados e fontes/licenças correspondentes no pacote. O executável usa componentes do .NET Framework/Windows; motores e dependências opcionais continuam no fluxo isolado existente. O EXE local não possui assinatura Authenticode; hashes comprovam integridade, não autoria. A instalação real e a conferência visual do produto inteiro seguem o checklist `VALIDACAO-MANUAL.md`, após autorização explícita.

## Continuação em 25/11/2026

Os resultados dos portes de Studio, incluindo os adaptadores de pintura, fontes compostas e rótulos nulos, estão separados em [PORTES-STUDIO-6.md](PORTES-STUDIO-6.md). Os números e bloqueios da entrega inicial acima são históricos; o perfil efetivamente aceito depende do catálogo e dos binários incluídos em cada EXE. Nenhum porte permite reutilizar os JARs da referência em outro Studio. Nenhum instalador foi executado contra a instalação principal.
