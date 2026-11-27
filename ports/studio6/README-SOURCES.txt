FONTES DOS PORTES — 24/11/2026

Os auxiliares de APIs Java incorporados aos módulos são derivados do
JvmDowngrader 2.0.1, sob LGPL-2.1-or-later. Os namespaces foram relocados
para com.salutem.portcompat.<módulo>. O arquivo upstream contém os fontes
correspondentes, scripts Gradle e wrapper do fornecedor; as licenças
acompanham estes fontes. Não se atribui uma nova licença ao projeto Salutem.

O JNA 5.14.0 incorporado mantém seu arquivo original e é redistribuído pela
opção Apache-2.0. Incluímos LICENSE, AL2.0 e o JAR de fontes Java do fornecedor.
As licenças também permanecem dentro do JAR JNA original. Não há alteração
dos binários nativos do JNA.

adapted-modules: cópia exata das fontes e recursos usados neste build.
objects: JARs dos módulos antes da adaptação e relocação de APIs, para
possibilitar nova ligação com uma versão modificada das bibliotecas.
translation-fragments: recursos gerados por hospedeiro, sem dados do usuário.
build-tools: ferramentas e instruções para reproduzir a ligação.
upstream: arquivos originais baixados das URLs fixadas em redistribution.json.

Para modificar os auxiliares LGPL, extraia o arquivo JvmDowngrader de fontes,
consulte o README e os projetos java-api do fornecedor e utilize seu Gradle
wrapper para produzir o API JAR e a ferramenta 2.0.1. O toolchain.json informa
os artefatos originais e seus hashes. Dependências de build podem exigir rede.

Com PowerShell 7.2+ e Java de build compatível com a ferramenta, execute
Relink-Module.ps1 informando Module, RawJar (de objects), ApiJar (incluindo uma
versão modificada), ToolJar, JavaExecutable, Classpath dos plugins da própria
distribuição Studio e OutputDirectory novo. O script chama Backport.ps1 com
o mesmo namespace de relocação usado no porte. Não inclui nem substitui as
bibliotecas proprietárias/distribuição do Studio; estas são fornecidas pelo
próprio usuário. Testes de compatibilidade precisam ser repetidos após mudanças.

Os hashes são registros de integridade, não assinatura do fornecedor.
Este material não contém relatórios, conexões ou credenciais do usuário.
