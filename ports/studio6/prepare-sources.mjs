import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url)),root=path.resolve(here,'../..');
const [destination,studioVersion='6.6.0']=process.argv.slice(2);if(!destination)throw Error('Informe uma saída nova.');
await fs.mkdir(destination,{recursive:true});
for(const module of ['theme','language','connection-order'])await fs.cp(path.join(root,'modules',module),path.join(destination,module),{recursive:true,errorOnExist:true,force:false});
async function change(module,file,from,to){const target=path.join(destination,module,'src',file);let text=await fs.readFile(target,'utf8');if(!text.includes(from))throw Error('Adaptador desatualizado: '+file+' / '+from);await fs.writeFile(target,text.split(from).join(to));}
const theme='com/salutem/theme/',order='com/salutem/order/';
if(['6.6.0','6.16.0','6.21.0','6.21.5'].includes(studioVersion)){
 await change('theme',theme+'Startup.java','method.invoke(m,bar?bg:null);','method.invoke(m,(bar || (m.getStyle()&SWT.DROP_DOWN)!=0)?bg:null);');
 await change('theme',theme+'Startup.java','foreground.invoke(m,bar?fg:null);','foreground.invoke(m,(bar || (m.getStyle()&SWT.DROP_DOWN)!=0)?fg:null);');
 await change('theme',theme+'Startup.java','  for(MenuItem i:m.getItems()) {','  NativePopupColors.install(m,()->activeTheme()?new Color[]{bg,fg,muted,accent}:null);\n  for(MenuItem i:m.getItems()) {');
 await fs.copyFile(path.join(here,'NativePopupColors.java'),path.join(destination,'theme/src',theme+'NativePopupColors.java'));
}
if(studioVersion==='6.21.5'){
 await change('language','com/salutem/l10n/Startup.java','if(!t.equals(', 'if(t!=null&&!t.equals(');
 await change('language','com/salutem/l10n/Startup.java','if(!translated.equals(', 'if(translated!=null&&!translated.equals(');
 await change('theme',theme+'Startup.java','String name=c.getClass().getName();','String name=c.getClass().getName();\n  if(ModernCompositeColors.supports(c))ModernCompositeColors.apply(c,bg,fg,muted);');
 await fs.copyFile(path.join(here,'ModernCompositeColors.java'),path.join(destination,'theme/src',theme+'ModernCompositeColors.java'));
}
for(const [from,to] of [['OS.DrawText(', 'Win32Bridge.drawText('],['org.eclipse.swt.internal.win32.OS.AllowDarkModeForWindow(', 'Win32Bridge.allowDarkModeForWindow('],['org.eclipse.swt.internal.win32.OS.IsDarkModeAvailable()', 'Win32Bridge.isDarkModeAvailable()'],['org.eclipse.swt.internal.win32.OS.SetPreferredAppMode(', 'Win32Bridge.setPreferredAppMode(']]){
 const file=from.startsWith('OS.DrawText')?'NativeListSelection.java':'Startup.java';await change('theme',theme+file,from,to);
}
await change('connection-order',order+'GestaoCatalog.java','JsonParser.parseString(', 'new JsonParser().parse(');
for(const [receiver,name] of [['adapter','getAutoCommit'],['adapter','getReadOnly'],['adapter','getTransactionIsolation'],['profile','getUseProtocol']])await change('connection-order',order+'GestaoConnections.java',receiver+'.'+name+'()', 'StudioApi.optionalMetadata('+receiver+',"'+name+'")');
await change('connection-order',order+'GestaoImportDialog.java','new Browser(body,SWT.EDGE)','StudioApi.browser(body)');
await fs.copyFile(path.join(here,'Win32Bridge.java'),path.join(destination,'theme/src',theme+'Win32Bridge.java'));
for(const file of ['LegacyWindowsDark.java','LegacyLabelPaint.java','LegacyToolbarColors.java'])await fs.copyFile(path.join(here,file),path.join(destination,'theme/src',theme+file));
await change('theme',theme+'Startup.java','if(c instanceof ToolBar || c instanceof org.eclipse.swt.widgets.CoolBar) {c.setBackground(surface(c));c.setForeground(text(c));}', 'if(c instanceof ToolBar || c instanceof org.eclipse.swt.widgets.CoolBar) {c.setBackground(surface(c));c.setForeground(text(c));if(c instanceof ToolBar)LegacyToolbarColors.install((ToolBar)c,()->activeTheme()?text(c):null,()->activeTheme()?accent:null);}');
await change('theme',theme+'Startup.java','}catch(ReflectiveOperationException ignored){ }\n  if(!enabled)', '}catch(ReflectiveOperationException ignored){LegacyLabelPaint.update(label,enabled);label.setData("salutem.effectiveEnabled",enabled);}\n  if(!enabled)');
await change('theme',theme+'Startup.java','   d.timerExec(3000,this);\n  }});','   if(Win32Bridge.legacyDarkActive())Win32Bridge.legacyPreferredMode(0);d.timerExec(3000,this);\n  }});');
await change('theme',theme+'Startup.java','if(!initialized) {','if(!initialized) {d.disposeExec(()->{if(Win32Bridge.legacyDarkActive())Win32Bridge.legacyPreferredMode(0);});');
await change('theme',theme+'NativeListSelection.java','private boolean painting,released,reported;','private boolean painting,released,reported;private int callbackDepth;');
await change('theme',theme+'NativeListSelection.java','long windowProc(long hwnd,long message,long wParam,long lParam){','long windowProc(long hwnd,long message,long wParam,long lParam){callbackDepth++;try{return dispatch(hwnd,message,wParam,lParam);}finally{callbackDepth--;}}\n private long dispatch(long hwnd,long message,long wParam,long lParam){');
await change('theme',theme+'NativeListSelection.java','if(released)return;released=true;','if(released)return;released=true;if(callbackDepth==0){callback.dispose();return;}');
await fs.copyFile(path.join(here,'StudioApi.java'),path.join(destination,'connection-order/src',order+'StudioApi.java'));
console.log('Adaptadores gerados em cópia; fontes e binários atuais preservados.');

