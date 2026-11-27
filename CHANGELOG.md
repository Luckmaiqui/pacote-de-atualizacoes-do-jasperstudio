# 2.8.0 — Pacote Unificado de Personalização do Jaspersoft Studio (pré-lançamento)

## 2.8.1

- Novo assistente de instalação: uma versão por vez, verificação seguida de instalação, progresso e opção de abrir ao finalizar.
- Ícone original do Jasper no executável e na janela do instalador.
- 6.16.0 aprovada pelo usuário, sem indicação Em teste; atualização para conexões 1.9.5.port6160 com correção da abertura do leitor do Gestão.
- Contrato JSON do plano corrigido no PowerShell 5.1; plano vazio/incompleto é bloqueado e arquivos antigos não são considerados atualizados.
- Verificação de ícone, fluxo do assistente, falhas, opção de abertura e integridade do pacote antes da publicação.


- Distribuição única: ZIP com somente o EXE, seleção de múltiplas instalações e instalação obrigatória do conjunto completo.
- Detecção por diretórios e registro, seleção manual de pasta, logs e diagnóstico por linha de comando.
- Portes exatos 6.6.0, 6.16.0, 6.20.6, 6.21.0 e 6.21.5, sem fallback entre versões. Perfis parciais identificados para testes.
- Corrige a abertura do leitor da 6.16.0: URL Equinox com espaços e diretório plugins não é convertida diretamente em URI de executável.
- Leitor WebView2 compartilhado para versões sem backend integrado, com ícone do Studio, tema e botão independente da navegação SPA.
- Pesquisa na revisão do Gestão por código, nome ou endereço, reutilizando a normalização existente.
- Mantém pesquisa global limitada a seis consultas simultâneas e isolamento dos exploradores.
- Java 11.0.33+10 privado incluído para a 6.16.0, com verificação de integridade e restauração de INI em falhas.
- Mantém backup, recuperação de cópias parciais e bloqueio de JARs alterados sem mudança de versão.

Limitações: testes de componentes não comprovam fluxos autenticados reais ou inspeção visual integral. O histórico da descrição de publicação tem pendência anterior conhecida; não é declarado corrigido. A release não deve ser classificada como estável até concluir as validações manuais documentadas.

# Versão 2.3.2

- Inicializa a conexão nativa do servidor escolhido antes de abrir a pesquisa, corrigindo o acesso a getServerInfo quando getWsClient ainda é nulo.
- A conexão ocorre com progresso e cancelamento, reutiliza o cliente existente e só consulta o servidor escolhido pelo usuário.
- Inclui as correções 2.3.1 para os nós Java/MyReports e o isolamento dos filtros de projetos e repositórios.
- Mantém o diagnóstico da causa interna de falhas de autenticação/rede.

A pesquisa de conteúdo continua por servidor. Não há busca simultânea em todos os cadastros: os filtros locais pesquisam nomes/códigos das conexões e nomes/caminhos dos arquivos do workspace.

Validação local: inicialização de cliente ausente, reutilização, cancelamento, filtros de MyReports Java, expansão/restauração e independência das árvores. O funcionamento com a autenticação e os servidores reais depende da validação após reiniciar o aplicativo.

Feche o Studio e reinicie pelo Abrir JS.bat em Ordenacao-de-conexoes para carregar a correção instalada.
# Versão 2.3.1

- Corrige a filtragem de projetos Java/MyReports: resolve os recursos por trás dos nós JDT e preserva contêineres virtuais que não adaptam a IResource.
- Expande os nós reais da árvore de projetos, incluindo wrappers Java, para revelar arquivos encontrados.
- Testa explicitamente filtros simultâneos e independentes de projetos e repositórios.
- A pesquisa do JasperServer usa a API com servidor explícito, sem obter a conexão da seleção da árvore.
- Mantém o encaminhamento do recurso escolhido para o explorador e mostra a causa interna das falhas da API nativa.

Esta versão continua pesquisando o conteúdo remoto de um servidor escolhido. Não há pesquisa global simultânea em todos os servidores. Esse recurso exigiria um agregador próprio com limites de paralelismo, cancelamento, paginação, autenticação e resultados identificados por servidor. Nenhuma consulta em massa foi executada.

Validação: modelos de conexão reais e controles SWT, projeto Java simulado com a interface IJavaProject, MyReports inicialmente recolhido, filtro profundo, restauração, isolamento entre as duas árvores e contrato da API remota com implementação simulada. A autenticação e abertura da pesquisa nos servidores reais precisam ser verificadas no aplicativo.

Feche o Studio, atualize somente JS-Ordenacao e abra por Abrir JS.bat em Ordenacao-de-conexoes.
# Versão 2.3.0

- Barra de pesquisa no Explorador de repositórios: filtra nomes/códigos das conexões de bancos e JasperServer, mostrando os dois grupos.
- Barra de pesquisa no Explorador de projeto: pesquisa nomes e caminhos de projetos, pastas e arquivos do workspace, em segundo plano, com cancelamento de pesquisas antigas.
- Limpar o filtro restaura a seleção e a expansão anteriores.
- Botão Buscar no servidor e Ctrl+F no Explorador de repositórios abrem a escolha do servidor sem exigir seleção prévia na árvore, seguida da pesquisa nativa do JasperServer.
- Ctrl+F no Explorador de projeto leva ao campo de filtro.
- Não inicia buscas remotas ao digitar nos filtros locais. Tema e tradução continuam independentes.

