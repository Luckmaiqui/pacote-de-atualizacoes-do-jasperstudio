#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';
import {root,cache,catalog,tools,json,execute,runWorker,selectEnvironment} from './runtime.mjs';
import {constants} from 'node:fs';

const [command,...args]=process.argv.slice(2);
try{
 if(command==='register-java'){
  if(!args[0])throw Error('Informe o caminho completo do executável Java');const executable=path.resolve(args[0]);await fs.access(executable);
  const probe=await execute(executable,['-XshowSettings:properties','-version'],{timeout:15000}),text=probe.stdout+'\n'+probe.stderr,match=text.match(/version "([^"]+)"/);
  if(probe.code!==0||!match)throw Error('Não foi possível confirmar esta JVM');const version=match[1],major=Number(version.startsWith('1.')?version.split('.')[1]:version.split('.')[0]);
  if(major<8||version.includes('-ea'))throw Error('Registre um runtime estável Java 8 ou superior');
  const config=await tools(),vendor=text.match(/java\.vendor = ([^\r\n]+)/)?.[1]?.trim()??'não identificado';
  config.java=config.java.filter(j=>j.major!==major);config.java.push({major,version,vendor,executable,source:'registered-local'});config.java.sort((a,b)=>a.major-b.major);
  const target=path.join(cache,'tools.json');await fs.writeFile(target+'.tmp',JSON.stringify(config,null,2));await fs.rename(target+'.tmp',target);console.log(JSON.stringify({registered:{major,version,vendor},status:'not-tested-until-matrix-runs'},null,2));
 }else if(command==='inventory'){
  const c=await catalog();let available=[];try{available=(await tools()).java.map(j=>({major:j.major,version:j.version}));}catch{}
  let evidence=[];try{evidence=(await json(path.join(root,'compatibility/results/core-matrix.json'))).results;}catch{}
  const versions=await Promise.all(c.versions.map(async v=>{let dependencies='absent';try{const lock=await json(path.join(cache,'engines',v.version,'core-native/lock.json'));for(const file of lock.files)await fs.access(file.path);dependencies='present-not-rehashed';}catch{}return {version:v.version,artifactPublished:v.available,localDependencies:dependencies,format:v.format,coreJavaMinimum:v.coreJavaMinimum,status:v.status,officialJavaCertifications:v.officialJavaCertifications,tests:evidence.filter(r=>r.version===v.version).map(r=>({java:r.javaMajor,status:r.status}))};}));
  console.log(JSON.stringify({asOf:c.asOf,latest:c.latest,java:available,scope:'Worker de relatórios; não representa compatibilidade dos complementos do Studio',versions},null,2));
 }else if(command==='inspect'){
  if(!args[0])throw Error('Informe um JRXML');const config=await tools(),java=config.java.find(j=>j.major>=8);
  console.log(JSON.stringify(await runWorker({java,version:'inspect',input:args[0],output:cache,operation:'validate'}),null,2));
 }else if(command==='validate'||command==='compile'){
  const [version,input,output,major]=args;if(!version||!input||!output)throw Error('Uso: validate|compile <motor|auto> <JRXML> <diretório novo> [Java]');
  try{await fs.access(output);throw Error('O diretório de saída já existe; escolha outro para preservar os arquivos');}catch(error){if(error.code!=='ENOENT')throw error;}
  const inspectJava=(await tools()).java[0];const inspection=await runWorker({java:inspectJava,version:'inspect',input,output:cache,operation:'validate'});if(!inspection.ok)throw Error(inspection.error??'JRXML inválido');
  if(inspection.inspection.format==='mixed')throw Error('JRXML mistura as famílias 6 e 7; revisão explícita necessária');
  const candidates=version==='auto'?(await catalog()).versions.filter(v=>v.available&&(inspection.inspection.format==='undetermined'||v.format===inspection.inspection.format)).map(v=>v.version).reverse():[version];
  let selected,selectedEngine;const failures=[];
  for(const candidate of candidates){try{selected=await selectEnvironment({version:candidate,input,preferredJava:major?Number(major):undefined,operation:command});selectedEngine=candidate;break;}catch(error){failures.push({version:candidate,reason:error.message});}}
  if(!selected)throw Error('Nenhum ambiente concluiu a operação: '+JSON.stringify(failures));
  if(command==='compile'){await fs.mkdir(path.dirname(path.resolve(output)),{recursive:true});await fs.mkdir(output);await fs.copyFile(path.join(selected.temporary,'report.jasper'),path.join(output,'report.jasper'),constants.COPYFILE_EXCL);}
  console.log(JSON.stringify({...selected.result,selectedEngine,selectedJava:selected.java.version,selectionIsNotExactSourceVersion:true},null,2));if(!selected.result.ok)process.exitCode=2;
 }else throw Error('Comandos: inventory; register-java <executável>; inspect <JRXML>; validate|compile <motor|auto> <JRXML> <diretório novo> [Java]. Preparação: Prepare-Tools.ps1 e Build-Worker.ps1.');
}catch(error){console.error(error.message);process.exitCode=1;}
