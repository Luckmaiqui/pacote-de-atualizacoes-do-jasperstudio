# Instalador unificado 2.8.0 — pré-lançamento

A distribuição consiste em um ZIP contendo somente JasperStudio.exe. Os módulos continuam separados internamente, mas tema, tradução, ordenação, pesquisa, contas e Gestão são instalados juntos. Não é necessário executar BATs. O próprio Studio não é instalado nem seus JARs originais são substituídos.

## Uso

1. Extraia o ZIP em uma pasta gravável e abra JasperStudio.exe.
2. Marque uma ou mais instalações detectadas. A detecção consulta diretórios usuais e o registro do Windows; Adicionar Pasta permite incluir instalações personalizadas.
3. Clique em Verificar Selecionados. São conferidos versão real dos bundles, JVM configurada, arquitetura, dependências e SHA-256 dos componentes. A verificação não instala nada.
4. Salve e feche os Studios marcados. Clique em Instalar Pacote Completo. O Windows solicita administrador para alterar Program Files.
5. Cada instalação possui transação, bloqueio, backup e recuperação próprios. Falhas em uma instalação são informadas; as demais são processadas separadamente. Isso não é uma transação atômica entre máquinas/instalações diferentes.
6. Para abrir, marque uma instalação, confira o pacote e clique em Abrir Studio. A abertura confere os JARs instalados antes de iniciar.
7. Abrir Logs mostra as transações e diagnósticos. Relatórios, conexões e credenciais não são incluídos na distribuição.

## Portes exatos

O pacote contempla 6.6.0, 6.16.0, 6.20.6, 6.21.0 e 6.21.5. Cada porte usa seus próprios JARs e fragmentos de tradução. Não se escolhe a referência numericamente mais próxima. Uma versão sem seu porte exato é recusada.

6.20.6 é a referência Java 17.0.8.1. Os demais portes são disponibilizados com identificação de suporte parcial/para testes, sem transformar testes de componentes em certificação do produto completo. 6.6.0 utiliza Java 8, 6.16.0 Java 11 e 6.21.0/6.21.5 Java 17. Consulte a matriz de evidências antes de considerar qualquer combinação plenamente validada.

Na 6.16.0, o instalador incorpora Java 11.0.33+10 privado para o suporte a HmacPBESHA256; ele altera apenas -vm dessa instalação, com backup do INI, sem alterar o Java do Windows, certificados ou configurações TLS. Em falha posterior, o INI anterior é restaurado; os arquivos do runtime privado podem permanecer sem uso para diagnóstico.

## Gestão e limitações

Nos Studios sem backend Edge no SWT, um único leitor WebView2 externo é reutilizado, com título comum, ícone do próprio Studio e cores da janela de origem. É necessário Microsoft Edge WebView2 Runtime instalado. Seu perfil de navegação é próprio; pode pedir login. O coletor não extrai cookies, tokens ou senhas. Coletor Para o Navegador e Importar JSON continuam disponíveis.

O leitor não depende da rota /management para habilitar o botão. O domínio HTTPS é validado e os cartões da lista de clientes devem estar presentes. O filtro da revisão pesquisa código, nome e endereço sem apagar itens da lista original. A pesquisa global consulta até seis servidores simultaneamente; os tempos reais dependem da rede e dos serviços.

Ainda exigem conferência manual: login/coleta real do Gestão, pesquisa nas conexões reais, instalação administrativa/reinstalação nas instalações principais e inspeção visual completa após reiniciar. O histórico automático de descrição de publicação possui uma limitação anterior conhecida em alguns servidores e não é declarado corrigido nesta entrega. Por isso a 2.8.0 é pré-lançamento.

## Interface auxiliar e automação

Opções implementadas no EXE:

- --open: abre a interface no fluxo de abertura.
- --detect --report arquivo.json: detecta instalações e registra caminhos/versões.
- --inspect "pasta do Studio" --report arquivo.json: verifica o plano completo, sem instalar.
- --verify "pasta do Studio" --report arquivo.json: confere os arquivos instalados sem abrir o Studio.
- --install "pasta do Studio" --report arquivo.json: instalação completa por linha de comando, com a elevação normal do Windows.
- --self-test "pasta nova": testa extração, integridade e construção da interface; não comprova instalação nem fluxos remotos.

O PowerShell auxiliar do projeto usa somente essas opções. Acao Instalar/Abrir abre a interface gráfica; Diagnosticar produz relatórios, JVMs configuradas e situação dos arquivos. O script não substitui a interface principal.