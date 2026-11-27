import fs from 'node:fs/promises';
import path from 'node:path';
import {root,cache,catalog,tools,json,hash,runWorker} from './runtime.mjs';

const selected=process.argv.slice(2).filter(a=>!a.startsWith('--'));
const output=path.join(root,'compatibility/results/core-matrix.json');await fs.mkdir(path.dirname(output),{recursive:true});
const c=await catalog(),config=await tools(),workerHash=hash(await fs.readFile(path.join(root,'compatibility/java/ReportWorker.java'))),runtimeHash=hash(await fs.readFile(path.join(root,'compatibility/runtime.mjs')));
let previous;try{previous=await json(output);}catch{}
const results=!process.argv.includes('--fresh')&&previous?.workerHash===workerHash&&previous?.runtimeHash===runtimeHash?previous.results:[];
const runFolder=await fs.mkdtemp(path.join(cache,'matrix-'));
let saveQueue=Promise.resolve();
function save(){saveQueue=saveQueue.then(async()=>{const data={schema:1,asOf:c.asOf,workerHash,runtimeHash,scope:['read','validate','compile-java-expression','edit-roundtrip','fill-two-map-rows','export-xml','source-sha256-preserved'],notCovered:['PDF','charts','Groovy','JavaScript','JasperServer','Studio UI','all JRXML features'],results:results.slice().sort((a,b)=>a.version.localeCompare(b.version,undefined,{numeric:true})||a.javaMajor-b.javaMajor)};await fs.writeFile(output+'.tmp',JSON.stringify(data,null,2)+'\n');await fs.rename(output+'.tmp',output);});return saveQueue;}
async function check(record,java){
 const input=path.join(root,'compatibility/fixtures',record.format==='digester-6'?'legacy.xml':'modern.xml');
 const expected=hash(await fs.readFile(input)),position=results.findIndex(r=>r.version===record.version&&r.javaMajor===java.major);
 if(position>=0){const previousResult=results[position],proof=previousResult.native.ok?previousResult.native:previousResult.adapted;if(previousResult.javaVersion===java.version&&proof?.inspection?.sha256===expected)return;results.splice(position,1);}
 const testedAt=new Date().toISOString();let native,adapted;
 try{native=await runWorker({java,version:record.version,input,output:path.join(runFolder,record.version+'-'+java.major+'-native'),operation:'smoke'});}catch(error){native={ok:false,errorType:'DEPENDENCY_RESOLUTION',error:error.message};}
 if(!native.ok){
  const compiler=java.major===8?'ecj8':'ecj11';
  try{adapted=await runWorker({java,version:record.version,input,output:path.join(runFolder,record.version+'-'+java.major+'-'+compiler),operation:'smoke',compiler});}catch(error){adapted={ok:false,errorType:'DEPENDENCY_RESOLUTION',error:error.message};}
 }
 results.push({version:record.version,javaMajor:java.major,javaVersion:java.version,testedAt,status:native.ok?'validated-core-native':adapted?.ok?'validated-core-adapted':'failed',native,adapted});await save();
}
const queue=c.versions.filter(v=>v.available&&(!selected.length||selected.includes(v.version)));
async function worker(){while(queue.length){const record=queue.shift();for(const java of config.java)await check(record,java);console.log(record.version+': '+results.filter(r=>r.version===record.version).map(r=>'Java '+r.javaMajor+' '+r.status).join('; '));}}
await Promise.all([worker(),worker()]);await save();
console.log(JSON.stringify({combinations:results.length,passed:results.filter(r=>r.status.startsWith('validated')).length,failed:results.filter(r=>r.status==='failed').length}));
if(results.some(r=>r.status==='failed'))process.exitCode=1;
