# Importar Conexões do Gestão — candidata 2.7.0

Acesso: **Janela → Importar Conexões do Gestão**, pelo botão do seletor de banco no Preview/Dataset ou pelo botão junto do campo Conta dos assistentes JDBC/JasperServer.

## Etapas

1. Cadastre as contas em Contas de Banco de Dados e JasperServer. As duas listas são independentes.
2. Clique em Abrir Gestão e Ler. Acesse o Gestão e realize o login. A sessão interna do Edge/WebView2 pode ser diferente da sessão do navegador comum; nenhuma sessão é extraída do navegador.
3. Abra a lista de clientes e clique em Ler Conexões. O coletor começa pela primeira página e percorre a paginação, sem modificar registros do site.
4. Se o Edge/WebView2 não estiver disponível no Jasper, use Coletor Para o Navegador. O botão copia o endereço de um favorito JavaScript; a janela mostra como criá-lo. Execute esse favorito na página do Gestão já autenticada e importe o JSON baixado.
5. A visão inicial **Pendentes** mostra conexões ausentes, incompletas, com erro de teste ou que exigem revisão. Cadastros completos ficam em **Cadastradas**; **Todas** reúne os dois grupos. O padrão (000) Nome ou 000 - Nome aplica-se somente às novas adições. Nomes manuais existentes são preservados.
6. Para novas conexões, selecione a conta de Banco ou de JasperServer e use Testar Marcados. Para uma conexão existente, o teste usa sua configuração nativa salva, sem exigir a escolha de outra conta. O teste é sequencial e cancelável; usa cópias das configurações, com limites de tempo, sem consultas de negócio ou publicação.
7. Use **Adicionar Testadas**. Somente novas conexões com teste OK são adicionadas; a presença é conferida novamente no início da gravação. A lista é atualizada automaticamente, e os itens adicionados passam para Cadastradas.

## Padrões confirmados por Luckas

| Tipo | Driver | Banco |
|---|---|---|
| Hospitalar | org.postgresql.Driver | salutem_hospitalar |
| Ambulatorial | org.postgresql.Driver | salutem |
| Painel | org.postgresql.Driver | painel |

Hosts e portas vêm exatamente dos campos do Gestão. Esquemas, caminhos ou tipos não reconhecidos ficam pendentes. Homologação não é presumida como produção.

## Duplicidades e atualização

- Banco: compara host, porta e banco, sem confundir as opções adicionais da URL JDBC com a identidade da conexão. Considera cadastros nas preferências e nos projetos carregados.
- Servidor: compara protocolo, host, porta e caminho, normalizando maiúsculas do protocolo/host, porta padrão e barra final. O caminho mantém sua capitalização e codificação. Vários cadastros para a mesma URL são preservados; o importador não escolhe arbitrariamente usuário ou organização para testá-los.
- Mesmo endereço repetido dentro do mesmo cliente é deduplicado. Endereço compartilhado entre códigos de clientes distintos fica pendente, sem descartar silenciosamente um cliente.
- Um cadastro encontrado pelo endereço não é recriado nem alterado. Nome, credenciais, organização, driver, opções e URL existentes são preservados. Há cadastro completo e conectividade ainda não verificada: somente um teste explícito bem-sucedido produz o estado Válida.
- Um nome já usado por outro endereço é recusado. Mudanças de host/porta de cadastros existentes exigem revisão manual nesta versão; não é seguro inferir uma migração só pelo nome.
- A aplicação usa os repositórios nativos por item, sem transação global. Em falha de gravação, confira o item indicado; os demais resultados são apresentados no resumo.
- O índice é capturado uma vez por lote e atualizado em memória a cada adição. Um bloqueio comum impede duas importações simultâneas nesta instância de cadastrar o mesmo endereço. Alterações feitas por outros processos diretamente nos arquivos nativos permanecem sob as regras do fornecedor.
- Eventos nativos de adição, edição e remoção atualizam a listagem. Alterações relevantes na configuração invalidam o teste anterior; renomear um cadastro não descarta um teste de conectividade inalterado. Resultados de teste são temporários, durante a janela de importação.
- Cadastros incompletos ou com teste malsucedido permanecem pendentes para revisão no Explorador de Repositórios, sem oferecer uma segunda adição para o mesmo endereço. Todos os cadastros continuam disponíveis no explorador nativo.
- Nenhuma conexão ausente na página é excluída. Não existe sincronização automática em segundo plano: rode o botão quando desejar revisar novamente.

## Dados e proteção

O coletor lê somente código/nome do cliente, nome do sistema, host/porta de banco e URL Jasper. Não lê senhas, cookies, tokens, contatos nem dados de SSH. O JSON contém endereços de infraestrutura: guarde-o localmente, sem adicioná-lo ao GitHub.

As contas são lidas do Armazenamento Seguro do Eclipse após seleção explícita. Novas conexões usam os repositórios nativos do Studio; suas senhas são armazenadas como referências UUID nas categorias nativas net.sf.jasperreports.data.adapter e com.jaspersoft.studio.jrserver, com o valor protegido no armazenamento seguro. O teste de servidor usa uma referência temporária, removida ao terminar. Senhas não são exportadas no JSON, logs nem pacotes.

Não há IA, chamadas de modelo ou uso de tokens durante leitura, teste e importação. Há acesso normal ao Gestão e às conexões escolhidas para teste.

## Limites de validação

O formato e os seletores foram baseados na página autenticada inspecionada. Os testes automatizados usam um DOM sintético com essa estrutura, paginação, autenticação ausente e erro de carregamento. O cadastro e os testes de conexões reais ainda precisam de validação após a instalação. Alterações futuras do HTML do Gestão podem exigir manutenção do coletor.

A reconciliação foi testada com conexões sintéticas e com os serviços nativos do Studio em configuração/workspace isolados: classificação, visão de cadastradas, preservação de nomes e atualização automática após adicionar, editar e remover. Quatro importações concorrentes do mesmo endereço produziram uma única gravação. Nenhum banco de cliente foi acessado nesses testes.

Compatibilidade: Studio Community 6.20.6, Windows e Java 17. O navegador interno depende de Edge/WebView2; a alternativa JSON permite usar a sessão autenticada do navegador comum sem copiar cookies.

A identificação usa o ID exibido no cartão do cliente e, na ausência dele, o prefixo numérico do título. Se ambos existirem e divergirem, ou se não houver identificação, o cadastro é ignorado e listado nos avisos de revisão; os demais continuam sendo coletados.
