# Ampliação de compatibilidade: diagnóstico e implementação

## Escopo e conclusão do diagnóstico

Este repositório era um pacote de complementos do **Jaspersoft Studio**, não uma distribuição alternativa da JasperReports Library. O Studio hospeda o editor e os modelos JRXML, faz a compilação/preenchimento e mantém a integração com o JasperServer. Os complementos fornecem temas, tradução, pesquisa, ordenação, contas e importação de cadastros de conexões.

A importação do Gestão cadastra conexões; ela não importa nem converte arquivos JRXML. `SubreportOpener`, `SubreportInputs` e `RemoteSubreportOpener` localizam fontes locais/remotos e delegam a abertura ao editor nativo. Não havia um serviço próprio de compilação nem uma página própria de gestão de versões de motores.

Restrições verificadas:

- `installer/common/Instalar.ps1`: Studio `6.20.6.final` e Java >=17; inicializador também usa o nome exato do bundle.
- `scripts/Build.ps1`: classpath e extração da JasperReports Library 6.20.6 internos ao Studio.
- Manifesto de conexões: intervalos `[6.20.6,6.20.7)` para os bundles de dados, Studio e servidor; todos os nossos complementos têm bytecode Java 17.
- Fragmentos de tradução: versões exatas dos hosts e pacotes Eclipse Babel da plataforma de referência.
- Temas: uso de APIs internas do SWT/Win32, GEF/Draw2D, JFace, Nebula e Eclipse Forms. Retirar os bloqueios do instalador não porta essas APIs.
- JRXML/JRTX 7 e `.jasper` da linha 7 rompem compatibilidade com a linha 6. A numeração do Studio não substitui a versão da biblioteca embarcada, nem determina o motor do servidor remoto.

Não foram removidas essas proteções nem substituídos os JARs da instalação. O perfil de interface validado permanece Studio Community 6.20.6/Java 17. A instalação local 6.6.0 foi inspecionada sem iniciá-la ou modificá-la: Java 8u171 e SWT 3.106.3, em contraste com Java 17/SWT 3.124 da referência. Não se declara suporte dos temas/contas/ordenação atuais no Studio 6.6.0.

## Estratégia implementada

Foi adicionada a pasta `compatibility/`, independente dos bundles OSGi. Ela permite trabalhar com os motores nativos de cada versão sem misturá-los no classloader do Studio:

1. `Collect-Versions.ps1`: enumera versões estáveis a partir de 6.6.0 no Maven Central e nas releases oficiais do GitHub, preserva datas de publicação e de changelog separadamente e lê os POMs. O JAR é conferido contra o checksum publicado e recebe SHA256 no catálogo. O bytecode da classe central é examinado diretamente. Dependências opcionais e fornecidas pelo ambiente são identificadas.
2. `Analyze-Schemas.ps1`: compara nomes dos XSDs 6.x e chaves de propriedades padrão. A família 7 é identificada pela ausência do XSD legado e pelo modelo Jackson. Deltas não são apresentados como uma análise semântica completa de remoções.
3. `Prepare-Tools.ps1`: utiliza Java 8/17 disponíveis quando encontrados ou baixa runtimes Temurin estáveis, com checksum da API oficial, em `.compatibility/`. Maven também tem checksum conferido. Não altera PATH, registro, `JAVA_HOME` global ou o `.ini` do Studio.
4. `runtime.mjs`: resolve dependências Maven em diretórios próprios por versão/perfil. Usa apenas o repositório HTTPS do Maven Central; repositórios HTTP antigos e dependências privadas ausentes não são contornados por troca silenciosa de versões. O fechamento resolvido recebe um lock com SHA256 de cada JAR, revalidado antes de executar. Solicitações iguais compartilham a resolução dentro do mesmo processo Node.
5. `ReportWorker.java`: worker Java 8, sem ligação binária com uma versão específica da biblioteca. Cada execução carrega um único motor em um processo JVM novo; chamadas comuns são resolvidas por reflexão. O motor selecionado é conferido pelo artefato e hash, e o manifesto é informação complementar. A 6.7.0 oficial tem `Implementation-Version` 6.6.0: essa divergência é registrada, não usada para inventar a versão real.
6. `cli.mjs`: inventário, inspeção, validação e compilação. Seleção automática testa a operação com o arquivo real, respeita a família XML e não afirma a versão original. Compilação ocorre numa saída temporária; só o resultado bem-sucedido é copiado para um diretório novo, recusando sobrescrita.
7. `Test-Matrix.mjs`: testes progressivos de todas as versões e JVMs preparadas, com persistência de evidências e continuidade após falhas. `Generate-Report.mjs` gera a matriz legível sem confundir motores publicados, dependências locais, validação ou certificação.

