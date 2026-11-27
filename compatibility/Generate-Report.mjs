import fs from 'node:fs/promises';
import path from 'node:path';
import {root,json,catalog} from './runtime.mjs';
const c=await catalog(),matrix=await json(path.join(root,'compatibility/results/core-matrix.json')),schemas=await json(path.join(root,'compatibility/schema-deltas.json'));
const cell=(version,java)=>{if(java<8)return 'B';const result=matrix.results.find(r=>r.version===version&&r.javaMajor===java);return result?.status==='validated-core-native'?'V':result?.status==='validated-core-adapted'?'A':result?'F':'NT';};
const lines=['# Compatibilidade JasperReports × Java','',`Levantamento em ${c.asOf}; ${c.versions.length} versões analisadas, de 6.6.0 a ${c.latest}.`,'',
'Este documento trata do worker isolado de relatórios, separado dos complementos de interface do Studio. **V não significa suporte integral ao Studio, a todos os recursos ou certificação oficial de uma JVM.**','',
'Legenda: **V** = escopo básico validado com dependências declaradas do perfil core; **A** = validado com compilador adaptado explícito; **F** = teste falhou; **NT** = não testado nesta JVM; **B** = bytecode do núcleo impede esta JVM.','',
'Escopo executado: leitura, validação do modelo, compilação de expressão Java com parâmetro e campo, gravação e releitura de .jasper no mesmo motor, edição e releitura de uma cópia JRXML, preenchimento de duas linhas com acentos, exportação XML e preservação SHA256 do original. Perfil core exclui PDF, gráficos e extensões opcionais não solicitadas.','',
'Java 6 e 7: B em todas as versões analisadas (núcleo com classfile 52/Java 8). Os POMs e o bytecode são evidências de construção; não foi localizada uma matriz oficial que certifique todas as combinações de JVM. Campo officialJavaCertifications permanece vazio, deliberadamente.',''];
const runtimes=[...new Map(matrix.results.map(r=>[r.javaMajor,r.javaVersion])).entries()].sort((a,b)=>a[0]-b[0]);
lines.push(`Resultado: ${matrix.results.length} combinações executadas; ${matrix.results.filter(r=>r.status.startsWith('validated')).length} aprovadas no escopo básico; ${matrix.results.filter(r=>r.status==='failed').length} falhas.`,
 `Runtimes efetivamente usados: ${runtimes.map(([major,version])=>'Java '+major+' ('+version+')').join('; ')}.`,
 'Java 9, 10 e 12–15 permanecem não testados nesta execução. Outros runtimes locais estáveis podem ser cadastrados por register-java, sem alterar a instalação do Studio.','');
