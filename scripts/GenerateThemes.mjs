import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const resources=path.join(root,'modules/theme/resources');
const p=Object.fromEntries(fs.readFileSync(path.join(resources,'palettes.properties'),'utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>{let i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1).replace(/\\u([0-9a-fA-F]{4})/g,(_,h)=>String.fromCharCode(parseInt(h,16)))];}));
const base=fs.readFileSync(path.join(resources,'css/salutem-noite.css'),'utf8').replace(/Salutem Noite 1\.5\.1/,'Salutem 2.0.0');
const meanings={'252638':'background','E6E9F4':'foreground','1D2032':'toolbar','E6EAF5':'foreground','C1C8DD':'muted','30344B':'surface','10162A':'border','333852':'surface','222436':'input','485176':'accent','B7BED1':'muted','E0E4F1':'foreground','F4F5FA':'foreground','72D7CD':'link','343A55':'surface'};
let xml=['<?xml version="1.0" encoding="UTF-8"?>','<?eclipse version="3.4"?>','<plugin>',' <extension point="org.eclipse.e4.ui.css.swt.theme">'];
for(const key of p.ids.split(',')){
 const css=base.replace(/#([0-9a-fA-F]{6})\b/g,(match,hex)=>meanings[hex.toUpperCase()]?'#'+p[key+'.'+meanings[hex.toUpperCase()]]:match);
 fs.writeFileSync(path.join(resources,'css',p[key+'.css']),css);
 xml.push(`  <theme id="${p[key+'.id']}" label="${p[key+'.label']}" basestylesheeturi="css/${p[key+'.css']}"/>`,`  <themeAssociation themeId="${p[key+'.id']}" colorAndFontId="org.eclipse.ui.defaultTheme"/>`);
}
xml.push(' </extension>',' <extension point="org.eclipse.ui.startup"><startup class="com.salutem.theme.Startup"/></extension>',
 ' <extension point="org.eclipse.ui.commands"><command id="com.salutem.theme.pickBlockColor" name="%command.pickBlockColor" description="%command.pickBlockColor.description" categoryId="org.eclipse.ui.category.window"/></extension>',
 ' <extension point="org.eclipse.ui.handlers"><handler commandId="com.salutem.theme.pickBlockColor" class="com.salutem.theme.PickBlockHandler"/></extension>',
 ' <extension point="org.eclipse.ui.menus"><menuContribution locationURI="menu:window?after=additions"><command commandId="com.salutem.theme.pickBlockColor" label="%command.pickBlockColor" style="push"/></menuContribution></extension>',
 ' <extension point="org.eclipse.ui.preferencePages"><page id="com.salutem.theme.customColors" name="%page.customColors" category="org.eclipse.ui.preferencePages.Views" class="com.salutem.theme.CustomColorsPage"/></extension>',
 '</plugin>','');
fs.writeFileSync(path.join(resources,'plugin.xml'),xml.join('\n'));
console.log('Temas gerados: '+p.ids.split(',').map(id=>p[id+'.label']).join(', '));