O código de leitura, validação, edição e compilação do Jasper é **reutilizado**, não reimplementado. O worker não cria um novo editor visual: edição interativa continua no Studio compatível; a prova automatizada de edição altera o nome de uma cópia e a reabre com o mesmo motor.

## Perfis e dependências

O perfil **core** contempla leitura do formato nativo, validação de modelo, compilação Java e preenchimento/exportação XML. Ele omite explicitamente iText/OpenPDF e bibliotecas de gráficos, além das extensões opcionais não solicitadas. Não é equivalente à distribuição completa do fornecedor. A lista integral das dependências diretas por versão continua em `compatibility/catalog.json`; a lista transitiva efetivamente usada fica no lock local.

A linha 7 usa o módulo `jasperreports-jdt` correspondente, pois o compilador deixou de fazer parte do núcleo. Perfis explícitos `ecj8` e `ecj11` permitem experimentar compiladores 3.26/3.33 em fechamento separado; os resultados distinguem esse caso do perfil nativo. Não se substitui o compilador de uma instalação existente. A resolução `full` está disponível na API para diagnóstico de todos os requisitos obrigatórios, mas não foi certificada por esta bateria.

Não foram adicionadas dependências aos bundles instaláveis nem alteradas as versões dos componentes publicados. Os motores/dependências baixados não são commitados nem incluídos nos instaladores de temas, tradução ou ordenação. São usados exclusivamente nos diretórios locais ignorados.

## Preservação e limites

- Inspeção XML rejeita DTD/entidades externas e tem limite de 20 MB. Um JRXML sem marcadores suficientes fica com formato indeterminado; `exactVersion` permanece nulo.
- Relatório 6 não é convertido em 7, nem vice-versa. Nenhuma expressão, linguagem, consulta ou arquivo original é regravado. Os testes comparam o SHA256 de entrada.
- `.jasper` é específico do motor e do alvo de compilação. Ser aprovado em várias JVMs não prova portabilidade de um `.jasper` entre motores/JVMs; use o destino correto e recompile quando necessário.
- O processo separado evita conflitos de classpath, mas **não é sandbox de segurança para expressões Java arbitrárias**. Os testes usam apenas relatórios sintéticos do projeto, sem bancos, servidores ou dados de clientes.
- PDF/XLSX, gráficos, mapas, códigos de barras, Groovy, JavaScript, classes externas, fontes personalizadas e relatórios reais exigem módulos e testes específicos. Nenhum desses recursos é declarado aprovado por passar no relatório mínimo.
- A validação dos complementos nos Studios 6.6–7.x é outra frente: requer builds por plataforma/Java, adaptações de APIs, fragmentos correspondentes e testes do workbench. Permanece pendente, com bloqueios de instalação preservados.

## Manutenção

Resultado local consolidado: 43 motores × 14 runtimes (Java 8, 11 e 16–27), totalizando **602 combinações aprovadas** no escopo core. Java 9, 10 e 12–15 permanecem não testados. Seis verificações de fronteira de recursos confirmaram aceitação/recusa esperadas de styleExpression e DPI. Seis testes adicionais de catálogo, evidências, XXE, famílias de formato e preservação passaram. Os 14 testes Java existentes de contas, interface, publicação, pesquisa, ordenação e sub-relatórios também passaram, assim como o verificador dos 153 JARs publicados.

O suporte completo de todos os complementos em todos os Studios **não está concluído**. Faltam os portes/testes por plataforma do Studio e a validação das extensões/formatos de saída não cobertos. A nova camada de motores não é usada para declarar esses critérios atendidos.

Atualize o catálogo por comando explícito, examine deltas de POM/esquema/propriedades, prepare os runtimes e execute a matriz novamente. Preserve o conjunto anterior de evidências para comparação. Uma versão só deve ganhar status validado para o escopo e combinação efetivamente exercitados. JVMs não instaladas permanecem NT; bytecode mínimo não vira certificação oficial.

O novo worker é aditivo e está em desenvolvimento local na branch `compatibility/jasper-java`. Não houve publicação automática nem mudança na instalação do usuário.

Fontes: [releases oficiais](https://github.com/Jaspersoft/jasperreports/releases), [histórico oficial](https://github.com/Jaspersoft/jasperreports/blob/master/changes.txt), [POMs publicados](https://repo.maven.apache.org/maven2/net/sf/jasperreports/jasperreports/), [mudança de formato na linha 7](https://github.com/Jaspersoft/jasperreports#readme), [disponibilidade Temurin](https://adoptium.net/support). O vault `Z:` estava indisponível e não foi consultado.
