import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
test('6.16 recebe menus, idioma e importação legados sem vazar controles modernos',async()=>{
 await fs.mkdir(path.join(root,'build'),{recursive:true});
 const stage=await fs.mkdtemp(path.join(root,'build','adapter616-test-'));
 try{
  execFileSync(process.execPath,[path.join(root,'ports/studio6/prepare-sources.mjs'),stage,'6.16.0'],{stdio:'pipe'});
  const read=file=>fs.readFile(path.join(stage,file),'utf8');
  const theme=await read('theme/src/com/salutem/theme/Startup.java');
  assert.match(theme,/NativePopupColors.install/);assert.doesNotMatch(theme,/ModernCompositeColors/);
  assert.match(await read('language/resources/plugin.xml'),/defaultHandler="com.salutem.l10n.SelectedLocaleHandler"/);
  const importer=await read('connection-order/src/com/salutem/order/GestaoImportDialog.java');
  assert.match(importer,/if\(!StudioApi.hasEdge\(\)\)/);assert.match(importer,/ExternalGestaoReader.read/);
  assert.ok((await fs.stat(path.join(stage,'connection-order/resources/gestao-reader/GestaoReader.exe'))).size>0);
  const reader=await read('connection-order/src/com/salutem/order/ExternalGestaoReader.java');
  assert.match(reader,/GestaoCatalog.parse/);assert.match(reader,/monitor.isCanceled/);
  assert.match(await read('language/src/com/salutem/l10n/SelectedLocaleHandler.java'),/Platform.getNL\(\)/);
 }finally{assert.ok(path.resolve(stage).startsWith(path.resolve(root,'build')+path.sep));await fs.rm(stage,{recursive:true,force:true});}
});