// One capability fallback and one reader binary for every Studio 6 port.
await change('connection-order',order+'GestaoImportDialog.java','private void browser(){new GestaoBrowser(getShell()).open();}', 'private void browser(){if(!StudioApi.hasEdge()){try{String json=ExternalGestaoReader.read(getShell());if(json!=null)load(json);}catch(InterruptedException cancelled){}catch(Exception error){Throwable cause=error;while(cause instanceof java.lang.reflect.InvocationTargetException && cause.getCause()!=null)cause=cause.getCause();org.eclipse.core.runtime.Platform.getLog(org.osgi.framework.FrameworkUtil.getBundle(getClass())).log(new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.IStatus.ERROR,"com.salutem.jasper.connectionorder","Falha ao abrir leitor do Gestão",cause));MessageDialog.openError(getShell(),"Ler Gestão","Não foi possível iniciar o leitor: "+cause.getClass().getSimpleName()+" — "+java.util.Objects.toString(cause.getMessage(),"Consulte o log do Eclipse."));}return;}new GestaoBrowser(getShell()).open();}');
await fs.copyFile(path.join(here,'ExternalGestaoReader.java'),path.join(destination,'connection-order/src',order+'ExternalGestaoReader.java'));
await fs.copyFile(path.join(here,'StudioLocation.java'),path.join(destination,'connection-order/src',order+'StudioLocation.java'));
await fs.cp(path.join(root,'artifacts/gestao-reader'),path.join(destination,'connection-order/resources/gestao-reader'),{recursive:true});
if(['6.6.0','6.16.0'].includes(studioVersion)){
 await change('theme',theme+'NativePopupColors.java','OS.DrawText(', 'Win32Bridge.drawText(');
 await fs.copyFile(path.join(here,'LegacyGestaoDialog.java'),path.join(destination,'connection-order/src',order+'LegacyGestaoDialog.java'));
 await fs.copyFile(path.join(here,'SelectedLocaleHandler.java'),path.join(destination,'language/src/com/salutem/l10n/SelectedLocaleHandler.java'));
 await fs.copyFile(path.join(here,'LocaleRestartArguments.java'),path.join(destination,'language/src/com/salutem/l10n/LocaleRestartArguments.java'));
 const xml=path.join(destination,'language/resources/plugin.xml');let plugin=await fs.readFile(xml,'utf8');
 plugin=plugin.replace('commandId="com.jaspersoft.studio.switchlanguage.command"','commandId="com.salutem.jasper.language.pt_BR.select"');
 plugin=plugin.replace('</plugin>','<extension point="org.eclipse.ui.commands"><command id="com.salutem.jasper.language.pt_BR.select" name="Português (Brasil)" defaultHandler="com.salutem.l10n.SelectedLocaleHandler"><commandParameter id="com.jaspersoft.studio.switchlanguage.locale" name="Locale" optional="true"/></command></extension></plugin>');
 await fs.writeFile(xml,plugin);
 const mf=path.join(destination,'language/resources/META-INF/MANIFEST.MF');let manifest=await fs.readFile(mf,'utf8');manifest=manifest.replace('Require-Bundle: org.eclipse.ui,','Require-Bundle: org.eclipse.core.runtime,org.eclipse.ui,');await fs.writeFile(mf,manifest);
}
