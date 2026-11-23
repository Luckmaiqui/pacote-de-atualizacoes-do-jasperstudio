package com.salutem.order;
import java.lang.reflect.*;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.core.runtime.IProgressMonitor;

/** Uses the explicit-server API; it does not derive the server from tree selection. */
public final class NativeServerSearch {
 static void refreshForReveal(TreeViewer viewer,Object provider,Runnable clearFilter)throws ReflectiveOperationException{
  // Match the native FindResourceJob: expose the already-loaded path without
  // recursively loading every server during refresh, and always restore it.
  Method skip=provider.getClass().getMethod("setSkipLazyLoad",boolean.class);
  try{skip.invoke(provider,true);clearFilter.run();viewer.refresh(false);}finally{skip.invoke(provider,false);}
 }
 public static void revealAndClose(TreeViewer viewer,Object result,Runnable close){focusResult(viewer,result);close.run();if(!viewer.getTree().isDisposed()){viewer.getTree().getShell().forceActive();viewer.getTree().setFocus();}}
 public static void focusResult(TreeViewer viewer,Object result){
  viewer.reveal(result);
  if(result instanceof com.jaspersoft.studio.model.ANode){
   var node=(com.jaspersoft.studio.model.ANode)result;
   java.util.List<Object> ancestors=new java.util.ArrayList<>();for(var parent=node.getParent();parent!=null;parent=parent.getParent())ancestors.add(parent);
   java.util.Collections.reverse(ancestors);for(Object parent:ancestors)viewer.setExpandedState(parent,true);
   if(result.getClass().getName().equals("com.jaspersoft.studio.server.model.MFolder"))viewer.setExpandedState(result,true);
  }
  viewer.getTree().setData("salutem.searchTarget",result);
  viewer.setSelection(new org.eclipse.jface.viewers.StructuredSelection(result),true);viewer.getTree().setFocus();viewer.getTree().redraw();
 }
 static Object client(Object server)throws ReflectiveOperationException{return server.getClass().getMethod("getWsClient").invoke(server);}
 static boolean connect(Object server,IProgressMonitor monitor)throws ReflectiveOperationException{
  if(monitor.isCanceled())return false;
  Object client=client(server);
  if(client==null)client=server.getClass().getMethod("getWsClient",IProgressMonitor.class).invoke(server,monitor);
  if(monitor.isCanceled())return false;
  if(client==null)throw new IllegalStateException("Não foi possível inicializar a conexão com o JasperServer.");
  return true;
 }
 static Object open(Class<?> job,Object server)throws ReflectiveOperationException{
  for(Method method:job.getMethods()){
   Class<?>[] p=method.getParameterTypes();
   if(method.getName().equals("doFindResource") && Modifier.isStatic(method.getModifiers()) && p.length==3 && p[0].isInstance(server) && p[1]==String[].class && p[2]==String[].class)
    return method.invoke(null,new Object[]{server,null,null});
  }
  throw new NoSuchMethodException("doFindResource(server, String[], String[])");
 }
 static void reveal(Class<?> job,Object provider,Object server,Object result,TreeViewer viewer)throws ReflectiveOperationException{
  for(Method method:job.getDeclaredMethods()){
   Class<?>[] p=method.getParameterTypes();
   if(method.getName().equals("selectResource") && Modifier.isStatic(method.getModifiers()) && p.length==4 && p[0].isInstance(provider) && p[1].isInstance(server) && p[2].isInstance(result) && p[3].isInstance(viewer)){
    method.setAccessible(true);method.invoke(null,provider,server,result,viewer);return;
   }
  }
  throw new NoSuchMethodException("selectResource(provider, server, result, viewer)");
 }
 static Throwable cause(Throwable error){while((error instanceof InvocationTargetException || error instanceof ExceptionInInitializerError) && error.getCause()!=null)error=error.getCause();return error;}
}