for(const majors of [[8,9,10,11,12,13,14,15,16,17],[18,19,20,21,22,23,24,25,26,27]]){
 lines.push('| Motor | '+majors.map(j=>'Java '+j).join(' | ')+' |','|---|'+majors.map(()=>'---').join('|')+'|');
 for(const v of c.versions)lines.push('| '+v.version+' | '+majors.map(j=>cell(v.version,j)).join(' | ')+' |');lines.push('');
}
lines.push('## Fronteiras de recursos','');
try{const features=await json(path.join(root,'compatibility/results/features.json'));for(const f of features.records)lines.push(`- ${f.feature}, motor ${f.version}/Java ${f.javaMajor}: esperado ${f.expectedSuccess?'aceitar e processar':'recusar'}; ${f.passed?'resultado confirmado':'resultado divergente'}.`);}catch{lines.push('Testes específicos de recursos ainda não executados.');}
lines.push('','## Evidências por versão','', 'Datas de publicação do GitHub e datas internas do changelog são registradas separadamente; diferenças entre elas não são corrigidas por suposição. O catálogo JSON contém todas as dependências diretas, versões, escopos e marcações opcionais. Os locks locais guardam o fechamento efetivamente resolvido e SHA256 de cada JAR.','');
for(const v of c.versions){
 const s=schemas.versions.find(r=>r.version===v.version),r=matrix.results.filter(r=>r.version===v.version),ok=r.filter(r=>r.status.startsWith('validated')).length;
 lines.push(`### ${v.version}`,`- Publicação: ${v.publishedAt??'não confirmada'}; changelog: ${v.changeLogDate??'não informado'}. [Notas oficiais](${v.releaseUrl??c.changesUrl}); [POM](${v.pomUrl}).`,
 `- Núcleo: Java ${v.coreJavaMinimum}, classfile ${v.coreClassMajor}; alvo do build declarado: ${v.compilerTarget??'não identificado no POM publicado'}. Formato: ${v.format}. Resultados básicos: ${ok}/${r.length} JVMs executadas.`,
 `- Áreas citadas nas notas: ${v.changeTopics?.join(', ')||'correções/manutenção; consultar notas oficiais'}.`,
 `- Estrutura: ${s.schema}; ${s.addedSchemaNames.length} nomes novos e ${s.removedSchemaNames.length} ausentes versus o predecessor analisado. Padrões: ${s.addedDefaultProperties.length} propriedades novas e ${s.removedDefaultProperties.length} ausentes. A ausência de um nome não comprova remoção semântica.`,
 `- Nomes novos do XSD: ${s.addedSchemaNames.slice(0,12).join(', ')||'nenhum'}. Detalhes completos: schema-deltas.json.`,
 `- Propriedades novas: ${s.addedDefaultProperties.slice(0,12).join(', ')||'nenhuma'}.`,
 `- Dependências diretas publicadas: ${v.dependencies.filter(d=>!['test','provided'].includes(d.scope)).map(d=>`${d.group}:${d.artifact}:${d.version}${d.optional?' (opcional)':''}`).join('; ')}.`);
 if(v.publishedExtensions?.length)lines.push(`- Extensões publicadas, não testadas por esta bateria: ${v.publishedExtensions.map(e=>e.artifact).join(', ')}. POMs individuais no catálogo.`);
 for(const bad of r.filter(r=>!r.status.startsWith('validated')))lines.push(`- Limitação Java ${bad.javaMajor}: ${bad.native.errorType??'falha'}; ${bad.native.error??''}.`);
 lines.push('');
}
lines.push('## Limites e manutenção','',
'- JRXML/JRTX 6 e 7 são famílias incompatíveis. Não há conversão automática nesta implementação. Para conversão intencional, use o recurso oficial do Studio 7 e grave uma cópia. Arquivos .jasper devem ser recompilados para o motor de destino; os resultados desta matriz não provam intercâmbio de binários entre versões.',
'- PDF, XLSX, gráficos, códigos de barras, componentes personalizados, Groovy/JavaScript e funções externas exigem seus módulos próprios e testes adicionais. A resolução full existe como diagnóstico e não substitui o perfil core sem evidência.',
'- A interface de ordenação/temas/tradução existente permanece validada no Studio Community 6.20.6/Java 17. Studio 6.6.0/Java 8 foi identificado localmente, mas os complementos atuais têm bytecode 17 e dependências de APIs internas/fragmentos exatos; não foram instalados nele.',
'- JVMs marcadas NT não foram declaradas compatíveis nem incompatíveis. É possível registrar outros ambientes e repetir a bateria. Nenhum resultado aqui altera configurações do Studio ou dos servidores.',
'- A descoberta de versões e os downloads só ocorrem ao executar comandos explícitos. Novas versões entram pelo catálogo, resolução isolada, testes, revisão e publicação; nunca por troca silenciosa de bibliotecas do Studio.','');
await fs.writeFile(path.join(root,'docs/COMPATIBILIDADE-JASPER-JAVA.md'),lines.join('\n'));
console.log(`Matriz gerada: ${c.versions.length} motores, ${matrix.results.length} testes registrados.`);
