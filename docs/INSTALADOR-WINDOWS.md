# Instalador unificado 2.8.1

A distribuição consiste em um ZIP contendo somente JasperStudio.exe. Os módulos continuam separados internamente, mas tema, tradução, ordenação, pesquisa, contas e Gestão são instalados juntos. Não é necessário executar BATs. O próprio Studio não é instalado nem seus JARs originais são substituídos.

## Uso

1. Extraia o ZIP em uma pasta gravável e abra JasperStudio.exe.
2. Leia as novidades e clique em Seguir. Escolha uma instalação na lista; o caminho aparece abaixo. Selecionar Outra Pasta permite incluir uma instalação personalizada.
3. Salve seus relatórios e feche a versão escolhida. Clique em Verificar: a verificação interna é obrigatória e, se aprovada, inicia a instalação automaticamente. O Windows solicita administrador para alterar Program Files.
4. Acompanhe as etapas na barra de atividade: verificação, instalação com backup e conferência dos arquivos instalados. A barra é indeterminada durante o trabalho; não simula uma porcentagem de cópia.
5. Qualquer falha interrompe as próximas etapas e apresenta a tela Não foi possível concluir, com Mostrar Logs e Voltar. O mecanismo de backup e recuperação continua ativo.
6. Após o sucesso, Abrir o Jaspersoft Studio ao finalizar vem marcado. Finalizar abre a versão instalada e fecha o assistente; desmarcar fecha sem abrir o Studio.
7. Uma instalação é processada por vez. Para outra versão, execute novamente o assistente. Relatórios, conexões e credenciais não são incluídos na distribuição.

## Portes exatos

O pacote contempla 6.6.0, 6.16.0, 6.20.6, 6.21.0 e 6.21.5. Cada porte usa seus próprios JARs e fragmentos de tradução. Não se escolhe a referência numericamente mais próxima. Uma versão sem seu porte exato é recusada.

A lista do assistente mostra somente o número da versão, incluindo 6.16.0, aprovada pelo usuário após a correção do Gestão. Essa apresentação não altera os limites registrados na matriz de evidências nem as proteções internas. 6.20.6 é a referência Java 17.0.8.1; 6.6.0 utiliza Java 8, 6.16.0 Java 11 e 6.21.0/6.21.5 Java 17. O bloqueio de autenticação do produto 6.21.0 é independente dos testes de seus componentes.

Na 6.16.0, o instalador incorpora Java 11.0.33+10 privado para o suporte a HmacPBESHA256; ele altera apenas -vm dessa instalação, com backup do INI, sem alterar o Java do Windows, certificados ou configurações TLS. Em falha posterior, o INI anterior é restaurado; os arquivos do runtime privado podem permanecer sem uso para diagnóstico.

## Gestão e limitações

Na revisão local do instalador, o ícone original do Studio está incorporado ao executável e à janela. A verificação do Windows PowerShell 5.1 exige uma lista JSON completa de arquivos: um plano vazio, incompleto ou divergente interrompe a operação, sem informar falsamente que o pacote está instalado.

Na 6.16.0, o complemento de conexões 1.9.3 usava diretamente a URL Equinox como URI e procurava o executável sob plugins. O complemento 1.9.5 incluído nesta revisão trata espaços na URL e localiza o executável na pasta correta. O instalador guarda a versão anterior no backup ao atualizar; é necessário fechar e reabrir essa instalação para carregar o complemento novo.

Nos Studios sem backend Edge no SWT, um único leitor WebView2 externo é reutilizado, com título comum, ícone do próprio Studio e cores da janela de origem. É necessário Microsoft Edge WebView2 Runtime instalado. Seu perfil de navegação é próprio; pode pedir login. O coletor não extrai cookies, tokens ou senhas. Coletor Para o Navegador e Importar JSON continuam disponíveis.

O leitor não depende da rota /management para habilitar o botão. O domínio HTTPS é validado e os cartões da lista de clientes devem estar presentes. O filtro da revisão pesquisa código, nome e endereço sem apagar itens da lista original. A pesquisa global consulta até seis servidores simultaneamente; os tempos reais dependem da rede e dos serviços.

Ainda exigem conferência manual: login/coleta real do Gestão, pesquisa nas conexões reais, instalação administrativa/reinstalação nas instalações principais e inspeção visual completa após reiniciar. O histórico automático de descrição de publicação possui uma limitação anterior conhecida em alguns servidores e não é declarado corrigido nesta entrega. A versão 2.8.0 permanece preservada como pré-lançamento; a 2.8.1 entrega as correções aprovadas do instalador e da atualização da 6.16.0.

## Interface auxiliar e automação

Opções implementadas no EXE:

- --open: abre a interface no fluxo de abertura.
- --detect --report arquivo.json: detecta instalações e registra caminhos/versões.
- --inspect "pasta do Studio" --report arquivo.json: verifica o plano completo, sem instalar.
- --verify "pasta do Studio" --report arquivo.json: confere os arquivos instalados sem abrir o Studio.
- --install "pasta do Studio" --report arquivo.json: instalação completa por linha de comando, com a elevação normal do Windows.
- --self-test "pasta nova": testa extração, integridade e construção da interface; não comprova instalação nem fluxos remotos.

O PowerShell auxiliar do projeto usa somente essas opções. Acao Instalar/Abrir abre a interface gráfica; Diagnosticar produz relatórios, JVMs configuradas e situação dos arquivos. O script não substitui a interface principal.
