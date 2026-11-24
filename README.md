# Pacote de Atualizações do JasperStudio

Complementos independentes para **Jaspersoft Studio Community 6.20.6 no Windows, Java 17+**.

> Versão 2.8.0: cor personalizada por bloco da interface, no pacote Temas. Consulte as [notas da versão](docs/releases/v2.8.0.md).
>
> Versão 2.7.0: contas, importação do Gestão e melhorias no gerenciamento de conexões. Consulte as [notas da versão](docs/releases/v2.7.0.md), o [histórico de revisão](docs/REVISAO-2.7.0.md) e a [Importação do Gestão](docs/IMPORTACAO-GESTAO.md). A validação de alguns fluxos reais continua pendente, conforme as notas.

## Escolha o que instalar

Baixe em [Releases](https://github.com/Luckmaiqui/pacote-de-atualizacoes-do-jasperstudio/releases):

| ZIP | Pasta | Conteúdo |
|---|---|---|
| JS-Temas-v2.8.0.zip | Temas | Noite, Grafite, Floresta, Ameixa, GitHub, Ônix e Meia-Noite, mais cor personalizada por bloco |
| JS-Traducao-ptBR-v2.8.0.zip | Traducao-ptBR | Tradução para Português (Brasil) e bandeira |
| JS-Ordenacao-v2.8.0.zip | Ordenacao-de-conexoes | Pesquisa e ordenação de conexões, contas e importação do Gestão |
| JS-Pacotes-Separados-v2.8.0.zip | Pacote de Atualizações do JasperStudio | As três pastas acima, com instaladores separados |

Cada pasta tem **Instalar.bat** e **Abrir JS.bat** próprios. Você pode instalar apenas uma, duas ou todas. O pacote de temas não instala tradução nem muda o idioma. O pacote de tradução não instala nem seleciona tema. A ordenação também é independente.

1. Extraia o ZIP, salve seus relatórios e feche o Studio.
2. Execute o Instalar.bat da pasta desejada e aceite a elevação do Windows.
3. Informe a instalação do Studio se solicitado.
4. Abra pelo Abrir JS.bat da pasta escolhida.
5. Para trocar cores: **Janela > Preferências > Gerais > Aparência > Tema**.
6. Para traduzir: **Janela > Language > Português (Brasil)** e reinicie; o inicializador da tradução também fornece `-nl pt_BR`.

O inicializador de temas respeita o idioma salvo; o padrão de tema para novos workspaces é Salutem Noite. Instalar um componente não remove componentes já instalados. A tradução complementar atua quando o idioma ativo é português.

## Recursos e compatibilidade

Os sete temas compartilham os ajustes de propriedades, paleta, menus, galeria, editor, preview e assistentes. O papel do relatório e seu conteúdo não são modificados. As cores de texto principais, campos e seleção são verificadas com contraste mínimo 4,5:1.

A tradução reúne 49 recursos / 6.496 entradas em 24 fragmentos, pacotes Eclipse Babel e rótulos complementares. A ordenação agrupa códigos como `(001)`, `(002)`, `(014)`, `(103)`.

Os complementos são copiados para `dropins/eclipse/plugins`, com validação SHA256 e bloqueio de arquivos existentes divergentes. Os JARs originais do produto não são substituídos.

**Não compatível diretamente com 6.16**: usa Java 17 e fragmentos vinculados às versões exatas dos módulos. Outras versões, Linux, macOS e Professional não foram validados. Traduções em lote podem exigir revisão editorial.

## Desenvolvimento

Requisitos: PowerShell 7, Node.js 20+, Java 17 e Studio 6.20.6 local para recompilar.

```powershell
pwsh ./scripts/Build.ps1 -StudioPath 'C:\Program Files\Jaspersoft\Jaspersoft Studio-6.20.6'
# Apenas empacotar artefatos já verificados:
pwsh ./scripts/Build.ps1 -PackageOnly
```

As paletas ficam em `modules/theme/resources/palettes.properties`. `scripts/GenerateThemes.mjs` gera CSS e registros de tema. Ao alterar código de um módulo, aumente sua versão em `versions.json`; os outros componentes mantêm seus binários. O build preserva JARs sem mudança de conteúdo e rejeita mudança sem aumento de versão.

A saída contém quatro ZIPs e SHA256SUMS.txt em `dist/`. O workflow Release publica todos esses arquivos, a partir de uma tag ou execução manual com a versão de `versions.json`.

Veja [instalação](docs/INSTALACAO.md), [desenvolvimento](docs/DESENVOLVIMENTO.md) e [avisos de terceiros](THIRD_PARTY_NOTICES.md).

Personalizações preparadas para Luckas / Salutem. Não é produto oficial Jaspersoft/Eclipse. A licença dos arquivos próprios ainda deve ser definida pelo titular; o repositório não declara licença aberta para eles.
## Pesquisa de conexões

Instale o pacote Ordenacao-de-conexoes atualizado. Ao abrir a lista de adaptadores no preview ou no dataset, digite o código ou parte do nome. A pesquisa ignora maiúsculas e acentos e aceita vários termos. Use as setas e Enter, clique em uma opção ou pressione Esc para cancelar. Não altera credenciais, consultas nem executa pesquisa no servidor; filtra somente os rótulos das conexões disponíveis.

A ordenação numérica também se aplica às conexões de banco de dados e JasperReports Server no Explorador de repositórios. Pastas, relatórios, seleção e expansão da árvore são preservados.

## Pesquisa nos exploradores

Repositórios: filtro local de bancos e conexões JasperServer por código/nome. Projeto: filtro independente de nomes e caminhos, com campo na barra nativa da aba (a árvore CommonNavigator permanece intacta).

Ctrl+F no repositório ou **Buscar em todos os servidores…** abre uma única janela. Digite pelo menos 3 caracteres e clique em Pesquisar. O escopo inicial acompanha o JasperServer selecionado, inclusive uma pasta/arquivo descendente. Sem contexto de servidor, inicia com Todos os Servidores. Use **Escolher Servidores…** para marcar múltiplas conexões e pesquisar somente nelas. A consulta ocorre com no máximo 2 consultas simultâneas. A lista reúne nome, tipo, caminho e servidor de origem; a aba Conexões informa falhas individuais. Cancelar mantém os resultados recebidos. Selecione um arquivo e use **Localizar na árvore**.

A busca usa nomes e descrições dos recursos pela API nativa autenticada do Studio. Não baixa o conteúdo de todos os relatórios nem pesquisa o texto dentro dos JRXMLs. Não executa consultas remotas enquanto você digita ou ao abrir o aplicativo. A duração depende da quantidade e disponibilidade dos servidores.

Os temas novos têm fundos próximos ao preto. O GitHub se inspira nas cores do [Primer](https://primer.style/product/getting-started/react/theme-reference/); Ônix usa preto neutro e Meia-Noite usa preto azulado. Menus de contexto usam o desenho nativo escuro do Windows para evitar texto em relevo nos comandos desabilitados.

## Desempenho

A versão 2.6.0 reduz varreduras repetidas da interface, agrupa atualizações e reutiliza traduções e cores. O Abrir JS.bat usa -clean apenas na primeira abertura ou após atualização dos complementos. Instalação e atualizações continuam com hashes verificados. Veja as medições e seus limites em [Desempenho](docs/DESEMPENHO.md).

Correção 2.6.1: restaura o desenho escuro dos menus e o destaque legível nas listas de seleção; os rótulos de publicação usam Escrever e Escrever Apenas Expressão. A ordem e os valores das opções permanecem os mesmos.

## Cor personalizada por bloco

Disponível no pacote Temas a partir da 2.8.0 e funciona com qualquer tema, inclusive o escuro padrão do Eclipse. Use **Janela > Escolher cor de um bloco...**: o bloco sob o mouse (área do editor, visões e pilhas de abas) recebe um contorno vermelho; clique para abrir o seletor de cores. Esc ou o botão direito cancelam. Clicar na área do editor escolhe a área do editor inteira; com um relatório aberto, clicar ao lado da página escolhe o **Fundo do designer** (só a região em volta da página).

O seletor tem quadro de saturação e brilho, barra de matiz, código hexadecimal (`#RRGGBB`, `RRGGBB` ou `#RGB`), campos R, G e B, prévia Atual/Nova, cores básicas, as 10 últimas cores usadas e conta-gotas para pegar a cor de qualquer ponto da tela, com lupa. Enter aplica e Esc cancela.

Os blocos coloridos ficam em **Janela > Preferências > Gerais > Aparência > Cores personalizadas**, onde é possível alterar, remover ou restaurar tudo; ao remover, o bloco volta às cores do tema. A cor cobre o fundo do bloco inteiro, inclusive árvores, tabelas, paleta e seções de Propriedades, com o texto ajustado para leitura; campos editáveis, botões e o conteúdo do relatório (página, elementos, prévia e saída) mantêm as cores do tema.
