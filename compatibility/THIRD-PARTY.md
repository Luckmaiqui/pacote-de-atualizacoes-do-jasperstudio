# Dependências utilizadas pela ferramenta de compatibilidade

O pacote desta ferramenta contém código próprio, metadados e evidências de testes. Não contém distribuições de Java, Maven, JasperReports ou suas bibliotecas transitivas. A preparação explícita baixa os arquivos oficiais para um cache local ignorado pelo Git e confere seus checksums.

- [JasperReports Library](https://github.com/Jaspersoft/jasperreports): LGPL; licenças e coordenadas em cada POM publicado. Consulte `catalog.json` para a versão utilizada.
- [Eclipse Temurin](https://adoptium.net/): OpenJDK, GPLv2 com Classpath Exception e avisos das distribuições. Os arquivos de licença permanecem nos runtimes extraídos.
- [Apache Maven](https://maven.apache.org/): Apache License 2.0; distribuição 3.9.11 com checksum SHA512.
- [Eclipse JDT/ECJ](https://eclipse.dev/jdt/): licenciamento Eclipse correspondente à versão. Bootstrap usa o compilador do Studio existente ou ECJ 3.33.0 do Maven Central; perfis experimentais adicionais são explícitos.
- As dependências transitivas conservam suas licenças próprias e ficam registradas no lock local. Os POMs completos no repositório oficial são a fonte de versões e escopos.

Um Java Oracle já instalado pode ser reutilizado sem ser copiado ou redistribuído pelo pacote. Os complementos originais e seus avisos de terceiros permanecem independentes desta ferramenta.
