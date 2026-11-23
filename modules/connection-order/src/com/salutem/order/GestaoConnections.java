package com.salutem.order;

import java.beans.PropertyChangeListener;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import com.jaspersoft.studio.data.*;
import com.jaspersoft.studio.data.jdbc.JDBCDataAdapterDescriptor;
import com.jaspersoft.studio.data.storage.ADataAdapterStorage;
import com.jaspersoft.studio.server.ServerManager;
import com.jaspersoft.studio.server.model.server.*;
import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;
import net.sf.jasperreports.data.jdbc.*;
import net.sf.jasperreports.engine.ParameterContributorContext;
import net.sf.jasperreports.eclipse.util.SecureStorageUtils;

/** Native persistence bridge. Existing entries are inspected/tested, never overwritten by import. */
final class GestaoConnections {
 record Existing(Object object,String kind,ADataAdapterStorage storage,GestaoReconciliation.Registered metadata){}
 record Snapshot(Map<String,List<Existing>> entries){
  Map<String,List<GestaoReconciliation.Registered>> metadata(){Map<String,List<GestaoReconciliation.Registered>> result=new HashMap<>();entries.forEach((k,v)->result.put(k,v.stream().map(Existing::metadata).toList()));return result;}
 }
 private static final String FINGERPRINT_SALT=UUID.randomUUID().toString();
 static Set<ADataAdapterStorage> storages(){Set<ADataAdapterStorage> result=new LinkedHashSet<>();result.add(DataAdapterManager.getPreferencesStorage());result.addAll(DataAdapterManager.getProjectStorages());return result;}
 static Snapshot snapshot(){
  Map<String,List<Existing>> result=new HashMap<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
  for(ADataAdapterStorage storage:storages())for(DataAdapterDescriptor descriptor:storage.getDataAdapterDescriptors()){
   if(!(descriptor.getDataAdapter() instanceof JdbcDataAdapter) || !seen.add(descriptor))continue;
   JdbcDataAdapter adapter=(JdbcDataAdapter)descriptor.getDataAdapter();if(adapter.getUrl()==null)continue;
   add(result,GestaoCatalog.key("DATABASE",adapter.getUrl()),database(descriptor,storage));
  }
  for(MServerProfile server:ServerManager.getServerProfiles())try{
   if(seen.add(server))add(result,GestaoCatalog.key("SERVER",server.getValue().getUrlString()),server(server));
  }catch(IllegalArgumentException ignored){/* Malformed endpoints cannot be equated to a source URL. */}
  result.replaceAll((k,v)->List.copyOf(v));return new Snapshot(Map.copyOf(result));
 }
 private static void add(Map<String,List<Existing>> index,String key,Existing value){index.computeIfAbsent(key,k->new ArrayList<>()).add(value);}
 private static String value(String text){return Objects.toString(text,"");}
 private static String fingerprint(Object...values){
  try{MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(FINGERPRINT_SALT.getBytes(StandardCharsets.UTF_8));for(Object value:values){digest.update((byte)0);digest.update(Objects.toString(value,"").getBytes(StandardCharsets.UTF_8));}return HexFormat.of().formatHex(digest.digest());}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
 }
 private static Existing database(DataAdapterDescriptor descriptor,ADataAdapterStorage storage){
  JdbcDataAdapter adapter=(JdbcDataAdapter)descriptor.getDataAdapter();String driver=value(adapter.getDriver()),user=value(adapter.getUsername());
  GestaoReconciliation.Configuration condition=GestaoReconciliation.Configuration.COMPLETE;String detail="";
  if(driver.isBlank() || user.isBlank() || (adapter.isSavePassword() && value(adapter.getPassword()).isBlank())){condition=GestaoReconciliation.Configuration.INCOMPLETE;detail="Cadastro Incompleto — Revise Driver e Credenciais no Explorador";}
  else if(!driver.equals("org.postgresql.Driver")){condition=GestaoReconciliation.Configuration.INVALID;detail="Driver Incompatível com o PostgreSQL — Revise no Explorador";}
  String stamp=fingerprint(driver,adapter.getUrl(),user,adapter.getPassword(),adapter.isSavePassword(),adapter.getProperties()==null?Map.of():new TreeMap<>(adapter.getProperties()),adapter.getClasspath(),adapter.getAutoCommit(),adapter.getReadOnly(),adapter.getTransactionIsolation());
  return new Existing(descriptor,"DATABASE",storage,new GestaoReconciliation.Registered(descriptor,value(adapter.getName()),stamp,condition,detail));
 }
 private static Existing server(MServerProfile server){
  ServerProfile profile=server.getValue();boolean complete=profile.isUseSSO() || (!value(profile.getUser()).isBlank() && (!value(profile.getPass()).isBlank() || profile.isAskPass()));
  String stamp=fingerprint(profile.getUrlString(),profile.getUser(),profile.getPass(),profile.getOrganisation(),profile.isUseSSO(),profile.getSsoUuid(),profile.isAskPass(),profile.getUseProtocol(),profile.getTimeout(),profile.isChunked(),profile.getLocale(),profile.getTimeZone());
  return new Existing(server,"SERVER",null,new GestaoReconciliation.Registered(server,value(profile.getName()),stamp,complete?GestaoReconciliation.Configuration.COMPLETE:GestaoReconciliation.Configuration.INCOMPLETE,complete?"":"Cadastro Incompleto — Revise as Credenciais no Explorador"));
 }
 static AutoCloseable watch(Runnable changed){
  Set<ADataAdapterStorage> stores=storages();PropertyChangeListener listener=event->changed.run();
  for(ADataAdapterStorage storage:stores)storage.addPropertyChangeListener(listener);
  ServerManager.getPropertyChangeSupport().addPropertyChangeListener(listener);
  return ()->{for(ADataAdapterStorage storage:stores)storage.removePropertyChangeListener(listener);ServerManager.getPropertyChangeSupport().removePropertyChangeListener(listener);};
 }
 static String secret(String category,String password)throws Exception{
  String id=UUID.randomUUID().toString();ISecurePreferences node=SecureStorageUtils.getDefaultSecurePreferencesNode(category,true);node.put(id,password,true);node.flush();if(!node.isEncrypted(id))throw new IllegalStateException("Não foi possível proteger a senha");return id;
 }
 private static void forget(String category,String key)throws Exception{ISecurePreferences node=SecureStorageUtils.getDefaultSecurePreferencesNode(category,true);node.remove(key);node.flush();}
 static JdbcDataAdapterImpl adapter(GestaoCatalog.Entry row,AccountStore.Login login){JdbcDataAdapterImpl adapter=new JdbcDataAdapterImpl();adapter.setName(row.client());adapter.setDriver("org.postgresql.Driver");adapter.setUrl(row.endpoint());adapter.setUsername(login.username());adapter.setPassword(login.password());return adapter;}
 private static void boundTimeout(Map<String,String> properties,String key,int maximum){try{int current=Integer.parseInt(properties.getOrDefault(key,"0"));if(current==0 || current>maximum)properties.put(key,Integer.toString(maximum));}catch(NumberFormatException ignored){/* Let the driver reject an invalid configured value. */}}
 private static void testDatabase(JdbcDataAdapter adapter)throws Exception{
  Map<String,String> properties=new HashMap<>();if(adapter.getProperties()!=null)properties.putAll(adapter.getProperties());boundTimeout(properties,"connectTimeout",10);boundTimeout(properties,"socketTimeout",15);boundTimeout(properties,"loginTimeout",10);adapter.setProperties(properties);
  JdbcDataAdapterService service=new JdbcDataAdapterService(new ParameterContributorContext(JasperReportsConfiguration.getDefaultJRConfig(),null,new HashMap<>()),adapter);
  try{service.contributeParameters(new HashMap<>());if(service.getConnection()==null || service.getConnection().isClosed())throw new IllegalStateException("Conexão não estabelecida");}finally{service.dispose();}
 }
 private static void testServer(ServerProfile profile,IProgressMonitor monitor)throws Exception{
  profile.setTimeout(profile.getTimeout()>0?Math.min(profile.getTimeout(),15000):15000);MServerProfile temporary=new MServerProfile(null,profile);
  if(temporary.getWsClient(monitor)==null || temporary.getWsClient().getServerInfo(monitor)==null)throw new IllegalStateException("Conexão não estabelecida");
 }
 static void test(Existing existing,IProgressMonitor monitor)throws Exception{
  if(monitor.isCanceled())return;
  if(existing.kind().equals("DATABASE")){
   DataAdapterDescriptor copy=DataAdapterManager.cloneDataAdapter((DataAdapterDescriptor)existing.object(),JasperReportsConfiguration.getDefaultJRConfig());testDatabase((JdbcDataAdapter)copy.getDataAdapter());
  }else testServer((ServerProfile)((MServerProfile)existing.object()).getValue().clone(),monitor);
 }
 static void test(GestaoCatalog.Entry row,AccountStore.Login login,IProgressMonitor monitor)throws Exception{
  if(monitor.isCanceled())return;
  if(row.kind().equals("DATABASE"))testDatabase(adapter(row,login));
  else{
   ServerProfile profile=new ServerProfile();profile.setName(row.client());profile.setUrl(row.endpoint());profile.setUser(login.username());String key=secret("com.jaspersoft.studio.jrserver",login.password());profile.setPass(key);
   try{testServer(profile,monitor);}finally{forget("com.jaspersoft.studio.jrserver",key);}
  }
 }
 private static Existing create(GestaoCatalog.Entry row,String name,AccountStore.Login login)throws Exception{
  if(login==null || login.username().isBlank())throw new IllegalArgumentException("Selecione a Conta da Nova Conexão");
  String category=row.kind().equals("DATABASE")?"net.sf.jasperreports.data.adapter":"com.jaspersoft.studio.jrserver";String key=secret(category,login.password());
  try{
   if(row.kind().equals("DATABASE")){
    JDBCDataAdapterDescriptor descriptor=new JDBCDataAdapterDescriptor();JdbcDataAdapter adapter=(JdbcDataAdapter)descriptor.getDataAdapter();adapter.setName(name);adapter.setDriver("org.postgresql.Driver");adapter.setUrl(row.endpoint());adapter.setUsername(login.username());adapter.setPassword(key);adapter.setSavePassword(true);
    ADataAdapterStorage storage=DataAdapterManager.getPreferencesStorage();if(!storage.addDataAdapter(descriptor))throw new IllegalStateException("Cadastro recusado pelo Studio");return database(descriptor,storage);
   }
   ServerProfile profile=new ServerProfile();profile.setName(name);profile.setUrl(row.endpoint());profile.setUser(login.username());profile.setPass(key);MServerProfile server=new MServerProfile(null,profile);ServerManager.addServerProfile(server);return server(server);
  }catch(Exception error){try{forget(category,key);}catch(Exception ignored){}throw error;}
 }
 static List<GestaoReconciliation.Addition> addBatch(List<GestaoCatalog.Entry> rows,boolean hyphen,Map<String,AccountStore.Login> logins){
  return GestaoReconciliation.addAbsent(rows,hyphen,new GestaoReconciliation.Registry(){
   public Map<String,List<GestaoReconciliation.Registered>> snapshot(){return GestaoConnections.snapshot().metadata();}
   public GestaoReconciliation.Registered add(GestaoCatalog.Entry row,String name)throws Exception{return create(row,name,logins.get(row.kind())).metadata();}
  });
 }
}