Atualize JS-Ordenacao-v2.3.0.zip ou o pacote completo. Feche o Studio, execute Instalar.bat na pasta Ordenacao-de-conexoes e reinicie por Abrir JS.bat.

A pesquisa remota permanece por servidor: pesquisar em todos exige múltiplas requisições, paginação, limites e tratamento de autenticação/timeouts. Não foi executado benchmark nem busca em massa nos servidores reais. Esta versão adota a alternativa de pesquisa nativa com escolha de servidor. O filtro de projetos não pesquisa o conteúdo textual dos arquivos.

Validação local com controles SWT reais, modelos reais de conexões e workspace simulado: filtros, acentos, banco+servidor pelo mesmo código, nomes/caminhos, debounce, ancestrais, restauração, Ctrl+F sem seleção e encaminhamento para a busca explícita. Não foram feitas consultas remotas automáticas.
# Versão 2.2.0

- Ordenação numérica das conexões de banco de dados e JasperReports Server no Explorador de repositórios.
- Mantém pastas e relatórios na organização original, incluindo seleção e categorias expandidas.
- Novas conexões e alterações de nome usam o mesmo comparador da árvore.
- Mantém a pesquisa e a ordenação das listas do preview e dataset.

Atualize somente o pacote JS-Ordenacao. Feche o Studio, execute Instalar.bat em Ordenacao-de-conexoes e reinicie pelo Abrir JS.bat.

Validação local com TreeViewer e modelos reais MDataAdapter/MServerProfile: ordem por código, cópias, duplicatas, renomeação, expansão, seleção e preservação dos modelos e relatórios. Nenhum servidor foi acessado nesses testes.
# Versão 2.1.1

- Corrige a posição da pesquisa de conexões no preview e no dataset: abre abaixo do botão original, sem depender do foco do editor ou do cursor.
- Se não houver espaço abaixo, abre acima, respeitando o monitor do botão e os limites da tela.
- Mantém filtros por código/nome, ordenação numérica, seleção e cancelamento.

Atualize apenas a pasta Ordenacao-de-conexoes. Reinicie o Studio pelo Abrir JS.bat dessa pasta.

Validado com botão SWT e DataAdapterAction reais, vínculo ao menu original, coordenadas abaixo do botão, limites inferior/direito e monitor com coordenadas negativas. Testes anteriores de pesquisa e identidade continuam passando.
# Versão 2.1.0

- Campo de pesquisa nos seletores de conexões usados pelo preview e pelo dataset.
- Filtro por código e nome, sem diferenciar acentos ou maiúsculas; permite múltiplos termos.
- Seleção com mouse, setas e Enter; Esc cancela sem trocar a conexão.
- Mantém os eventos e os objetos originais dos adaptadores, as opções auxiliares e a ordenação numérica.
- Pesquisa local nos rótulos; não consulta bancos nem altera credenciais ou relatórios.
- Atualiza somente o complemento de conexões para 1.1.0. Tema e tradução mantêm seus binários.

Instalação: feche o Studio e execute Instalar.bat na pasta Ordenacao-de-conexoes. Reinicie pelo Abrir JS.bat dessa pasta. Temas e tradução continuam independentes.

Compatibilidade: Jaspersoft Studio Community 6.20.6, Windows, Java 17+. Testes com SWT real cobrem filtros, acentos, identidade da conexão, eventos originais, ausência de resultados, cancelamento e descarte do popup. A integração no aplicativo completo deve ser conferida após reiniciar.
# Versão 2.0.0

- Instaladores independentes para temas, tradução pt-BR e ordenação de conexões.
- Quatro temas: Salutem Noite, Grafite, Floresta e Ameixa.
- O inicializador dos temas respeita o idioma escolhido, sem forçar português.
- Tradução e ordenação mantêm suas versões e seus binários anteriores.
- Quatro downloads: temas, tradução, ordenação e conjunto com pastas separadas.
- Build preserva JARs inalterados e exige aumento de versão para código alterado.

Compatível com Jaspersoft Studio Community 6.20.6, Windows, Java 17+. Não compatível diretamente com 6.16.

Validação: integridade dos 153 JARs, 6.496 chaves de tradução, contraste das quatro paletas, alternância das cores em Gallery/ProgressMonitorPart reais e validação dos instaladores pelo Windows PowerShell 5.1. A aplicação completa deve ser reiniciada para carregar o novo complemento.
# Histórico

## 1.0.0 — 2026-11-16

Primeiro pacote versionado do projeto, reunindo:

- Tema Salutem Noite 1.7.5.
- Português (Brasil) 2.0.2, bandeira e 24 fragmentos de tradução.
- Ordenação numérica de conexões 1.0.0.
- Instalador portátil para Windows/Studio 6.20.6.
- Fontes, verificações e workflows de validação/release.

Correções mais recentes: remover `@Ctrl+S` do botão Salvar; traduzir a pergunta de salvamento preservando o nome do arquivo; escurecer o progresso da publicação; definir uma cor nativa única para rótulos desabilitados.

A antiga tentativa de splash personalizado foi revertida e não é distribuída.
