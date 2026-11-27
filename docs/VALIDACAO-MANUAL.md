# Validação visual e funcional no Studio — separada do CI

Os testes de dados, hashes e componentes sintéticos não demonstram aparência de todas as telas. Executar em instalação/workspace de teste, com cópia dos relatórios. Não usar credenciais ou servidores de clientes no CI.

## Candidato Windows

- Iniciar o EXE em Windows x64, DPI 100%, 150% e 200%; conferir textos e navegação por teclado.
- Detectar instalação padrão e selecionar uma portátil com espaços/acentos. Conferir bloqueio de Studio 6.6, Java 8/11/21, JVM não identificada, SWT divergente e módulos obrigatórios ausentes.
- Cancelar escolha da pasta, verificação e confirmação. Rejeitar UAC; conferir mensagem e ausência de cópia.
- Após autorização explícita, instalar cada módulo sozinho e as combinações em uma instalação descartável do Studio 6.20.6/Java 17. Conferir hashes e backups. Testar pacote corrompido, arquivos bloqueados e conflito de mesmo nome.
- Abrir pelos dois BATs e pelo EXE; conferir preservação das preferências, `-clean` apenas quando necessário, pt_BR apenas se selecionado e tema sem forçar tradução.

## Temas

- Percorrer as sete paletas, seletores Combo/List, menus e itens desabilitados, galeria, Propriedades, paleta, publicação e editor de consulta.
- Confirmar expansão das listas, contraste de seleção, números das linhas e ausência de texto duplicado.
- Personalizar/restaurar cor de um bloco e fundo do designer; comparar fonte, JRXML e preview antes/depois. Papel e cores do relatório devem ser preservados.

## Tradução

- Alternar inglês/pt_BR e reiniciar; confirmar nomes técnicos, ID, Java Bean, Linguagem, Escrever e Escrever Apenas Expressão.
- Confirmar que valores editáveis, expressões, consultas, nomes de recursos e atalhos não foram traduzidos indevidamente.

## Conexões

### Histórico da Descrição da Unidade de Relatório

Usar uma pasta de testes e usuários autorizados. A candidata não foi instalada automaticamente e não publicou relatórios reais.

1. Publicar relatório novo com descrição vazia; conferir formato, usuário da conexão e horário no fuso configurado. Fechar e reabrir o assistente e conferir também no servidor.
2. Atualizar uma unidade com descrição livre e registros anteriores, incluindo acentos e espaços; conferir preservação literal e apenas uma linha nova. Repetir sem mudar o relatório, alterando somente SELECT e somente design.
3. Repetir com o mesmo usuário no mesmo minuto: uma linha exatamente igual não deve duplicar. Usar outro usuário/minuto: deve acrescentar outra linha.
4. Abrir/navegar/cancelar, ou provocar falha da publicação antes de concluir: não deve surgir registro de sucesso.
5. Em ambiente controlado, testar rejeição do PATCH após publicar: deve aparecer histórico pendente, sem falsa confirmação nem truncamento solicitado. Restaurar o acesso e repetir no mesmo assistente; o ticket não deve duplicar.
6. Publicar concorrentemente com dois clientes e conferir o histórico. O PATCH usa versões/releitura, mas a publicação completa nativa não constitui uma transação distribuída; registrar qualquer sobrescrita externa.
7. Na 6.6.0, validar primeiro abertura/encerramento do assistente. O teste automatizado desta integração atingiu timeout na inicialização; não está aprovado.

Detalhes, evidências, limites do tamanho da descrição e arquivos: seção de histórico em `PORTES-STUDIO-6.md`.

- Conferir ordenação no preview, dataset, exploradores e assistente de publicação; seleção, identidade e destino devem ser preservados.
- Testar filtros independentes, MyReports expandido, busca global, escopo selecionado/múltiplos servidores, cancelamento e localizar/abrir na árvore.
- Conferir contas, importação sem duplicação e abertura de sub-relatórios locais/remotos, somente em ambiente autorizado.

## Limites

Abertura das prévias privadas, editor, paleta e compilação/preenchimento de relatório sintético foram conferidos nas versões 6.6.0 e 6.21.5. A revisão encontrou pendências reais nos menus nativos, mantidas como bloqueadoras. Este checklist completo ainda não representa aprovação executada. Instalação real com UAC, recursos remotos, DPI e todas as telas continuam pendentes. Os testes de motores isolados têm matriz e escopo separados em COMPATIBILIDADE-JASPER-JAVA.md.
