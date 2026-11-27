# Portes dos complementos para o Studio 6.x

## Porte 6.16.0 — 26/11/2026

### Correção de certificados e importação — conexões 1.9.1

A primeira aplicação pelo usuário falhou em `[IO.File]::Replace($temp,$ini,$null)`: no Windows PowerShell 5.1 o argumento nulo desse overload foi convertido em caminho vazio, causando `ArgumentException`. A falha foi reproduzida em arquivos sintéticos. O INI real permaneceu apontando para o runtime original; a cópia do Java privado não implicava sua ativação. O helper `Replace-IniFile` agora exige um caminho real e novo de backup. `tests/Test-Secure6160.ps1` passou a executar a substituição real em arquivos dentro de build, conferindo backup, recuperação byte a byte, recusa de backup existente e falha que preserva a origem. O script local foi regenerado; não foram alterados INI ou complementos em Program Files pelo assistente. Pode-se repetir o comando: os arquivos do runtime já copiados são conferidos e reutilizados.

O log real de `JaspersoftWorkspaceTeste/.metadata/.log` confirmou `PKCS12KeyStore.engineLoad` em `CertChainValidator.getDefaultTrustStore`, falhando com `NoSuchAlgorithmException: Algorithm HmacPBESHA256 not available` no Java 11.0.8. Isso ocorre antes da conexão HTTP/SOAP e explica falhas tanto da busca global como do teste dos JasperServers importados. Não é evidência de senha errada em todos os servidores. A mensagem “Studio 6.6.0” era literal em `LegacyGestaoDialog`; o JAR instalado foi conferido como `1.9.0.port6160`, portanto o texto não provava uso de um binário 6.6.0. A janela agora consulta a versão do bundle ativo.

