package com.salutem.order;

import com.google.gson.*;
import java.lang.reflect.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import org.eclipse.core.runtime.IProgressMonitor;

/** Uses the Studio's authenticated JAX-RS target. No password or session is copied.
 * PATCH uses the official PatchDescriptor format: version + patch(field,value).
 * It deliberately avoids addOrModifyResource's version-only 409 retry. */
final class PublicationDescriptionStore implements PublicationHistory.Store {
 private final Object target;private final ClassLoader loader;private final String api;private final IProgressMonitor monitor;
 PublicationDescriptionStore(Object rest,String uri,IProgressMonitor monitor)throws Exception{
  if(uri==null||!uri.startsWith("/")||uri.contains("?")||uri.contains("#"))throw new IllegalArgumentException("URI inválida para a Unidade de Relatório.");
  this.monitor=monitor;loader=rest.getClass().getClassLoader();Object root=rest.getClass().getMethod("getTarget").invoke(rest);
  if(root==null)throw new IllegalStateException("A sessão REST do Studio não foi inicializada.");
  String prefix=null;for(String candidate:new String[]{"javax.ws.rs","jakarta.ws.rs"})try{if(loader.loadClass(candidate+".client.WebTarget").isInstance(root)){prefix=candidate;break;}}catch(ClassNotFoundException absent){}
  if(prefix==null)throw new IllegalStateException("API REST do Studio não reconhecida.");api=prefix;
  target=type("client.WebTarget").getMethod("path",String.class).invoke(root,"resources"+uri);
 }
 private Class<?> type(String name)throws ClassNotFoundException{return loader.loadClass(api+'.'+name);}
 private Object invoke(Object object,String owner,String name,Class<?>[] types,Object...args)throws Exception{
  try{return type(owner).getMethod(name,types).invoke(object,args);}catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;throw error;}
 }
 private Object response(String method,String body)throws Exception{
  Object builder=invoke(target,"client.WebTarget","request",new Class<?>[]{String[].class},(Object)new String[]{"application/repository.reportUnit+json"});
  builder=invoke(builder,"client.Invocation$Builder","header",new Class<?>[]{String.class,Object.class},"Cache-Control","no-cache");
  Object async=invoke(builder,"client.Invocation$Builder","async",new Class<?>[0]);Future<?> pending;
  if(body==null)pending=(Future<?>)invoke(async,"client.AsyncInvoker","method",new Class<?>[]{String.class},method);
  else{Object entity=type("client.Entity").getMethod("entity",Object.class,String.class).invoke(null,body,"application/json");pending=(Future<?>)invoke(async,"client.AsyncInvoker","method",new Class<?>[]{String.class,type("client.Entity")},method,entity);}
  while(true){if(monitor.isCanceled()||Thread.currentThread().isInterrupted()){if(pending.isDone()&&!pending.isCancelled())try{Object completed=pending.get();invoke(completed,"core.Response","close",new Class<?>[0]);}catch(Exception ignored){}pending.cancel(true);throw new InterruptedException("Publicação cancelada.");}try{return pending.get(100,TimeUnit.MILLISECONDS);}catch(TimeoutException waiting){}catch(ExecutionException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;throw error;}}
 }
 public PublicationHistory.Snapshot read()throws Exception{
  Object response=response("GET",null);try{
   int status=(Integer)invoke(response,"core.Response","getStatus",new Class<?>[0]);if(status==404)return null;status(status);
   String raw=(String)invoke(response,"core.Response","readEntity",new Class<?>[]{Class.class},String.class);JsonObject json=new JsonParser().parse(raw).getAsJsonObject();
   if(!json.has("version")||json.get("version").isJsonNull()||json.get("version").getAsInt()<0)throw new IllegalStateException("O servidor não forneceu a versão da Unidade de Relatório.");
   Instant serverTime=null;String date=(String)invoke(response,"core.Response","getHeaderString",new Class<?>[]{String.class},"Date");if(date!=null)try{serverTime=ZonedDateTime.parse(date,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();}catch(java.time.format.DateTimeParseException ignored){}
   return new PublicationHistory.Snapshot(json.has("description")&&!json.get("description").isJsonNull()?json.get("description").getAsString():"",json.get("version").getAsInt(),serverTime);
  }finally{invoke(response,"core.Response","close",new Class<?>[0]);}
 }
 static String patch(int version,String description){JsonObject json=new JsonObject();json.addProperty("version",version);JsonObject item=new JsonObject();item.addProperty("field","description");item.addProperty("value",description);JsonArray items=new JsonArray();items.add(item);json.add("patch",items);return json.toString();}
 public void patchDescription(int version,String description)throws Exception{
  Object response=response("PATCH",patch(version,description));try{int status=(Integer)invoke(response,"core.Response","getStatus",new Class<?>[0]);if(status==409||status==412)throw new PublicationHistory.Conflict();status(status);}finally{invoke(response,"core.Response","close",new Class<?>[0]);}
 }
 private static void status(int status)throws Exception{if(status<200||status>=300)throw new IllegalStateException("JasperReports Server recusou os metadados (HTTP "+status+"). Verifique permissões, suporte a PATCH e limites da descrição.");}
}
