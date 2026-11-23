package com.salutem.order;
import java.lang.reflect.*;import java.util.*;
import org.eclipse.core.runtime.*;import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.ui.*;import org.eclipse.swt.widgets.Display;
import com.jaspersoft.studio.model.ANode;import com.jaspersoft.studio.repository.RepositoryView;

final class RemoteSubreportOpener {
 static Object invoke(Class<?> type,Object target,String name,Object...args)throws ReflectiveOperationException{
  for(Method method:type.getMethods())if(method.getName().equals(name) && method.getParameterCount()==args.length){Class<?>[] p=method.getParameterTypes();boolean ok=true;for(int i=0;i<p.length;i++)if(args[i]!=null && !p[i].isInstance(args[i]))ok=false;if(ok)return method.invoke(target,args);}throw new NoSuchMethodException(name);
 }
 static String property(Object design,String key){try{return Objects.toString(invoke(design.getClass(),design,"getProperty",key),"");}catch(ReflectiveOperationException error){return "";}}
 static boolean open(IWorkbenchWindow window,ANode model,String expression){
  Object design=null;for(ANode node=model;node!=null;node=node.getParent()){Object value=node.getValue();if(value!=null && value.getClass().getName().equals("net.sf.jasperreports.engine.design.JasperDesign")){design=value;break;}}
  if(design==null)return false;String uri=property(design,"ireport.jasperserver.reportUnit");if(uri.isBlank() || property(design,"ireport.jasperserver.url").isBlank())return false;
  Object report=design,configuration=model.getJasperConfiguration();Display display=window.getShell().getDisplay();
  Job job=new Job("Abrir fonte do sub-relatório no JasperServer"){
   protected IStatus run(IProgressMonitor monitor){try{
    ClassLoader loader=Class.forName("com.jaspersoft.studio.server.ServerManager").getClassLoader();Class<?> manager=Class.forName("com.jaspersoft.studio.server.ServerManager",true,loader),helper=Class.forName("com.jaspersoft.studio.server.WSClientHelper",true,loader);
    Object server=invoke(manager,null,"getServerProfile",report,configuration,monitor);if(server==null)throw new IllegalStateException("Não foi encontrada a conexão JasperServer associada ao relatório.");if(!NativeServerSearch.connect(server,monitor))return Status.CANCEL_STATUS;
    Class<?> descriptorType=Class.forName("com.jaspersoft.jasperserver.api.metadata.xml.domain.impl.ResourceDescriptor",true,loader);Object descriptor=descriptorType.getConstructor().newInstance();invoke(descriptorType,descriptor,"setUriString",uri);invoke(descriptorType,descriptor,"setWsType","reportUnit");
    Object unit=invoke(helper,null,"getResource",monitor,server,descriptor);if(unit==null)throw new IllegalStateException("A unidade do relatório não foi encontrada no servidor.");
    List<RemoteSubreportMatcher.Resource> resources=new ArrayList<>();collect(unit,resources,new HashSet<>(),0);
    var selected=RemoteSubreportMatcher.choose(expression,resources);if(selected==null)throw new IllegalStateException("O fonte JRXML do sub-relatório não foi encontrado nos recursos da unidade. Verifique se o fonte foi publicado junto do relatório principal.");
    Object selectedDescriptor=selected.nativeValue();String reference=Objects.toString(invoke(descriptorType,selectedDescriptor,"getReferenceUri"),"");if(!reference.isBlank()){Object resolved=invoke(helper,null,"getReference",monitor,server,selectedDescriptor);if(resolved!=null)selectedDescriptor=resolved;}
    Object resource=invoke(helper,null,"findSelected",monitor,selectedDescriptor,server);if(resource==null)throw new IllegalStateException("Não foi possível localizar o fonte no Explorador de Repositórios.");if(monitor.isCanceled())return Status.CANCEL_STATUS;
    display.asyncExec(()->{if(window.getShell().isDisposed())return;try{
     RepositoryView view=null;for(IViewReference ref:window.getActivePage().getViewReferences()){IViewPart part=ref.getView(false);if(part instanceof RepositoryView){view=(RepositoryView)part;break;}}
     if(view==null)throw new IllegalStateException("Abra o Explorador de Repositórios e tente novamente.");TreeViewer viewer=view.getTreeViewer();Field providers=RepositoryView.class.getDeclaredField("rprovs");providers.setAccessible(true);Object provider=null;for(Object p:(Iterable<?>)providers.get(view))if(p.getClass().getName().equals("com.jaspersoft.studio.server.ServerProvider"))provider=p;
     if(provider!=null)NativeServerSearch.refreshForReveal(viewer,provider,()->ExplorerSearch.selectServer(viewer,server));NativeServerSearch.focusResult(viewer,resource);
     Class<?> actionType=Class.forName("com.jaspersoft.studio.server.action.resource.OpenInEditorAction",true,loader);Object action=actionType.getConstructor(TreeViewer.class).newInstance(viewer);invoke(actionType,action,"run");
    }catch(Exception error){show(window,error);}});
    return Status.OK_STATUS;
   }catch(Exception error){display.asyncExec(()->show(window,error));return Status.CANCEL_STATUS;}}
  };job.setUser(true);job.schedule();return true;
 }
 private static void collect(Object descriptor,List<RemoteSubreportMatcher.Resource> result,Set<Object> seen,int depth)throws ReflectiveOperationException{
  if(descriptor==null || depth>6 || !seen.add(descriptor))return;Class<?> type=descriptor.getClass();String name=Objects.toString(invoke(type,descriptor,"getName"),""),kind=Objects.toString(invoke(type,descriptor,"getWsType"),""),uri=Objects.toString(invoke(type,descriptor,"getUriString"),"");result.add(new RemoteSubreportMatcher.Resource(name,kind,uri,descriptor));
  try{Object children=invoke(type,descriptor,"getChildren");if(children instanceof Iterable<?>)for(Object child:(Iterable<?>)children)collect(child,result,seen,depth+1);}catch(NoSuchMethodException ignored){}
 }
 private static void show(IWorkbenchWindow window,Throwable error){if(!window.getShell().isDisposed())MessageDialog.openError(window.getShell(),"Abrir Sub-Relatório",Objects.toString(NativeServerSearch.cause(error).getMessage(),"Não foi possível abrir o fonte no JasperServer."));}
}