A documentação oficial [Oracle 11.0.17](https://www.oracle.com/java/technologies/javase/11-0-17-relnotes.html), consultada em 26/11/2026, explica que PKCS12 com o MAC SHA-256 exige suporte introduzido no Java 11.0.12. Para a correção foi baixado Temurin JRE 11.0.33+10 x64 do [fornecedor](https://github.com/adoptium/temurin11-binaries/releases/tag/jdk-11.0.33%2B10), com SHA-256 `d07c70035f7462924cd385912d76d50622baa17b15e5260df8facabcf2fff998`, conferido contra a API oficial. Esta combinação específica foi testada; não se certificam automaticamente todos os patches do Java 11.

`installer/windows/Update-Secure6160.ps1` gera o atualizador local `outputs/JS-6.16.0-Teste/Atualizar-6.16.0.ps1`. O pacote inclui o Java privado e os JARs exatos, com inventário fixo de hashes. A aplicação, feita pelo usuário como administrador, copia o runtime para `runtimes/salutem-java11-11.0.33-10` dentro deste Studio, preserva o runtime original, faz backup do INI, altera somente `-vm` de forma atômica e chama o instalador transacional existente. Em falha da instalação o INI original é restaurado. Não converte/reescreve truststores, não remove certificados, não desativa TLS ou validação de integridade e não muda o Java global. O modo `-SomenteVerificar` não instala nem muda o INI. O atalho de script antigo `Instalar-6.16.0.ps1` encaminha para esta correção; o pacote anterior fica preservado para referência/recuperação.

Conexões foi versionado como 1.9.1. Os 136 JARs inalterados foram comparados por conteúdo e preservados com os hashes anteriores, evitando substituir arquivos diferentes com o mesmo nome. O novo JAR de conexões pode ser instalado com backup do anterior.

Testes: reproduzida a ausência de HmacPBESHA256 no Java 11.0.8; PKCS12 sintético com MAC SHA-256 lido pelo Java atualizado e senha errada rejeitada; argumentos originais da JVM aceitos; 17 testes de componentes passaram no Java 11.0.33; a janela real de importação analisou e exibiu as 363 conexões do JSON de Downloads, sem testar/adicionar nenhuma. Testado o helper do INI no PowerShell 5.1: alteração exclusiva de `-vm`, CRLF/LF, caminhos com `$`, idempotência e recusa de ambiguidades. Conferidos hashes e preflight dos scripts, sem instalação em Program Files. Busca autenticada real após a atualização e eventuais falhas de rede/credenciais ainda dependem de validação do usuário.

Evidências: `build/security6160-results.json`, `build/crypto6160`, `build/studio-components-232be2fb-55c8-48ae-bba8-837e2b4e3db8`, `build/studio-components-279c68b7-f535-4a73-84df-e81b6abe6f34`, `ports/studio6/results/studio-6.16.0.json`. O JSON real não foi incorporado ao repositório. `tests/Test-Secure6160.ps1`, `tests/CheckGestaoImport.java` e `installer/windows/CryptoRuntimeProbe.java` cobrem as novas verificações; CI contém os testes puros de INI e PKCS12.

O erro de histórico de publicação HTTP 405 é outra pendência e não foi corrigido nesta atualização. Z: continuou indisponível.

Instalação direta adicional: `outputs/JS-6.16.0-Teste/Instalar-6.16.0.ps1` (fora da raiz do repositório). O modelo versionável fica em `installer/windows/Install-Exact6160.ps1`. Usa um inventário SHA-256 fixo, os 137 JARs realmente compilados/testados `port6160`, o instalador transacional existente e destino exclusivo `6.16.0.final` com Java 11. `-SomenteVerificar` não instala nada. Pacote, fontes/licenças e backups ficam em `instalacao-6160`. Foi validado no Windows PowerShell 5.1 contra a instalação real; testes de recusa de versão e integridade usam somente verificação, sem instalar componentes. O seletor de `Unified.ps1` também foi alterado para recusar fallback em 6.16.0 quando não houver a referência exata; executáveis anteriores precisam de nova geração para conter essa alteração. Usar o script direto para este teste.

Instalação usada como API e runtime: `C:\Program Files\TIBCO\Jaspersoft Studio-6.16.0`. Java observado: AdoptOpenJDK **11.0.8**; SWT **3.115.0.v20200831-1002**, Windows x64. Os complementos foram recompilados especificamente, com bytecode Java 8; isso não certifica execução do Studio em Java 8. O instalador oferece esse candidato com requisito de Java 11. Não foram substituídos arquivos da instalação original.

Reutilizados os adaptadores existentes de APIs opcionais, ordenação, busca, contas e traduções por host. O porte 6.16.0 recebe os menus próprios das sete paletas, o seletor de idioma que não regrava o INI ao escolher o idioma já ativo e a alternativa de coletor externo/JSON quando não há WebView2. O legado não recebe os adaptadores exclusivos dos controles da 6.21.5.

Diferença encontrada: nesse SWT, `OS.IsDarkModeAvailable()` existe, mas retorna falso neste Windows. `Display.setData` não ativa `useDarkModeExplorerTheme` nem `comboUseDarkTheme`. `Win32Bridge` agora usa a ponte Windows já existente também quando a API retorna indisponível, com efeitos limitados ao processo e sem alterações no registro do Windows. As asserções legadas verificam o estado dessa ponte nesses casos; o teste separado de pixels de menus continua obrigatório.

Resultados:

- Compilação contra os JARs reais da 6.16.0: passou para tema, idioma e conexões.
- Suíte de 17 componentes reais: passou após corrigir a recuperação do modo escuro; 15 haviam passado antes da correção. Inclui campos de seleção, publicação/fonte de dados, ordenação/identidade, exploradores independentes, busca, contas, mapeamento do Gestão, cores personalizadas e progresso.
- Menus nas sete paletas: passou com conferência de pixels, rótulos desabilitados, comandos, imagens, atalhos e liberação de callbacks.
- Recursos de tradução: **407** recursos associados aos hosts reais, **26447** chaves, **21361** traduzidas e **4489** em inglês para revisão. Os totais não são uma certificação de tradução integral.
- Teste automático novo de geração dos adaptadores 6.16.0: passou e foi incluído no CI.
- Prévia: iniciada com configuração e workspace privados, `pt_BR`, Java 11.0.8 e os próprios JARs `port6160`. Os logs identificaram o início do Studio 6.16.0. A captura visual da ferramenta não foi suficiente para aprovar todas as telas; revisão de Luckas e testes reais continuam pendentes.

Evidências: `ports/studio6/results/studio-6.16.0.json`; logs em `build/studio-components-20e1b768-d113-478f-896e-29180ab029ef`; teste final de menus em `build/native-popup-842b91ee-ca05-4fe2-a48a-922a050fb899/results.json`.

Arquivos deste porte: `ports/studio6/{prepare-sources.mjs,Win32Bridge.java,LegacyAssertions.java,NativeSuiteApplication.java,Test-NativePopup.ps1,Package-ExperimentalPort.ps1,Preview-Studio.ps1}`, `compatibility/studio-profiles.json`, `installer/windows/Unified.ps1`, `installer/common/Instalar.ps1`, `tests/{studio616-port.test.mjs,Test-ExperimentalInstaller.ps1}` e `.github/workflows/ci.yml`. Evidências e documentação foram adicionadas sem substituir o trabalho anterior.

O perfil permanece **candidato**. O modo normal do instalador não foi liberado como certificado; para teste manual usa-se o modo experimental com a referência específica 6.16.0. A abertura privada não depende de instalar esses JARs em Program Files. Nenhuma release foi publicada.

**Pendência separada:** Luckas informou que o histórico automático da descrição não funcionou ao publicar. A janela de erro observada mostrou HTTP 405 na atualização dos metadados. Não foi efetuada publicação de teste nem corrigido esse fluxo durante o porte 6.16.0; não o considerar aprovado pelos 17 testes de componentes.

O cache de desenvolvimento foi recomposto apenas com as ferramentas necessárias, sem restaurar os ambientes Java e as cópias completas removidos na limpeza. Z: segue indisponível e não foi consultado.

## Histórico automático de publicação — candidata de 26/11/2026

Implementado no complemento de conexões **1.9.0**, dentro da candidata de pacote 2.8.0. Não instalado nem publicado no GitHub nesta etapa. A validação anterior de um porte não aprova automaticamente este código novo.

### Fluxo encontrado e integração

O assistente nativo `Publish2ServerWizard.performFinish()` executa a publicação por `IWizardContainer.run`. Navegação e descoberta de dependências usam a mesma interface; portanto interceptar toda operação registraria publicações inexistentes. O complemento decora o container somente desse assistente e reconhece a chamada de `performFinish`, preservando as páginas, o destino e o publicador original. `ConnectionUi` anexa o decorador na varredura existente e antes do evento dos botões, sem criar outro assistente.

`PublishingHistoryUi.run` identifica a Unidade de Relatório, o cliente autenticado e o fuso; `PublicationHistory.Session.execute` lê a descrição anterior, chama o publicador original e só após seu retorno bem-sucedido acrescenta o registro. Arquivos avulsos sem Unidade de Relatório não recebem histórico. Abrir, navegar e cancelar não geram entradas. A descrição não é gravada no JRXML nem exige comparação de SQL/design.

Formato: `DD/MM/AAAA HH:MM - (usuário do servidor) - Alteração no select e/ou design.` O usuário vem de `IConnection.getUser().getUsername()` quando disponível, com alternativa no usuário configurado em `IConnection.getUsername()`. Nenhum nome manual foi incluído no código de produção. O horário utiliza o cabeçalho HTTP Date da releitura posterior à publicação; na ausência dele usa o relógio real do cliente. A conversão usa o fuso configurado no perfil ou o do formatador da conexão nativa. Fuso inválido ou identidade vazia impedem iniciar a operação; não se escolhe um fuso fixo silenciosamente.

### Preservação, transporte e duplicidades

`PublicationHistory.append` compara linhas completas com igualdade exata, reconhecendo CRLF, LF e CR. Não remove espaços, descrições livres nem registros semelhantes. O conteúdo anterior é preservado literalmente e o novo registro recebe uma quebra de linha real. O ticket por assistente/destino/usuário/fuso mantém a mesma linha nas tentativas após falha parcial, inclusive se o minuto mudar. Publicações do mesmo usuário no mesmo minuto podem produzir a mesma linha e são deduplicadas conforme a regra solicitada.

`PublicationDescriptionStore` reutiliza o `WebTarget` REST v2 do Studio, inclusive seu contexto de autenticação; não extrai senhas ou sessões do navegador. O PATCH envia exclusivamente `version` e um item `description` com `field/value`, sem expressões. Não utiliza PUT do recurso inteiro nem altera parâmetros, recursos ou o banco interno do servidor.

O cliente nativo inspecionado pode repetir a gravação de um recurso após conflito usando uma versão atualizada sem mesclar seu conteúdo. Por isso o histórico usa PATCH separado: conflitos HTTP 409/412 provocam nova leitura/mesclagem, com até cinco tentativas. Um conjunto limitado de travas locais serializa este fluxo para a mesma unidade; a versão do servidor protege a atualização de metadados entre processos. A descrição é relida após gravar. Se a resposta se perder, a releitura verifica se o bloco completo já foi persistido.

### Limites reais

- Publicação do relatório e PATCH da descrição são **duas transações**. Não há garantia atômica entre elas. Se o relatório foi publicado e o histórico não foi confirmado, o usuário recebe erro explícito de histórico pendente e o assistente não conclui normalmente. Cancelar depois da publicação não desfaz o relatório já enviado.
- A trava local e o controle de versão reduzem conflitos; não impedem outro cliente ou o publicador nativo de sobrescrever a descrição fora deste protocolo. Não há bloqueio distribuído envolvendo toda a publicação do relatório.
- Nenhum limite universal de tamanho da descrição foi comprovado. O complemento não trunca. Rejeições HTTP ou releitura divergente são informadas. Um servidor que truncar por regra própria não permite ao cliente garantir recuperação automática do conteúdo anterior.
- REST v2/PATCH e permissão de leitura/edição de metadados são necessários. A documentação oficial situa PATCH no Server 5.5, mas isso não certifica todos os servidores/implantações. Não há fallback silencioso para PUT/SOAP.
- Reabertura e persistência foram testadas em armazenamento simulado; publicação e reabertura em um JasperReports Server real ainda precisam de teste autorizado.

### Arquivos e verificações desta funcionalidade

Produção: `modules/connection-order/src/com/salutem/order/{PublicationHistory,PublicationDescriptionStore,PublishingHistoryUi,ConnectionUi}.java`. O número do componente foi atualizado em `versions.json`. `scripts/Build.ps1` e `scripts/Build-Windows.ps1` selecionam somente o JAR da versão atual de conexões, preservando o artefato anterior sem empacotar IDs duplicados.

Testes: `tests/{PublicationHistoryTest,PublicationWireTest,CheckPublicationHistory}.java`, `tests/{Test-PublicationHistory,Test-PublicationWire}.ps1`, `scripts/Test-StudioComponents.ps1`, `ports/studio6/TestContainer.ps1` e `.github/workflows/ci.yml`. O teste de container usa fragmentos exclusivamente de teste; não muda os acessos/exportações dos complementos de produção.

Resultados locais de 26/11/2026:

| Verificação | Resultado e alcance |
| --- | --- |
| Histórico independente, compilado para Java 8 | Passou: novo/existente, descrição literal, três tipos de alteração, usuários, fuso, relógio do servidor, duplicidade, falha, cancelamento, reabertura, repetição, resposta perdida, concorrência e truncamento detectado. Incluído no CI. |
| Transporte JAX-RS do Studio 6.20.6 em HTTP local simulado | Passou: URI com espaço, contexto de sessão sintético reutilizado, PATCH limitado à descrição/versão, UTF-8/CRLF, cabeçalho Date, conflito e repetição. Nenhum servidor real acessado. |
| Assistente nativo 6.20.6 e 6.21.5, container OSGi privado | Passou: anexação única, navegação e cancelamento preservados, reconhecimento exclusivo de Concluir, outros assistentes e seleção de fuso. Não executa publicação remota. |
| Porte 6.6.0 | Compilou com API real e bytecode Java 8. Teste de construção do assistente excedeu 30 s durante inicialização nativa; integração pendente. Não certificado por esta etapa. |
| Regressões automáticas gerais | Passaram: paletas, ordem/identidade, tradução, abertura, cache, recuperação da instalação, coletor e seis testes de compatibilidade. Inspeção visual separada. |

A tentativa inicial de executar o assistente nativo 6.20.6 num classpath plano falhou por ausência do serviço de workspace. A execução correta em container OSGi privado passou. No teste HTTP, foram corrigidas as dependências do fixture e a leitura de corpos chunked do servidor simulado; a execução final passou. Essas falhas não foram ocultadas como validações positivas.

Fontes oficiais consultadas em **26/11/2026**, confrontadas com o bytecode e os DTOs fornecidos no Studio 6.20.6:

- [Modifying a Resource — PATCH, version e field/value](https://actiancorp.github.io/jaspersoft-docs/jasperreports-server/web-services-guide/modifying_a_resource_v2/).
- [The v2/resources Service — optimistic locking](https://actiancorp.github.io/jaspersoft-docs/jasperreports-server/web-services-guide/the_v2_resources_service/).

O vault Z: estava indisponível (`Test-Path Z:\` retornou falso). Não foi consultado; o motivo da indisponibilidade não foi determinado.

Escopo: tema, tradução e ordenação/pesquisa do **Jaspersoft Studio Windows x64**, de 6.6.0 a 6.21.5. A linha 7 não faz parte deste porte. A matriz de motores JasperReports Library em `compatibility/` tem outro escopo e não comprova a interface do Studio.

Em 24/11/2026, Luckas decidiu deixar a validação das versões intermediárias para outro momento. Esta etapa cobre as três distribuições efetivamente disponíveis abaixo.

## Resultados desta etapa

| Studio | JVM executada | SWT | Testes aprovados | Traduções dos candidatos |
|---|---|---|---|---|
| 6.6.0 | Java 8u171 x64 | 3.106.3.v20180329-0507 | 17 | 139 fragmentos; 458 recursos carregados pelo OSGi |
| 6.20.6 | Java 17.0.8.1 x64 | 3.124.0.v20230523-1044 | 16 | Regressão do pacote original; inventário preservado |
| 6.21.5 | Java 17.0.8.1 x64 | 3.126.0.v20240528-0813 | 17 | 145 fragmentos; 481 recursos carregados pelo OSGi |

Os 50 testes passaram. Os candidatos têm registros de hashes e escopo em [6.6.0](../ports/studio6/results/studio-6.6.0.json) e [6.21.5](../ports/studio6/results/studio-6.21.5.json). Os controles das versões antigas são exercitados diretamente no SWT; o carregamento de recursos usa o contêiner OSGi real. Na 6.21.5, 13 casos usam o contêiner e quatro testes de funções independentes usam o classpath das APIs reais. O teste do seletor de fontes inicia também um workbench de teste, pois o controle do fornecedor depende do gerenciador de temas. O campo executionModes distingue os modos executados. Não se trata de 50 testes de relatórios de produção ou de certificação de todas as operações do Studio.

A suíte cobre seleções nativas, 100 criações/descartes de controles e abertura repetida de combos, sete paletas, publicação em controles sintéticos, ordenação e seleção preservadas, independência dos exploradores, pesquisa agregada/cancelamento, contas sintéticas, resolução de caminhos de sub-relatórios, importação sintética, cores personalizadas e rótulos desabilitados.

Também passaram as regressões automáticas existentes, o teste da migração de traduções (incluindo o caso fragmento/hospedeiro), a execução desse gerador no Java 8u171, a conferência dos 153 JARs/49 recursos/6496 chaves do inventário original e os testes do executável/planos de instalação. Nenhum instalador foi aplicado à instalação principal.

As decisões de login/privacidade foram concluídas pelo usuário. As duas prévias abriram o relatório sintético `compatibilidade.jrxml`, exibiram o editor, a paleta preenchida e traduzida, as propriedades e os campos de pesquisa dos exploradores. O tema Noite foi aplicado pela interface. O papel e o texto do relatório permaneceram preservados. Isso comprova a inicialização básica, não os fluxos reais com servidores.

A revisão inicial encontrou texto ilegível nas barras e no seletor Relatório Principal. O adaptador de pintura corrigiu esses dois casos, conferidos nas prévias reais. Nas duas versões, o relatório sintético foi compilado e preenchido com um registro vazio, sem alterar o papel ou o conteúdo original. As duas versões ainda apresentam menus nativos claros e bordas brilhantes neste ambiente, por isso continuam parciais e bloqueadas no instalador. A evidência visual em `ports/studio6/results/ui-initialization.json` identifica o conjunto exato observado e as pendências restantes.

A inspeção do bytecode do SWT 3.106.3 confirmou que o tratamento `NM_CUSTOMDRAW` de `ToolBar` não configura a cor do texto dos itens, ao contrário do comportamento esperado pelo complemento recente. Essa diferença explica por que chamar apenas `ToolBar.setForeground` não basta nesse controle legado. Foi implementado um adaptador de pintura para os itens com texto, preservando o processamento nativo, os ícones, os comandos e as cores armazenadas. Os testes específicos conferem pixels, seleção, restauração ao desativar o tema e liberação dos callbacks. A barra ficou legível nas duas prévias reais. A prévia antiga usa `EclipseStarter`, com uma entrada Java 8 separada, para evitar o tratamento incorreto de URLs de extensões pelo launcher antigo em caminhos com espaços. Os componentes originais não foram substituídos.

## Estratégia implementada

Arquivos principais desta etapa:

- Build/adaptação: `ports/studio6/Build-Port.ps1`, `Backport.ps1`, `prepare-sources.mjs` e `toolchain.json`.
- Diferenças de plataforma: `Win32Bridge.java`, `LegacyWindowsDark.java`, `LegacyLabelPaint.java`, `LegacyToolbarColors.java`, `ModernCompositeColors.java`, `StudioApi.java` e `LegacyAssertions.java`, dentro de `ports/studio6/`.
- Tradução e regressões: `PortTranslations.java`, `Test-PortTooling.ps1`, `NativeSuiteApplication.java`, `TestContainer.ps1`, `tests/PortTranslationsTest.java` e `scripts/Test-StudioComponents.ps1`. `tests/CheckExplorerSearch.java` passou a aguardar a conclusão do filtro em vez de usar esperas fixas curtas.
- Prévia e evidências: `Preview-Studio.ps1`, `StudioPreviewLauncher.java`, `Record-Evidence.ps1`, `provenance.json` e `results/`, dentro de `ports/studio6/`.
- Integração/documentação: `.github/workflows/ci.yml`, `.gitignore`, `compatibility/studio-profiles.json`, `scripts/Build-Windows.ps1`, `README.md` e este documento. O documento da matriz de motores passou a apontar para as evidências separadas do Studio.
- Empacotamento por versão: `Package-VerifiedPort.ps1`, `installer/windows/Unified.ps1`, `installer/common/Instalar.ps1`, `tests/Test-PortPackaging.ps1` e `tests/Test-WindowsInstaller.ps1`. `tests/CheckLauncher.ps1` carrega também os valores padrão do perfil ao testar as funções extraídas do instalador.

As fontes e os binários publicados dos três módulos foram preservados; as adaptações são aplicadas a cópias no build. O trabalho local anterior no worker, no instalador e nos demais testes não foi descartado.

`ports/studio6/Build-Port.ps1` compila uma cópia das fontes contra os JARs da distribuição de destino. Nunca substitui os JARs do Studio. Os complementos recebem um qualificador por plataforma, como `.port660`, e dependências limitadas à versão de destino. Uma versão intermediária exige sua própria distribuição, compilação e testes; não é liberada por estar entre dois números já avaliados.

A compilação usa Java 17. O JvmDowngrader 2.0.1 adapta o bytecode e as chamadas de APIs Java novas para Java 8. Somente os auxiliares necessários ficam dentro de cada complemento, sob um namespace privado. Todos os arquivos `.class` do resultado são conferidos para o limite 52. A JVM e as bibliotecas da instalação principal permanecem intactas.

Diferenças tratadas:

- APIs nativas `DrawText` e modo escuro variam entre versões de SWT. A ponte consulta a API disponível; nas versões antigas, usa JNA 5.14.0 para funções Windows por processo. O caminho legado exige Windows x64, build 18362 ou posterior, e verifica a disponibilidade antes de usar essas funções. Não altera configurações globais ou o registro do Windows.
- O SWT antigo não tem a mesma configuração de rótulos desabilitados. O adaptador conserva valor, fonte e estado e restaura o estilo nativo ao habilitar o componente.
- Hooks de seleção liberam os callbacks com segurança. Isso evita esgotar os slots nativos em versões antigas ao criar/destruir muitos controles.
- Gson antigo usa a API de análise de JSON disponível naquela distribuição.
- Metadados opcionais de conexões são consultados somente quando existem; ausência não é confundida com um valor conhecido.
- Os seletores de fonte da 6.21.5 usam JSSTableCombo, um Composite do fornecedor com filhos ocultos. O adaptador usa seus getters públicos, aplica contraste ao StyledText e à tabela interna e apenas lê o estado de herança; não muda fonte, seleção nem opções. A coluna de amostras também recebe contraste calculado por célula, preservando texto, fonte e cor de fundo. Um listener por popup reaplica o contraste depois que o fornecedor recria as linhas ao abrir a lista; ele é removido quando a tabela é descartada. O teste constrói a subclasse real criada por WritableComboTableViewer em workbench isolado e verifica as sete paletas. As extensões de inicialização do produto são desativadas apenas nessa fixture; a abertura completa continua sendo conferida separadamente na prévia. Rótulos opcionais nulos são preservados sem tentativa de tradução/escrita e têm uma regressão específica.
- Na 6.6.0 não há SWT Edge. A importação do Gestão conserva a alternativa de coleta externa/importação JSON, com uma mensagem clara; não usa o navegador Internet Explorer como substituto.

## Traduções por plataforma

`PortTranslations.java` lê os recursos em inglês dos hosts reais. Uma tradução existente só é reaproveitada quando a chave, o texto de referência e os marcadores de parâmetros continuam compatíveis. Textos novos ou alterados ficam em inglês para revisão. Isso evita traduções incorretas ou perda de argumentos.

Os fragmentos gerados são vinculados à versão exata do host. O relatório `translation-fragments/coverage.tsv` registra a cobertura. Contagens de textos mantidos em inglês incluem nomes técnicos; não equivalem automaticamente a erros de tradução.

Quando o componente de origem já é um fragmento, a tradução é anexada ao seu hospedeiro real. O caso dos exemplos (`com.jaspersoft.studio.doc.samples`) tem teste de regressão específico. Colisões entre recursos provenientes de componentes diferentes interrompem a geração, em vez de sobrescrever uma tradução silenciosamente.

O teste sintético `PortTranslationsTest.java` verifica textos alterados, marcadores divergentes, chaves novas, preservação de recursos e vínculo exato. Ele pode rodar no CI. A verificação `PortResourceBindings`, no contêiner Eclipse, compara SHA-256 dos recursos encontrados pelo host com os fragmentos gerados. Essa verificação de carregamento não substitui a revisão dos textos na interface.

## Como reproduzir os testes

Pré-requisitos de desenvolvimento: PowerShell 7.2+, Node.js, Java de build configurado em `.compatibility/tools.json` e uma distribuição Windows da versão a testar. O build reaproveita o compilador ECJ da referência 6.20.6. As ferramentas externas são fixadas por URL e SHA-256 em `ports/studio6/toolchain.json`.

```powershell
./ports/studio6/Build-Port.ps1 -StudioPath 'C:\copia-de-testes\Studio'
./scripts/Test-StudioComponents.ps1 -StudioPath 'C:\copia-de-testes\Studio' -PortBuild 'build/studio6-latest-6.21.5.json'
./ports/studio6/Test-PortTooling.ps1 -JavaHome 'C:\caminho\do\jdk'
```

Nenhum desses comandos instala complementos. As saídas ficam em `build/`. A suíte nativa utiliza controles sintéticos, perfil e área de trabalho isolados. Suítes de pixels/foco devem rodar sequencialmente: uma trava recusa execução simultânea que poderia fechar os popups do outro teste.

`ports/studio6/Preview-Studio.ps1` prepara uma configuração separada para conferir o workbench inteiro. Exige o build e o `results.json` correspondente, todos os testes obrigatórios aprovados e os mesmos hashes de módulos/JVM. Sem `-Launch`, apenas prepara os arquivos. Com `-Launch`, abre uma instância vazia, em português e com tema Noite, usando workspace e perfil próprios. A preferência de tema é semeada somente no workspace novo de teste: `pluginCustomization` sozinho não garantia a ativação no primeiro início. O inventário parte da distribuição original e inclui somente os complementos candidatos; não inclui fragmentos de testes ou customizações externas já registradas na instalação. Esse fluxo não instala arquivos na distribuição principal. A preparação e a abertura devem ser registradas separadamente da inspeção visual.

O executável aceita inventários separados por versão via `Build-Windows.ps1 -PortBuilds`. O empacotamento exige perfil `validated-port`, os 17 testes obrigatórios, conferência básica aprovada sem achados bloqueadores e o mesmo conjunto de hashes. Inclui somente os JARs finais, nunca intermediários do backport. Um perfil aprovado cujos binários não foram incluídos no EXE é marcado `not-included` na cópia interna do catálogo. A inspeção jamais reutiliza os JARs da referência em outro Studio. Os dois portes continuam bloqueados: a revisão real também encontrou menus nativos claros na 6.21.5. Os testes de estado das propriedades do SWT não comprovam a pintura nativa do menu; esse limite está explícito na evidência visual. O CI valida essas regras com evidências inteiramente sintéticas; tais fixtures não aprovam um perfil real.

Em execuções assistidas, `-Wait` mantém o processo de acompanhamento e grava `stdout.log` e `stderr.log` ao término. Um encerramento com erro informa o código de saída e a pasta dos logs. O bootstrap da prévia legada é compilado separadamente contra o framework de destino; não entra nos módulos nem nos instaladores. As telas de login e de privacidade devem ser tratadas pelo usuário.

## Limites de evidência

- Compilar para Java 8 não certifica uma distribuição recente do Studio em Java 8. O Studio 6.21.5 fornecido usa Java 17.0.8.1; suas dependências do Eclipse continuam com seus próprios requisitos.
- O arquivo `.dmg` fornecido contém a distribuição macOS. Foi utilizado inicialmente para análise das APIs comuns. Os resultados Windows devem se basear na distribuição `.exe` fornecida depois, extraída sem executar seu instalador.
- Credenciais dos testes são sintéticas. Não foram testados servidores reais, senhas reais ou o armazenamento criptografado completo de cada distribuição.
- Resta a conferência visual do workbench completo, dos fluxos reais de publicação/sub-relatórios/importação e das cores em cada versão candidata.
- Os instaladores existentes continuam bloqueando versões sem perfil aprovado. Candidatos de porte não autorizam instalar os JARs de 6.20.6 em outro Studio.
- A unidade `Z:` não estava acessível (`Test-Path Z:\` retornou falso). A máquina que a hospeda pode estar desligada ou fora da rede; a causa não foi confirmada. As regras e notas adicionais do vault não puderam ser consultadas. O anexo “Texto colado(1).txt” não foi localizado nas fontes acessíveis.

## Fontes e ferramentas

Consulta em 24/11/2026:

- [Downloads oficiais](https://community.jaspersoft.com/download-jaspersoft/community-edition/): identifica a última distribuição Studio 6.x como 6.21.5, publicada em 11/07/2025.
- [Notas oficiais do Studio 6.21.5 para Windows](https://community.jaspersoft.com/download-jaspersoft/community-edition/jaspersoft-studio_windows_6.x): mudanças da plataforma, remoção de SOAP e dos fragmentos antigos de internacionalização. A importação/pesquisa remota depende das APIs disponíveis nessa versão.
- [JvmDowngrader 2.0.1](https://github.com/unimined/JvmDowngrader/tree/2.0.1): adaptação de bytecode/APIs, LGPL-2.1-or-later. Hash do executável conferido com o digest publicado no release; API Java 8 conferida com o checksum publicado no repositório Maven do fornecedor.
- [JNA 5.14.0](https://github.com/java-native-access/jna/tree/5.14.0): Apache-2.0 ou LGPL-2.1-or-later. Artefato conferido com checksum publicado e SHA-256 fixado localmente.
- [7-Zip 26.06](https://github.com/ip7z/7zip/releases/tag/26.06): ferramenta portátil utilizada somente para extrair os arquivos. Seu instalador não foi executado.

As novas dependências estão confinadas aos candidatos. O empacotamento inclui as licenças, fontes correspondentes fixadas por SHA-256, fontes adaptadas, objetos anteriores ao backport e instruções de relink dos componentes incorporados. A geração falha quando esse material está ausente ou diverge do inventário. Nenhuma release foi publicada por esta etapa.

O runner passou a evitar a inicialização completa do Eclipse nos quatro testes de funções independentes, mantendo o mesmo limite de tempo e as verificações. Uma execução anterior registrou timeouts de inicialização em dois desses casos; os logs foram preservados em build/. Isso não foi tratado como aprovação. Os componentes que dependem de serviços Eclipse continuam no contêiner real.

Na 6.21.5, o runner reaproveita do INI original somente as opções --add-exports, --add-opens e --add-modules necessárias às bibliotecas da distribuição (incluindo o serializador Castor). A evidência registra esses argumentos. Não são copiadas opções arbitrárias -D, credenciais ou caminhos do INI para o teste. A ausência dessas opções em uma fixture anterior gerou IllegalAccessException; os logs anteriores foram preservados, e a execução corrigida é registrada separadamente.

Consulta adicional em 25/11/2026: o [código nativo do SWT](https://github.com/eclipse-platform/eclipse.platform.swt/blob/master/bundles/org.eclipse.swt/Eclipse%20SWT%20PI/win32/library/os_custom.c) valida as funções internas do Windows antes de informar disponibilidade do modo escuro. Não se deve confundir disponibilidade configurada no Display com aprovação da pintura nativa. A resposta efetiva da distribuição fornecida é registrada como diagnóstico nos testes; nenhuma proteção do SWT foi removida para liberar o perfil.

## Correções focadas da 6.21.5 — 25/11/2026

Luckas confirmou visualmente dois problemas: menus da barra superior claros e falha de carregamento do Gestão.

- **Menus:** a preferência nativa de modo escuro não colore os drop-downs da barra superior nessa distribuição. Reproduzido separadamente: menu de contexto escuro, drop-down claro; aplicar somente fundo/fonte provoca sombra branca no texto desabilitado. `NativePopupColors.java` usa WM_DRAWITEM/WM_MEASUREITEM e SetWindowSubclass/DefSubclassProc para desenhar uma única vez cada linha desses drop-downs, incluindo os estados desabilitado/marcado, ícones, atalhos e mnemônicos. Os menus de contexto principais conservam o tratamento nativo. A adaptação só entra na cópia da versão 6.21.5.
- **Gestão:** o processo da prévia foi iniciado com acesso de rede restrito. O DNS retornou “Acesso negado”; com a permissão de rede concedida, o host resolveu e a página `/management` respondeu HTTP 200. Não houve alteração de DNS, proxy, certificado, endereço ou credenciais. A prévia precisa ser reiniciada com esse acesso; a sessão/login permanece sob controle do usuário.

O teste `Test-NativePopup.ps1` passou contra o JAR final: sete paletas com amostragem dos pixels do próprio menu, ausência da sombra branca, texto desabilitado presente, comando nativo, mnemônico, IDs, imagens e estado marcado preservados, restauração do tipo dos itens e liberação do callback. Menus de contexto foram conferidos apenas quanto à preservação do estilo/estado; sua aparência e os fluxos completos continuam na conferência manual. Evidência separada: `ports/studio6/results/menus-6.21.5.json`. O caso `CheckDisabledRendering` também passou; o inventário original permaneceu íntegro. Testes intermediários com timeout/falhas da fixture foram preservados em build, sem serem considerados aprovação.

A pedido de Luckas, não foi repetida a suíte inteira de ordenação/tradução para esta mudança de tema. `Preview-ThemePatch.ps1` verifica a referência anterior de 17 casos, o JAR exato e a JVM do teste focado e altera somente o URI do tema em uma prévia privada fechada. Mantém os JARs de tradução/ordenação já testados, as conexões de teste e o workspace. A evidência anterior completa não é sobrescrita nem atribuída ao JAR novo. Essa candidata continua aguardando a validação manual; o instalador público permanece bloqueado para o perfil. Nenhuma release foi publicada ou instalação principal modificada.

Referências de implementação, consultadas em 25/11/2026: [menus owner-drawn](https://learn.microsoft.com/en-us/windows/win32/menurc/using-menus#creating-owner-drawn-menu-items), [WM_DRAWITEM](https://learn.microsoft.com/en-us/windows/win32/controls/wm-drawitem), [WM_MEASUREITEM](https://learn.microsoft.com/en-us/windows/win32/controls/wm-measureitem). DPI elevado, leitor de tela e todas as combinações de submenus ainda exigem conferência manual.
A correção final de menus também usa fundo transparente no DrawText (SetBkMode/TRANSPARENT) e o teste nativo rejeita retângulos claros por trás dos rótulos. A prévia permite aplicar revisões manuais sucessivas do tema somente quando conserva a evidência do JAR anterior e mantém os demais componentes da referência. Essa atualização não modifica o perfil de compatibilidade do instalador.

Conferência final na janela real: menu Janela com fundo escuro e rótulos legíveis, sem os retângulos claros; o comando de importação abriu a janela e o WebView carregou a página de Gestão de Clientes. Nenhuma senha foi lida/preenchida e a importação efetiva foi deixada ao usuário. Registro focado: ports/studio6/results/menu-workbench-6.21.5.json e gestao-network-6.21.5.json. Isso não equivale à aprovação do perfil completo ou de todos os fluxos do Studio.

## Correções focadas da 6.6.0 — 25/11/2026

### Menus

O SWT 3.106.3 da distribuição 6.6.0 não oferece os mesmos métodos de cores de Menu do SWT recente. O adaptador dos drop-downs agora também é gerado especificamente para 6.6.0, com DrawText via Win32Bridge e gerenciamento/restauração de MENUINFO.hbrBack quando os setters de cores não existem. Não há troca de SWT, remoção de proteções ou alteração dos JARs originais. Menus de contexto continuam com o tratamento anterior; a fixture verifica sua preservação, não certifica todos os seus pixels.

### Importação do Gestão

A ausência de SWT.EDGE foi confirmada na API real da 6.6.0. O botão Abrir Gestão e Ler agora abre LegacyGestaoDialog com Abrir Gestão no Navegador, Copiar Coletor e Importar JSON. Reutiliza os métodos de coleta e reconciliação existentes; não usa Internet Explorer, não extrai a sessão do navegador e não adiciona conexões automaticamente. O usuário realiza o login no navegador externo e exporta o JSON pelo coletor existente. A leitura real da página, exportação e importação com conexões reais continuam para validação do usuário.

### Português já ativo

O comando nativo executa ConfigurationManager.changeLocale antes de verificar qualquer alteração. ConfigurationManager monta o caminho do INI a partir de Platform.getInstallLocation e appName; no bootstrap privado observado, o caminho resultou em plugins/Jaspersoft Studio.ini. A contribuição de Português recebe um comando próprio com parâmetro de locale declarado e dependência explícita de org.eclipse.core.runtime. SelectedLocaleHandler retorna sem gravar arquivos se Platform.getNL já for pt_BR. Para uma mudança efetiva de outro idioma, conserva o comando nativo do Studio: sua gravação/reinicialização ainda depende do launcher e de um INI acessível. Esse fluxo entre idiomas não foi certificado pela presente correção, que resolve a seleção de português já ativo na prévia.

### Testes e limites

- JARs gerados contra a distribuição real 6.6.0, bytecode/API Java 8; runtime Oracle 8u171 original.
- Test-NativePopup: sete paletas, pixels do próprio processo, sem sombra branca em texto desabilitado; comandos, mnemônicos, imagens e estados preservados; restauração/liberação verificadas.
- Test-PortTooling: geração separada dos perfis, registro/parâmetro do comando e fluxo legado presentes, preservação dos placeholders e fallback de traduções.
- Suite focada: CheckNativeSelection, CheckReview270 e PortResourceBindings. O último verifica os 458 recursos anexados aos hosts reais e inicializa o diálogo legado, além de acionar a seleção de português já ativo sem workbench/INI gravável.
- A primeira integração revelou dependência ausente de Platform; corrigida no manifesto gerado. A conferência do workbench revelou parâmetro não declarado no comando; corrigido e coberto pelo teste. Esses resultados intermediários permanecem em build e não contam como aprovação.
- Uma execução de CheckNativeSelection falhou ao manter o combo aberto na primeira atualização. A repetição com os mesmos JARs passou, assim como execuções anteriores. A causa da intermitência não foi confirmada; a conferência manual das caixas permanece necessária. O teste não foi enfraquecido.
- Preview-LegacyFix.ps1 altera somente três URIs de módulos em uma prévia privada fechada, valida hashes/resultados/JVM, preserva workspace/conexões/fragmentos, faz backup e restaura a configuração/evidências em falhas. Não substitui a referência histórica dos 17 testes nem libera o perfil do instalador público.
- Z: permaneceu indisponível; não foi possível consultar o vault nesta correção. A causa da indisponibilidade não foi confirmada.

Arquivos desta revisão: ports/studio6/{prepare-sources.mjs,NativePopupColors.java,NativePopupTest.java,Test-NativePopup.ps1,StudioApi.java,LegacyGestaoDialog.java,SelectedLocaleHandler.java,NativeSuiteApplication.java,Test-PortTooling.ps1,Preview-LegacyFix.ps1}, scripts/Test-StudioComponents.ps1 e este documento. As fontes dos módulos de referência não foram substituídas.

Conferência da prévia: menu Janela e submenu Idioma escuros, seleção de Português (Brasil) sem erro do INI; orientação de importação externa aberta. Evidências focadas: ports/studio6/results/menus-6.6.0.json e workbench-fixes-6.6.0.json. Três testes da rodada final passaram em build/studio-components-357629d9-ff01-4810-b366-996bc7071f41/results.json. O teste nativo do JAR final passou em build/native-popup-97271349-7c9a-41d2-9f04-ab4672d4bb17/results.json. A janela 6.6.0 ficou aberta para conferência do usuário; nenhuma conexão real foi adicionada.
