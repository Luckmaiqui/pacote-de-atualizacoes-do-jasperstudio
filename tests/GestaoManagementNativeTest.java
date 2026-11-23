package com.salutem.tests;

import java.lang.reflect.*;import java.util.*;
import java.util.List;
import com.google.gson.*;
import com.jaspersoft.studio.data.*;import com.jaspersoft.studio.data.jdbc.JDBCDataAdapterDescriptor;import com.jaspersoft.studio.data.storage.ADataAdapterStorage;
import com.jaspersoft.studio.server.ServerManager;import com.jaspersoft.studio.server.model.server.*;
import com.jaspersoft.studio.model.MRoot;import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;
import net.sf.jasperreports.data.jdbc.JdbcDataAdapter;
import org.eclipse.core.runtime.Platform;import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;import org.eclipse.swt.widgets.*;

/** Native storage CRUD in the test workspace only; never establishes a network connection. */
public final class GestaoManagementNativeTest {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static JDBCDataAdapterDescriptor adapter(String name,String host,boolean complete){JDBCDataAdapterDescriptor d=new JDBCDataAdapterDescriptor();JdbcDataAdapter a=(JdbcDataAdapter)d.getDataAdapter();a.setName(name);a.setUrl("jdbc:postgresql://"+host+":8002/salutem_hospitalar");a.setDriver(complete?"org.postgresql.Driver":"");a.setUsername(complete?"synthetic":"");a.setSavePassword(false);return d;}
 static void row(JsonArray array,String code,String label,String kind,String endpoint){JsonObject r=new JsonObject();r.addProperty("code",code);r.addProperty("client","Cliente "+code);r.addProperty("label",label);r.addProperty("kind",kind);r.addProperty("endpoint",endpoint);array.add(r);}
 static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static void pump(Display display)throws Exception{long end=System.currentTimeMillis()+150;while(System.currentTimeMillis()<end){if(!display.readAndDispatch())Thread.sleep(2);}}
 public static void run(Shell parent)throws Exception{
  ADataAdapterStorage storage=DataAdapterManager.getPreferencesStorage();var existing=adapter("Nome Manual Preservado","existing.invalid",true);var incomplete=adapter("Cadastro Incompleto","incomplete.invalid",false);var added=adapter("Adição Externa","new.invalid",true);
  MRoot repositoryRoot=new MRoot(null,null);repositoryRoot.setJasperConfiguration(JasperReportsConfiguration.getDefaultJRConfig());
  ServerProfile profile=new ServerProfile();profile.setName("Servidor Manual Preservado");profile.setUrl("https://server.invalid:443/jasperserver/");profile.setUser("synthetic");profile.setAskPass(true);MServerProfile server=new MServerProfile(new MServers(repositoryRoot),profile);
  Dialog dialog=null;boolean serverPresent=false;
  try{
   check(storage.addDataAdapter(existing),"Fixture existente não criada");check(storage.addDataAdapter(incomplete),"Fixture incompleta não criada");ServerManager.getServerProfiles();ServerManager.addServerProfile(server);serverPresent=true;
   Class<?> type=Platform.getBundle("com.salutem.jasper.connectionorder").loadClass("com.salutem.order.GestaoImportDialog");Constructor<?> constructor=type.getDeclaredConstructor(Shell.class);constructor.setAccessible(true);dialog=(Dialog)constructor.newInstance(parent);dialog.create();
   JsonArray rows=new JsonArray();row(rows,"001","Hospitalar","DATABASE","existing.invalid:8002");row(rows,"002","Hospitalar","DATABASE","incomplete.invalid:8002");row(rows,"003","Hospitalar","DATABASE","new.invalid:8002");row(rows,"004","JasperServer","SERVER","https://server.invalid/jasperserver");JsonObject export=new JsonObject();export.addProperty("format","salutem-gestao-v1");export.addProperty("source","https://gestao.sistemasalutem.com.br/management");export.add("connections",rows);
   Method load=type.getDeclaredMethod("load",String.class);load.setAccessible(true);load.invoke(dialog,export.toString());
   Table table=(Table)field(dialog,"table");Combo mode=(Combo)field(dialog,"displayMode");check(table.getItemCount()==2,"Existentes completos continuam em pendências");
   mode.select(1);mode.notifyListeners(SWT.Selection,new Event());check(table.getItemCount()==3,"Cadastros não estão acessíveis na visão própria");check(Arrays.stream(table.getItems()).anyMatch(i->i.getText(0).equals("Nome Manual Preservado")),"Nome manual substituído");
   mode.select(0);mode.notifyListeners(SWT.Selection,new Event());check(storage.addDataAdapter(added),"Adição externa recusada");pump(parent.getDisplay());check(table.getItemCount()==1,"Adição externa não atualizou a lista automaticamente");
   ((JdbcDataAdapter)existing.getDataAdapter()).setUsername("");check(storage.editDataAdapter(existing.getName(),existing),"Edição externa recusada");pump(parent.getDisplay());check(table.getItemCount()==2,"Cadastro incompleto não reapareceu após editar");
   ((JdbcDataAdapter)existing.getDataAdapter()).setUsername("synthetic");check(storage.editDataAdapter(existing.getName(),existing),"Restauração externa recusada");pump(parent.getDisplay());check(table.getItemCount()==1,"Cadastro corrigido continuou pendente");
   ServerManager.removeServerProfile(server);serverPresent=false;pump(parent.getDisplay());check(table.getItemCount()==2,"Remoção do servidor não atualizou a ausência");
   check(existing.getName().equals("Nome Manual Preservado") && ((JdbcDataAdapter)existing.getDataAdapter()).getUrl().equals("jdbc:postgresql://existing.invalid:8002/salutem_hospitalar"),"Importador alterou cadastro preservado");
   System.out.println("PASS: native storage matching, registered view, preserved aliases, automatic add/edit/remove refresh and incomplete configuration visibility; no network tests.");
  }catch(Exception|AssertionError error){error.printStackTrace();throw error;}finally{if(dialog!=null && dialog.getShell()!=null && !dialog.getShell().isDisposed())dialog.close();for(var descriptor:List.of(existing,incomplete,added))if(storage.getDataAdapterDescriptors().contains(descriptor))storage.removeDataAdapter(descriptor);if(serverPresent)ServerManager.removeServerProfile(server);}
 }
}
