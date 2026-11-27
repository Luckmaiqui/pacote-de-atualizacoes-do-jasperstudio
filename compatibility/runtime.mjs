import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';

export const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
export const cache=path.join(root,'.compatibility');
export const json=async file=>JSON.parse((await fs.readFile(file,'utf8')).replace(/^\uFEFF/,''));
export const catalog=()=>json(path.join(root,'compatibility/catalog.json'));
export const tools=()=>json(path.join(cache,'tools.json'));
export const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
export function execute(executable,args,{cwd=root,timeout=120000}={}){
 return new Promise(resolve=>{
  let stdout='',stderr='',timedOut=false;const child=spawn(executable,args,{cwd,windowsHide:true,shell:false,env:{...process.env,JAVA_TOOL_OPTIONS:'',JDK_JAVA_OPTIONS:'',_JAVA_OPTIONS:''}});
  const timer=setTimeout(()=>{timedOut=true;child.kill();},timeout);
  child.stdout.on('data',bytes=>stdout+=bytes);child.stderr.on('data',bytes=>stderr+=bytes);
  child.on('error',error=>{clearTimeout(timer);resolve({code:-1,stdout,stderr:error.message,timedOut});});
  child.on('close',code=>{clearTimeout(timer);resolve({code,stdout,stderr,timedOut});});
 });
}
function escapeXml(value){return String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');}
const inFlight=new Map();
export async function resolveEngine(version,{compiler='native',full=false}={}){
 const id=version+'/'+compiler+'/'+full;if(inFlight.has(id))return inFlight.get(id);
 const promise=resolveLocked(version,{compiler,full});inFlight.set(id,promise);return promise;
}
async function resolveLocked(version,{compiler,full}){
 const record=(await catalog()).versions.find(v=>v.version===version);if(!record?.available)throw Error('Versão ausente do catálogo: '+version);
 if(!['native','ecj8','ecj11'].includes(compiler))throw Error('Perfil de compilador desconhecido');
 const config=await tools();const java=config.java.find(j=>j.major===17)??config.java.find(j=>j.major>=11);if(!java)throw Error('Preparação de dependências exige Java 11+ e Maven');
 const folder=path.join(cache,'engines',version,(full?'full':'core')+'-'+compiler);await fs.mkdir(folder,{recursive:true});
 const lockFile=path.join(folder,'lock.json');
 try{const lock=await json(lockFile);for(const file of lock.files){if(hash(await fs.readFile(file.path))!==file.sha256)throw Error('Artefato alterado: '+path.basename(file.path));}return lock;}catch(error){if(error.code!=='ENOENT')throw error;}
 const dep=d=>`<dependency><groupId>${escapeXml(d.group)}</groupId><artifactId>${escapeXml(d.artifact)}</artifactId><version>${escapeXml(d.version)}</version></dependency>`;
 const rootDep=`<dependency><groupId>net.sf.jasperreports</groupId><artifactId>jasperreports</artifactId><version>${version}</version>${full?'':'<exclusions><exclusion><groupId>*</groupId><artifactId>*</artifactId></exclusion></exclusions>'}</dependency>`;
 const excluded=new Set(['itext','openpdf','jcommon','jfreechart']);
 const dependencies=full?[]:record.dependencies.filter(d=>!d.optional&&!['test','provided'].includes(d.scope)&&!excluded.has(d.artifact));
 let compilerDependency='';
 if(compiler!=='native'){
  const ecj=compiler==='ecj8'?'3.26.0':'3.33.0';compilerDependency=dep({group:'org.eclipse.jdt',artifact:'ecj',version:ecj});
  for(let i=dependencies.length-1;i>=0;i--)if(dependencies[i].artifact==='ecj')dependencies.splice(i,1);
 }
 if(record.format==='jackson-7')compilerDependency+=`<dependency><groupId>net.sf.jasperreports</groupId><artifactId>jasperreports-jdt</artifactId><version>${version}</version>${compiler==='native'?'':'<exclusions><exclusion><groupId>org.eclipse.jdt</groupId><artifactId>ecj</artifactId></exclusion></exclusions>'}</dependency>`;
 const pom=`<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.salutem.compat</groupId><artifactId>engine-${version}</artifactId><version>1</version><dependencies>${rootDep}${dependencies.map(dep).join('')}${compilerDependency}</dependencies></project>`;
 await fs.writeFile(path.join(folder,'pom.xml'),pom);
 // No obsolete HTTP repository or implicit replacement of private vendor dependencies.
 const settings=path.join(folder,'settings.xml');await fs.writeFile(settings,'<settings><mirrors><mirror><id>central-only</id><mirrorOf>*</mirrorOf><url>https://repo.maven.apache.org/maven2</url></mirror></mirrors></settings>');
 const boot=(await fs.readdir(path.join(config.maven,'boot'))).find(f=>f.startsWith('plexus-classworlds-')&&f.endsWith('.jar'));
 const classpathFile=path.join(folder,'classpath.txt');
 const r=await execute(java.executable,[`-Dmaven.home=${config.maven}`,`-Dclassworlds.conf=${path.join(config.maven,'bin/m2.conf')}`,`-Dmaven.multiModuleProjectDirectory=${folder}`,'-cp',path.join(config.maven,'boot',boot),'org.codehaus.plexus.classworlds.launcher.Launcher','-B','-ntp','-s',settings,`-Dmaven.repo.local=${path.join(cache,'m2')}`,'-f',path.join(folder,'pom.xml'),'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',`-Dmdep.outputFile=${classpathFile}`],{cwd:folder,timeout:180000});
 await fs.writeFile(path.join(folder,'resolution.log'),r.stdout+'\n'+r.stderr);
 if(r.code!==0||r.timedOut)throw Error('Dependências indisponíveis para '+version+' / '+compiler+'; consulte resolution.log');
 const files=[];for(const file of (await fs.readFile(classpathFile,'utf8')).trim().split(path.delimiter))files.push({path:file,sha256:hash(await fs.readFile(file))});
 const engine=files.find(f=>path.basename(f.path)===`jasperreports-${version}.jar`);if(!engine||engine.sha256!==record.artifactSha256)throw Error('Motor resolvido diverge do catálogo verificado');
 const lock={schema:1,version,compiler,profile:full?'full':'core',createdAt:new Date().toISOString(),files,excludedCapabilities:full?[]:['PDF','gráficos','extensões opcionais não solicitadas']};
 await fs.writeFile(lockFile,JSON.stringify(lock,null,2));return lock;
}
export async function runWorker({java,version,input,output,operation='smoke',compiler='native'}){
 if(!['validate','compile','smoke'].includes(operation))throw Error('Operação inválida');
 const classes=path.join(cache,'worker');await fs.access(path.join(classes,'com/salutem/compat/ReportWorker.class'));
 const lock=version==='inspect'?null:await resolveEngine(version,{compiler});
 if(lock&&java.major<(await catalog()).versions.find(v=>v.version===version).coreJavaMinimum)return {ok:false,errorType:'INCOMPATIBLE_JAVA',error:'JVM inferior ao bytecode do núcleo'};
 const cp=[classes,...(lock?.files.map(f=>f.path)??[])].join(path.delimiter);
 const r=await execute(java.executable,['-Xmx384m','-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',cp,'com.salutem.compat.ReportWorker',version,path.resolve(input),path.resolve(output),operation],{timeout:45000});
 const line=r.stdout.split(/\r?\n/).find(l=>l.startsWith('SALUTEM_RESULT='));
 const result=line?JSON.parse(line.slice('SALUTEM_RESULT='.length)):{ok:false,errorType:r.timedOut?'TIMEOUT':'PROCESS_FAILED',error:r.stderr.slice(-2000)};
 return {...result,processCode:r.code,compiler,profile:'core'};
}
export async function selectEnvironment({version,input,preferredJava,operation='validate'}){
 const config=await tools(),record=(await catalog()).versions.find(v=>v.version===version);if(!record)throw Error('Motor não catalogado');
 const choices=config.java.filter(j=>j.major>=record.coreJavaMinimum&&(!preferredJava||j.major===preferredJava));
 if(!choices.length)throw Error('Nenhuma JVM instalada atende ao bytecode do motor');
 // An isolated load/validation probe is evidence for this file, not a blanket certification.
 const failures=[];
 for(const java of choices.sort((a,b)=>b.major-a.major)){
  const temporary=await fs.mkdtemp(path.join(cache,'probe-'));
  const result=await runWorker({java,version,input,output:temporary,operation});
  if(result.ok)return {java,result,temporary};
  failures.push({java:java.major,type:result.errorType,reason:result.error});
 }
 throw Error('Nenhum ambiente concluiu a operação: '+JSON.stringify(failures));
}
