import fs from 'node:fs/promises';
import path from 'node:path';
import {root,cache,tools,runWorker} from './runtime.mjs';
const config=await tools(),java=config.java.find(j=>j.major===17),folder=await fs.mkdtemp(path.join(cache,'features-'));
const records=[];
for(const version of ['6.20.5','6.20.6','6.21.5']){
 const result=await runWorker({java,version,input:path.join(root,'compatibility/fixtures/style-expression.xml'),output:path.join(folder,version),operation:'smoke'});
 const expected=version!=='6.20.5';records.push({feature:'styleExpression',version,javaMajor:17,expectedSuccess:expected,passed:result.ok===expected,result});
 console.log(version+' styleExpression: '+(result.ok===expected?'PASS':'FAIL'));
}
// DPI is a format-level feature introduced in 7.1.0. Keep a separate source copy.
const source=await fs.readFile(path.join(root,'compatibility/fixtures/modern.xml'),'utf8'),dpi=path.join(folder,'dpi.xml');await fs.writeFile(dpi,source.replace('<jasperReport ','<jasperReport dpi="144" ').replaceAll('height="20"','height="60"'));
for(const version of ['7.0.8','7.1.0','7.1.1']){
 const result=await runWorker({java,version,input:dpi,output:path.join(folder,version),operation:'smoke'});const expected=version!=='7.0.8';
 records.push({feature:'dpi',version,javaMajor:17,expectedSuccess:expected,passed:result.ok===expected,result});console.log(version+' dpi: '+(result.ok===expected?'PASS':'FAIL'));
}
await fs.writeFile(path.join(root,'compatibility/results/features.json'),JSON.stringify({schema:1,records},null,2)+'\n');
if(records.some(r=>!r.passed))process.exitCode=1;
