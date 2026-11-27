# Motores JasperReports isolados — ferramenta em desenvolvimento

Esta ferramenta amplia o processamento de relatórios sem trocar os JARs do Studio. **Não instala os complementos de temas/tradução/ordenação em outras versões do Studio.** Consulte `../docs/ARQUITETURA-COMPATIBILIDADE.md` e a matriz gerada `../docs/COMPATIBILIDADE-JASPER-JAVA.md` para escopo e evidências.

Requisitos da ferramenta: Windows x64, PowerShell 7.2+ e Node.js 20+. As JVMs dos motores são independentes do Node/PowerShell e do Studio. A primeira preparação exige Internet e espaço para runtimes/dependências; depois os locks são reutilizados e conferidos. Nenhum login do Gestão/JasperServer é utilizado.

## Preparar

Execute na raiz do projeto/pacote:

```powershell
pwsh -File compatibility/Prepare-Tools.ps1
pwsh -File compatibility/Build-Worker.ps1
```

Isso prepara Java 8, 11, 17, 21, 25 e 27. Para preparar outro subconjunto, use `-JavaVersions 8,17,21`; versões indisponíveis no fornecedor geram erro, não um runtime improvisado. Os executáveis e caminhos locais ficam em `.compatibility/tools.json`, que não é distribuído. Não são necessárias mudanças de administrador em Program Files.

O preparador continua com as demais JVMs se uma não estiver disponível. Se não houver JRE publicado para a versão, aceita o JDK oficial como runtime. Runtimes preparados anteriormente são preservados. Para um Java já instalado, inclusive uma versão histórica que a API atual não distribui, registre explicitamente:

```powershell
node compatibility/cli.mjs register-java "C:\Java\jdk-15\bin\java.exe"
```

O comando confere a versão real, recusa EA e versões inferiores a 8 e substitui apenas o cadastro local daquele major. Não muda a JVM do Studio; o cadastro só fica validado depois de executar a matriz.

## Usar

```powershell
node compatibility/cli.mjs inventory
node compatibility/cli.mjs inspect "C:\Relatorios\Exemplo.jrxml"
node compatibility/cli.mjs validate 6.6.0 "C:\Relatorios\Exemplo.jrxml" "C:\Resultados\validacao" 8
node compatibility/cli.mjs compile 6.20.6 "C:\Relatorios\Exemplo.jrxml" "C:\Resultados\compilacao" 17
```

Informe um diretório de saída que ainda não exista. `validate` não grava o relatório. `compile` produz `report.jasper` na saída nova e preserva o original. O parâmetro Java é opcional: sem ele, os runtimes preparados são testados para aquela operação, começando pelo mais recente. A família 6/7 deve corresponder ao arquivo.

`compile auto` é uma opção explícita para escolher o motor mais recente que consiga compilar o arquivo daquela família. **Ela não identifica a versão original e não escolhe a versão do servidor de publicação**. Para entregar a um servidor/sistema existente, informe a versão de JasperReports desse destino, em vez de usar `auto`.

O inventário separa artefato publicado, dependências locais e resultados da matriz. A presença dos arquivos do lock não significa que foram novamente conferidos no inventário; a execução revalida os hashes.

## Repetir a análise e os testes

```powershell
pwsh -File compatibility/Collect-Versions.ps1 -AsOf 2026-11-24
pwsh -File compatibility/Analyze-Schemas.ps1
node compatibility/Test-Matrix.mjs
node --test tests/compatibility.test.mjs
node compatibility/Generate-Report.mjs
pwsh -File compatibility/Inspect-Studio.ps1 -StudioPath "C:\Program Files\Jaspersoft\Jaspersoft Studio-6.20.6"
```

O catálogo atualizado fica em `catalog.json`. As evidências de componentes e de operações são distintas: teste do worker não autoriza instalar um JAR de complemento em outro Studio. As matrizes usam dados sintéticos e não acessam conexões de clientes. Dependências antigas são mantidas apenas para o motor solicitado, sem serem misturadas à instalação principal.
