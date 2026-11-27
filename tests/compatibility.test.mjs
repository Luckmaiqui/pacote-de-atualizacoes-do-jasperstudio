import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {root,cache,catalog,tools,runWorker,execute,hash,json} from '../compatibility/runtime.mjs';

test('catálogo estável cobre início, regressão, fronteira de formato e versão atual sem duplicidades',async()=>{
 const c=await catalog();assert.equal(c.versions[0].version,'6.6.0');assert.ok(c.versions.some(v=>v.version==='6.20.6'));assert.ok(c.versions.some(v=>v.version==='7.0.0'));assert.equal(c.versions.at(-1).version,c.latest);assert.equal(new Set(c.versions.map(v=>v.version)).size,c.versions.length);
 for(const v of c.versions){assert.match(v.version,/^\d+\.\d+\.\d+$/);assert.equal(v.coreJavaMinimum,8);assert.equal(v.coreClassMajor,52);assert.ok(v.pomUrl.startsWith('https://repo.maven.apache.org/'));assert.deepEqual(v.officialJavaCertifications,[]);assert.match(v.artifactSha256,/^[a-f0-9]{64}$/);}
});
test('famílias XML e evidências de alterações estruturais permanecem separadas',async()=>{
 const c=await catalog(),s=await json(path.join(root,'compatibility/schema-deltas.json'));
 assert.equal(s.versions.length,c.versions.length);assert.equal(c.versions.find(v=>v.version==='6.20.6').format,'digester-6');assert.equal(c.versions.find(v=>v.version==='7.1.1').format,'jackson-7');
 assert.ok(s.versions.find(v=>v.version==='6.20.6').schemaNames.includes('element:styleExpression'));assert.equal(s.versions.find(v=>v.version==='7.0.0').schema,'jackson-model-no-legacy-xsd');
});
test('evidências registram o escopo comprovado, sem duplicar motor/JVM nem aceitar fonte desatualizada',async()=>{
 const proof=await json(path.join(root,'compatibility/results/core-matrix.json'));
 assert.equal(proof.workerHash,hash(await fs.readFile(path.join(root,'compatibility/java/ReportWorker.java'))));assert.equal(proof.runtimeHash,hash(await fs.readFile(path.join(root,'compatibility/runtime.mjs'))));
 const keys=new Set();for(const r of proof.results){const key=r.version+':'+r.javaMajor;assert.ok(!keys.has(key));keys.add(key);if(r.status.startsWith('validated')){const result=r.native.ok?r.native:r.adapted;for(const operation of ['read','validate','compile','compiledRoundTrip','editRoundTrip','fill','exportXml','originalPreserved'])assert.equal(result[operation],true,key+' '+operation);}}
});
let config;try{config=await tools();}catch{}
test('inspeção recusa XXE, não inventa versão exata e preserva bytes',{skip:!config},async()=>{
 const java=config.java[0],temp=await fs.mkdtemp(path.join(cache,'negative-')),input=path.join(root,'compatibility/fixtures/legacy.xml'),before=hash(await fs.readFile(input));
 const valid=await runWorker({java,version:'inspect',input,output:temp,operation:'validate'});assert.equal(valid.ok,true);assert.equal(valid.inspection.exactVersion,null);assert.equal(valid.inspection.format,'digester-6');assert.equal(hash(await fs.readFile(input)),before);
 const xxe=path.join(temp,'xxe.xml');await fs.writeFile(xxe,'<!DOCTYPE jasperReport [<!ENTITY secret SYSTEM "file:///invalid-private-file">]><jasperReport name="x">&secret;</jasperReport>');
 const bad=await runWorker({java,version:'inspect',input:xxe,output:temp,operation:'validate'});assert.equal(bad.ok,false);assert.match(bad.error,/DOCTYPE|doctype/i);
});
test('motores recusam a outra família sem converter o original',{skip:!config},async()=>{
 for(const [version,fixture] of [['6.20.6','modern.xml'],['7.1.1','legacy.xml']]){const input=path.join(root,'compatibility/fixtures',fixture),before=hash(await fs.readFile(input)),output=await fs.mkdtemp(path.join(cache,'mismatch-'));const r=await runWorker({java:config.java[0],version,input,output,operation:'validate'});assert.equal(r.ok,false);assert.match(r.error,/Formato incompatível/);assert.equal(hash(await fs.readFile(input)),before);}
});
test('CLI recusa saída existente antes de compilar',{skip:!config},async()=>{
 const output=await fs.mkdtemp(path.join(cache,'preserve-'));await fs.writeFile(path.join(output,'sentinel'),'preservar');
 const r=await execute(process.execPath,['compatibility/cli.mjs','compile','6.6.0','compatibility/fixtures/legacy.xml',output,'8']);assert.equal(r.code,1);assert.match(r.stderr,/já existe/);assert.equal(await fs.readFile(path.join(output,'sentinel'),'utf8'),'preservar');
});
