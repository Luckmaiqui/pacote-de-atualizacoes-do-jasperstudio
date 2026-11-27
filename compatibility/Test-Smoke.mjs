import fs from 'node:fs/promises';
import path from 'node:path';
import {root,cache,tools,runWorker,hash} from './runtime.mjs';

// Separate evidence: do not overwrite the full historical matrix for a subset run.
const config=await tools(),folder=await fs.mkdtemp(path.join(cache,'smoke-'));
const results=[];
for(const [version,major] of [['6.6.0',8],['6.20.6',17],['6.21.5',21],['7.0.0',17],['7.1.1',27]]){
 const java=config.java.find(j=>j.major===major);
 if(!java){results.push({version,javaMajor:major,status:'not-tested',reason:'Runtime não preparado'});continue;}
 const input=path.join(root,'compatibility/fixtures',version.startsWith('6.')?'legacy.xml':'modern.xml');
 let result;try{result=await runWorker({java,version,input,output:path.join(folder,version),operation:'smoke'});}catch(error){result={ok:false,error:error.message};}
 results.push({version,javaMajor:major,javaVersion:java.version,status:result.ok?'validated-core-native':'failed',result});
 console.log(version+' / Java '+major+': '+(result.ok?'PASS':'FAIL'));
}
await fs.writeFile(path.join(root,'compatibility/results/smoke.json'),JSON.stringify({schema:1,testedAt:new Date().toISOString(),workerHash:hash(await fs.readFile(path.join(root,'compatibility/java/ReportWorker.java'))),runtimeHash:hash(await fs.readFile(path.join(root,'compatibility/runtime.mjs'))),scope:'core sintético; não valida complementos do Studio',results},null,2)+'\n');
if(results.some(r=>r.status!=='validated-core-native'))process.exitCode=1;
